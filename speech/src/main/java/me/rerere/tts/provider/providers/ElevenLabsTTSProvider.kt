/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "ElevenLabsTTSProvider"

/**
 * 明确"重试也没用"的失败: API key 错误、请求参数不合法、账户额度耗尽。
 *
 * 单独一个类型是为了让重试循环能干净地把它放过去。用 message 前缀去区分
 * 可重试和不可重试太脆 —— 改一个字文案就会让不可重试的错误被反复重试 4 次,
 * 白等十几秒才报错。
 */
private class NonRetryableTtsException(message: String) : IOException(message)

/** 429/5xx 时最多重试几次 (不含首次请求) */
private const val MAX_RETRIES = 4

/** 没有 Retry-After 头时的基础退避时长 */
private const val BASE_BACKOFF_MS = 700L

/** 退避上限, 免得一段话卡太久 */
private const val MAX_BACKOFF_MS = 8_000L

class ElevenLabsTTSProvider : TTSProvider<TTSProviderSetting.ElevenLabs> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.ElevenLabs,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        // clamp 到 API 要求的 0..1 区间, 防止迁移/导入的旧设置超范围触发 400
        val stability = providerSetting.stability.coerceIn(0f, 1f)
        val similarityBoost = providerSetting.similarityBoost.coerceIn(0f, 1f)

        val requestBody = JSONObject().apply {
            put("text", request.text)
            put("model_id", providerSetting.modelId)
            // 显式锁定输出格式为 mp3, 与下面 AudioFormat.MP3 一致。
            // eleven_v3 等模型默认返回 PCM, 不显式指定会导致拿到 PCM 却按 MP3
            // 解析, 播放杂音或 PlaybackException。
            put("output_format", "mp3_44100_128")
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarityBoost)
            })
        }

        Log.i(
            TAG,
            "generateSpeech request: voiceId=${providerSetting.voiceId}, " +
                "modelId=${providerSetting.modelId}, chars=${request.text.length}"
        )

        val audioData = requestWithRetry(providerSetting, requestBody.toString())

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "elevenlabs",
                    "model" to providerSetting.modelId,
                    "voice" to providerSetting.voiceId
                )
            )
        )
    }

    /**
     * 带退避重试的请求。
     *
     * ## 为什么必须重试
     * ElevenLabs 按套餐限制**并发**请求数 (低档套餐通常只有 2~3 路)。
     * TtsController 会为了压低首句延迟而预取多个分段, 一旦并发数超过套餐上限,
     * 多出来的请求立刻收到 429。而 TtsController 的 worker 对合成失败的分段是
     * "记一条错误然后 continue", 于是那几段被静默跳过 —— 表现就是"一段话只念了
     * 其中两句"。限制并发 (见 TtsController 的 synthesisGate) 是主要修复,
     * 这里的重试是第二道保险: 网络抖动或对端瞬时拥塞时不至于直接丢一段。
     *
     * ## 哪些错误重试, 哪些不重试
     * - 429 (并发/频率超限) 和 5xx (对端故障): 重试。这类错误重试是安全的,
     *   TTS 合成是幂等的, 重复请求只是多花一次配额, 不会产生副作用。
     * - 4xx 里除 429 之外的 (401 key 错、400 参数错、402 额度耗尽): **不重试**。
     *   这些重试一百次也是同样结果, 只会拖长用户等待。直接带上返回体抛出,
     *   让错误信息能指出到底是 key 问题还是额度问题。
     * - IOException (连接失败/超时): 重试。请求可能没送达。
     *
     * ## 退避时长
     * 优先采用响应头 `Retry-After` (秒)。没有就用指数退避 700ms/1.4s/2.8s/5.6s,
     * 上限 8 秒, 并叠加最多 30% 的随机抖动 —— 多个分段同时被限流时, 如果它们
     * 用完全相同的退避时长, 会一起苏醒再一起撞上限流, 抖动能把它们错开。
     */
    private suspend fun requestWithRetry(
        providerSetting: TTSProviderSetting.ElevenLabs,
        bodyJson: String
    ): ByteArray {
        var lastError: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                Log.w(TAG, "generateSpeech 重试第 $attempt 次")
            }

            val httpRequest = Request.Builder()
                .url("${providerSetting.baseUrl}/text-to-speech/${providerSetting.voiceId}")
                .addHeader("xi-api-key", providerSetting.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                httpClient.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body.bytes()
                    }

                    val errorBody = runCatching { response.body.string() }.getOrNull()
                    val retryable = response.code == 429 || response.code >= 500

                    if (!retryable) {
                        // 不可重试: 直接抛, 并把返回体带上便于区分 key 错还是额度耗尽。
                        // 用专门的异常类型而不是靠 message 前缀判断 —— 下面的
                        // catch(IOException) 需要把它原样放过, 字符串匹配太脆。
                        Log.e(
                            TAG,
                            "generateSpeech 失败且不可重试: code=${response.code}, " +
                                "body=$errorBody, voiceId=${providerSetting.voiceId}"
                        )
                        throw NonRetryableTtsException(
                            "ElevenLabs TTS 失败 (${response.code} ${response.message})" +
                                (errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                        )
                    }

                    val hint = if (response.code == 429) {
                        "并发或频率超限"
                    } else {
                        "服务端错误"
                    }
                    Log.w(
                        TAG,
                        "generateSpeech $hint: code=${response.code}, body=$errorBody, " +
                            "attempt=$attempt"
                    )
                    lastError = IOException(
                        "ElevenLabs TTS $hint (${response.code})" +
                            (errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                    )

                    if (attempt < MAX_RETRIES) {
                        delay(backoffMs(attempt, response.header("Retry-After")))
                    }
                }
            } catch (e: NonRetryableTtsException) {
                // key 错 / 参数错 / 额度耗尽: 重试一百次也是同样结果, 直接抛出
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "generateSpeech 网络异常, attempt=$attempt: ${e.message}")
                lastError = e
                if (attempt < MAX_RETRIES) {
                    delay(backoffMs(attempt, null))
                }
            }
        }

        throw lastError ?: IOException("ElevenLabs TTS 请求失败 (未知原因)")
    }

    /** 计算退避时长: 优先 Retry-After 头, 否则指数退避 + 抖动 */
    private fun backoffMs(attempt: Int, retryAfterHeader: String?): Long {
        retryAfterHeader?.trim()?.toLongOrNull()?.let { seconds ->
            if (seconds in 0..60) {
                return (seconds * 1000L).coerceAtLeast(200L)
            }
        }
        val exponential = (BASE_BACKOFF_MS * Math.pow(2.0, attempt.toDouble())).toLong()
        val capped = exponential.coerceAtMost(MAX_BACKOFF_MS)
        val jitter = Random.nextLong(0, (capped / 3).coerceAtLeast(1))
        return capped + jitter
    }
}
