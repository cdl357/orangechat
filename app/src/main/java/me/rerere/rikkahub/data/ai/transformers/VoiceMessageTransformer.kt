/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.findProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "VoiceMessageTransformer"

/**
 * 把语音消息转成带韵律描述的文字给模型看。
 *
 * 音频发到耳朵服务（whisper 转写 + omni 韵律分析），拿不到就退化成纯文字转写。
 *
 * 走网关代理而不是直连耳朵服务：耳朵服务本身没有鉴权，直连就得把 8766 对公网开放，
 * 谁扫到都能白蹭 API 额度。网关有 API_SECRET，而这个 key 已经是用户配好的
 * provider apiKey，不需要在源码里硬编码任何密钥（本仓库是公开的）。
 */
object VoiceMessageTransformer : InputMessageTransformer {

    /** 网关上的耳朵代理路径，鉴权由网关的 API_SECRET 负责 */
    private const val EARS_PROXY_PATH = "/api/ears"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 没有语音消息就原样返回，不去碰 settings，省掉无谓开销
        if (messages.none { m -> m.parts.any { it is UIMessagePart.VoiceMessage } }) {
            return messages
        }

        val endpoint = resolveEarsEndpoint(ctx)

        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.VoiceMessage -> transformVoicePart(part, endpoint)
                        else -> part
                    }
                }
            )
        }
    }

    /**
     * 从当前模型所属的 provider 推出耳朵代理地址和凭据。
     *
     * 聊天模型走的是我们自己的网关（baseUrl 形如 http://host:41337/v1），
     * 去掉 /v1 后缀拼上 /api/ears 就是代理入口，apiKey 就是网关的 API_SECRET。
     * 换成别家 provider 时拿不到有效地址，语音会退化成纯文字转写（不报错）。
     */
    private fun resolveEarsEndpoint(ctx: TransformerContext): EarsEndpoint? {
        val provider = ctx.model.findProvider(ctx.settings.providers) ?: return null
        val (baseUrl, apiKey) = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl to provider.apiKey
            is ProviderSetting.Claude -> provider.baseUrl to provider.apiKey
            else -> return null
        }
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        val root = baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        if (root.isBlank()) return null
        return EarsEndpoint(url = root + EARS_PROXY_PATH, apiKey = apiKey)
    }

    private data class EarsEndpoint(val url: String, val apiKey: String)

    private suspend fun transformVoicePart(
        part: UIMessagePart.VoiceMessage,
        endpoint: EarsEndpoint?,
    ): UIMessagePart {
        val audioFile = part.url.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.length() > 0 }

        if (audioFile != null && endpoint != null) {
            try {
                val summary = withContext(Dispatchers.IO) { analyzeWithEars(audioFile, endpoint) }
                if (summary != null) {
                    val transcript = summary.optString("transcript", part.transcript)
                    return UIMessagePart.Text(text = buildRhythmText(summary, transcript))
                }
                Log.w(TAG, "耳朵服务没给出结果，降级为纯文字")
            } catch (e: Exception) {
                Log.w(TAG, "耳朵服务失败，降级: ${e.message}")
            }
        } else if (audioFile != null) {
            Log.w(TAG, "拿不到网关地址（当前 provider 不是网关？），降级为纯文字")
        }

        return if (part.transcript.isNotBlank()) {
            UIMessagePart.Text(text = part.transcript)
        } else {
            UIMessagePart.Text(text = "[语音消息]")
        }
    }

    private fun analyzeWithEars(file: File, endpoint: EarsEndpoint): JSONObject? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()
        val req = Request.Builder()
            .url(endpoint.url)
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .post(body)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return null
            if (!resp.isSuccessful) {
                Log.w(TAG, "耳朵服务 HTTP ${resp.code}: ${text.take(200)}")
                return null
            }
            val json = JSONObject(text)
            return if (json.optBoolean("ok", false)) json.optJSONObject("summary") else null
        }
    }

    /**
     * 拼成 【语音】原话 / 【韵律】描述 两行。
     *
     * 耳朵 v3 直接返回一句现成的 prosody 描述（omni 模型听出来的语气），优先用它。
     * 老版本只给 pitch/energy/tempo_proxy 这些数值，没有 prosody 时回退到自己归纳。
     */
    private fun buildRhythmText(summary: JSONObject, transcript: String): String {
        val text = transcript.ifBlank { "[语音消息]" }
        val duration = summary.optDouble("duration_sec", 0.0)

        val prosody = summary.optString("prosody", "").trim()
        val rhythm = if (prosody.isNotBlank()) {
            // 服务端偶尔会把 prompt 里的占位词（"描述"）带在开头，去掉
            val cleaned = prosody.removePrefix("描述").trim().trimStart('：', ':', '\n')
            if (duration > 0) "$cleaned，时长${duration.toInt()}秒" else cleaned
        } else {
            legacyRhythm(summary, duration)
        }

        return if (rhythm.isNotBlank()) "【语音】$text\n【韵律】$rhythm" else text
    }

    /** 兼容耳朵 v1：只有数值指标时自己归纳成人话 */
    private fun legacyRhythm(summary: JSONObject, duration: Double): String {
        val pitchMean = summary.optJSONObject("pitch")?.optDouble("mean_hz")
        val energyMean = summary.optJSONObject("energy")?.optDouble("mean")
        val tempoProxy = summary.optDouble("tempo_proxy", 0.0)
        val pauses = summary.optInt("pause_count", 0)

        val parts = mutableListOf<String>()
        if (pitchMean != null && pitchMean > 0) {
            parts += when {
                pitchMean > 250 -> "音调偏高"
                pitchMean < 150 -> "音调偏低"
                else -> "音调适中"
            }
        }
        if (energyMean != null) {
            parts += when {
                energyMean > 0.1 -> "声音较响"
                energyMean < 0.02 -> "声音轻柔"
                else -> "音量适中"
            }
        }
        if (tempoProxy > 0) {
            parts += when {
                tempoProxy > 100 -> "语速较快"
                tempoProxy < 40 -> "语速较慢"
                else -> "语速正常"
            }
        }
        if (pauses > 3) parts += "有停顿"
        if (duration > 0) parts += "时长${duration.toInt()}秒"
        return parts.joinToString("，")
    }
}
