/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import me.rerere.asr.stripTrailingEmoji
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SiliconFlowASR"

class SiliconFlowASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.SiliconFlow
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var vadJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var audioBuffer: ByteArrayOutputStream? = null
    private var recordingStartTime: Long = 0L
    private var amplitudesBuffer = mutableListOf<Float>()

    /**
     * 是否允许本 controller 自己在静音后停止录音并提交转写。
     *
     * 语音通话在 AI 说话期间会置为 false: 麦克风要继续开着给打断检测读音量,
     * 但不能把扬声器传回来的 AI 声音当成一句话提交去转写。
     */
    @Volatile
    private var autoStopOnSilence: Boolean = true

    override fun setAutoStopOnSilence(enabled: Boolean) {
        autoStopOnSilence = enabled
    }

    /**
     * 丢掉已累积的 PCM, 只保留最后 [keepTailMs] 毫秒作为预卷。
     *
     * 抢话确认时调用: 之前的缓冲里混着从扬声器录回来的 AI 声音, 不能拿去转写;
     * 但也不能整个清空 —— 打断是在用户已经开口几百毫秒后才确认的, 清空会把
     * 用户开头那几个字吞掉。保留 1 秒左右刚好覆盖这段。
     *
     * 只截尾, 不停录音, 不提交转写。录音继续, 让用户把话说完。
     */
    override fun resetBufferKeepingTail(keepTailMs: Long) {
        val buffer = audioBuffer ?: return
        // 16-bit 单声道: 每毫秒的字节数 = sampleRate * 2 / 1000
        val bytesPerMs = provider.sampleRate * 2 / 1000
        val keepBytes = (keepTailMs * bytesPerMs).toInt().coerceAtLeast(0)

        val data = buffer.toByteArray()
        if (data.size <= keepBytes) return // 还没攒够, 不用截

        // 从偶数字节边界开始截, 否则 16-bit 采样会错位, 回放/转写变噪音
        var from = data.size - keepBytes
        if (from % 2 != 0) from += 1

        val fresh = ByteArrayOutputStream()
        fresh.write(data, from, data.size - from)
        audioBuffer = fresh
        // 录音起点也要跟着挪, 否则时长统计和 VAD 的 30 秒上限会算错
        recordingStartTime = System.currentTimeMillis() - keepTailMs
        Log.d(TAG, "预卷截断: ${data.size} -> ${data.size - from} 字节")
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        audioBuffer = ByteArrayOutputStream()
        amplitudesBuffer = mutableListOf()
        recordingStartTime = System.currentTimeMillis()
        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true,
                transcript = ""
            )
        }
        startRecorder()
        startLocalVad()
    }

    override fun stop() {
        vadJob?.cancel()
        recorderJob?.cancel()
        val buffer = audioBuffer
        audioBuffer = null
        releaseRecorder()

        if (buffer != null && buffer.size() > 0) {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            scope.launch {
                transcribeAudio(buffer.toByteArray())
            }
        } else {
            _state.update { it.copy(status = ASRStatus.Idle) }
        }
    }

    override fun dispose() {
        stop()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                provider.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(provider.sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder: AudioRecord
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    provider.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException(
                        "AudioRecord 初始化失败, state=${recorder.state}, 请检查录音权限或音频参数"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord 构造/初始化失败", e)
                setError(e.message ?: "麦克风初始化失败")
                return@launch
            }

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                        audioBuffer?.write(buffer, 0, read)
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private suspend fun transcribeAudio(pcmData: ByteArray) {
        val durationMs = System.currentTimeMillis() - recordingStartTime

        withContext(Dispatchers.IO) {
            try {
                // Convert PCM to WAV
                val wavData = pcmToWav(pcmData, provider.sampleRate)

                // Save to persistent voice file
                val voiceDir = File(context.filesDir, "voice_messages")
                voiceDir.mkdirs()
                val audioFile = File(voiceDir, "voice_${System.currentTimeMillis()}.wav")
                FileOutputStream(audioFile).use { it.write(wavData) }

                // Build multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", provider.model)
                    .addFormDataPart(
                        "file",
                        "audio.wav",
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .apply {
                        if (provider.language.isNotBlank()) {
                            addFormDataPart("language", provider.language)
                        }
                    }
                    .build()

                val request = Request.Builder()
                    .url(provider.baseUrl.trim())
                    .addHeader("Authorization", "Bearer ${provider.apiKey}")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                // body.string() 对空响应体返回的是 ""，不是 null。
                // 原来只判断 `responseBody == null` 等于没判断，"" 会一路穿到
                // JSONObject("") 那里抛 JSONException，用户看到的是
                // "End of input at character 0 of" —— 一句只有写代码的人能看懂的话，
                // 而真正的原因 (HTTP 401/402/429/502) 全被吃掉了。
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "API response: ${response.code} $responseBody")

                // 先看 HTTP 状态码。原来整个函数没有任何 isSuccessful 检查，
                // 不管对面返回 401 还是 502 都当成正常响应去解析 JSON。
                // 同项目的 MiMoASRController 两道检查都有，这里是漏写的。
                if (!response.isSuccessful) {
                    audioFile.delete()
                    setError(describeHttpFailure(response.code, responseBody))
                    return@withContext
                }

                if (responseBody.isBlank()) {
                    audioFile.delete()
                    setError("语音识别服务返回了空响应 (HTTP ${response.code})，请稍后再试")
                    return@withContext
                }

                // 解析失败不能把 JSONException 的原文直接甩给用户。
                // 带上状态码和响应体前缀，才能判断是网关返回了 HTML 错误页
                // 还是接口格式变了。
                val json = runCatching { JSONObject(responseBody) }.getOrElse { e ->
                    Log.e(TAG, "响应不是合法 JSON: code=${response.code}, body=$responseBody", e)
                    audioFile.delete()
                    setError(
                        "语音识别返回的内容无法解析 (HTTP ${response.code}): " +
                            responseBody.take(80)
                    )
                    return@withContext
                }

                // SiliconFlow response: { code, message, data }
                val code = json.optInt("code", -1)
                val message = json.optString("message", "")

                // Check for error: code present and non-zero
                if (code != -1 && code != 0) {
                    audioFile.delete()
                    setError(message.ifEmpty { "API error code: $code" })
                    return@withContext
                }

                val rawText = json.optString("data", "").trim().ifEmpty {
                    json.optString("text", "").trim()
                }
                val text = rawText.stripTrailingEmoji()

                if (text.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            transcript = text,
                            status = ASRStatus.Idle,
                            errorMessage = null,
                            audioFilePath = audioFile.absolutePath,
                            durationMs = durationMs
                        )
                    }
                    onTranscriptChange?.invoke(text)
                } else {
                    _state.update {
                        it.copy(
                            status = ASRStatus.Idle,
                            errorMessage = null,
                            audioFilePath = audioFile.absolutePath,
                            durationMs = durationMs
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                setError(e.message ?: "Transcription failed")
            }
        }
    }

    /**
     * 把 HTTP 失败翻译成一句能指出下一步动作的话。
     *
     * 原来这些情况全都表现为 "End of input at character 0 of" —— 用户完全无法
     * 判断是自己网不好、key 过期了、还是余额没了。错误信息的价值在于指向动作,
     * 所以每一类都写清楚"去哪里改什么"。
     * 服务端返回体也附在后面 (截断), 便于排查接口自身的报错。
     */
    private fun describeHttpFailure(code: Int, body: String): String {
        val hint = when (code) {
            401, 403 -> "语音识别的 API Key 无效或已过期，请到「设置 - 语音识别」检查"
            402 -> "语音识别服务余额不足，请充值后再试"
            404 -> "语音识别接口地址不对，请到「设置 - 语音识别」核对 Base URL"
            413 -> "这段录音太长，服务端拒收了，说短一点再试"
            429 -> "语音识别请求太频繁被限流了，等一会儿再说"
            in 500..599 -> "语音识别服务端故障 (HTTP $code)，稍后再试"
            else -> "语音识别失败 (HTTP $code)"
        }
        val detail = body.trim().take(80)
        return if (detail.isEmpty()) hint else "$hint：$detail"
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val wav = ByteArrayOutputStream(44 + dataSize)
        // RIFF header
        wav.write("RIFF".toByteArray())
        writeIntLE(wav, totalSize)
        wav.write("WAVE".toByteArray())
        // fmt chunk
        wav.write("fmt ".toByteArray())
        writeIntLE(wav, 16) // chunk size
        writeShortLE(wav, 1) // PCM format
        writeShortLE(wav, numChannels.toShort())
        writeIntLE(wav, sampleRate)
        writeIntLE(wav, byteRate)
        writeShortLE(wav, blockAlign.toShort())
        writeShortLE(wav, bitsPerSample.toShort())
        // data chunk
        wav.write("data".toByteArray())
        writeIntLE(wav, dataSize)
        wav.write(pcmData)

        return wav.toByteArray()
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Short) {
        out.write(value.toInt() and 0xFF)
        out.write((value.toInt() shr 8) and 0xFF)
    }

    /**
     * 本地 VAD: 检测用户说完话后自动停止录音
     * 优化: 检测到 500ms 静音就自动停止, 不需要手动点停止
     */
    private fun startLocalVad() {
        vadJob?.cancel()
        vadJob = scope.launch {
            var lastAmplitudeTime = System.currentTimeMillis()
            var speechDetected = false
            val silenceThresholdMs = 500L // 500ms 静音就停止
            val minSpeechDurationMs = 400L // 至少说 400ms 才有效
            val maxRecordingDurationMs = 30_000L // 最多录 30 秒

            while (isActive) {
                delay(50)

                // 语音通话在 AI 说话期间会关掉自动停止: 麦克风继续录 (打断检测要读音量),
                // 但不能把扬声器回声当成用户说完一句话提交去转写。
                // 证据也要清掉, 否则恢复的瞬间会拿着回声累积的计时立刻误判成"说完了"。
                if (!autoStopOnSilence) {
                    speechDetected = false
                    lastAmplitudeTime = System.currentTimeMillis()
                    continue
                }

                // 检查最大录音时长
                val recordingDuration = System.currentTimeMillis() - recordingStartTime
                if (recordingDuration > maxRecordingDurationMs) {
                    Log.d(TAG, "VAD: Max recording duration reached")
                    stop()
                    break
                }

                val amplitudes = _state.value.amplitudes
                val recentAmplitude = if (amplitudes.isNotEmpty()) {
                    amplitudes.takeLast(3).average().toFloat()
                } else 0f

                // 检测是否有语音活动
                if (recentAmplitude > 0.03f) {
                    lastAmplitudeTime = System.currentTimeMillis()
                    if (!speechDetected) {
                        speechDetected = true
                        Log.d(TAG, "VAD: Speech detected")
                    }
                }

                // 如果已经检测到语音, 且静音超过阈值, 就自动停止
                if (speechDetected) {
                    val silentFor = System.currentTimeMillis() - lastAmplitudeTime
                    val speechDuration = lastAmplitudeTime - recordingStartTime

                    if (silentFor >= silenceThresholdMs && speechDuration >= minSpeechDurationMs) {
                        Log.d(TAG, "VAD: Auto-stop after ${silentFor}ms silence")
                        stop()
                        break
                    }
                }
            }
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }
}
