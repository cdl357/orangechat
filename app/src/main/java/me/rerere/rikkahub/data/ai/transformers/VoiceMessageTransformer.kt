/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
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
 * Transforms VoiceMessage parts into rich text for AI providers.
 * 如果本地保存了音频文件，上传到耳朵服务获取转写+韵律分析；
 * 否则降级为纯文字转写。
 */
object VoiceMessageTransformer : InputMessageTransformer {

    private const val EARS_URL = "http://134.175.7.196:8766"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.VoiceMessage -> transformVoicePart(part)
                        else -> part
                    }
                }
            )
        }
    }

    private fun transformVoicePart(part: UIMessagePart.VoiceMessage): UIMessagePart {
        val audioFile = part.url.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }

        if (audioFile != null) {
            try {
                val summary = analyzeWithEars(audioFile)
                if (summary != null) {
                    val transcript = summary.optString("transcript", part.transcript)
                    return UIMessagePart.Text(text = buildRhythmText(summary, transcript))
                }
            } catch (e: Exception) {
                Log.w(TAG, "耳朵服务失败，降级: ${e.message}")
            }
        }

        return if (part.transcript.isNotBlank()) {
            UIMessagePart.Text(text = part.transcript)
        } else {
            UIMessagePart.Text(text = "[语音消息]")
        }
    }

    private fun analyzeWithEars(file: File): JSONObject? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()
        val req = Request.Builder().url("$EARS_URL/analyze").post(body).build()
        val resp = httpClient.newCall(req).execute()
        val text = resp.body?.string() ?: return null
        val json = JSONObject(text)
        return if (json.optBoolean("ok", false)) json.optJSONObject("summary") else null
    }

    private fun buildRhythmText(summary: JSONObject, transcript: String): String {
        val duration = summary.optDouble("duration_sec", 0.0)
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

        val rhythm = parts.joinToString("，")
        val text = transcript.ifBlank { "[语音消息]" }
        return if (rhythm.isNotBlank()) "【语音】$text\n【韵律】$rhythm" else text
    }
}
