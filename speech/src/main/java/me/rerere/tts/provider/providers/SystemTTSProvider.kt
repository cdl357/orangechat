/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider.providers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.android.appTempFolder
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SystemTTSProvider"

class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val audioData = suspendCancellableCoroutine<ByteArray> { continuation ->
            var tts: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ttsInstance = tts ?: error("TextToSpeech instance is null")

                    // Set language
                    val locale = Locale.getDefault()
                    val langResult = ttsInstance.setLanguage(locale)

                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.e(
                            TAG,
                            "generateSpeech: Language $locale not supported, " +
                                "langResult=$langResult, engine=${ttsInstance.defaultEngine}"
                        )
                        ttsInstance.shutdown()
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("手机语音引擎不支持当前语言($locale)，请检查系统 TTS 语音数据，或到「设置 - 语音」改用云端语音")
                        )
                    } else {

                        // Set speech parameters
                        ttsInstance.setSpeechRate(providerSetting.speechRate)
                        ttsInstance.setPitch(providerSetting.pitch)

                        // Create temporary file for audio output using temp directory like RikkaHubApp
                        val tempDir = context.appTempFolder
                        val audioFile = File(tempDir, "tts_${System.currentTimeMillis()}.wav")

                        val utteranceId = UUID.randomUUID().toString()

                        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.i(TAG, "onStart: TTS engine started!")
                            }

                            override fun onDone(utteranceId: String?) {
                                try {
                                    if (audioFile.exists()) {
                                        val audioData = audioFile.readBytes()
                                        audioFile.delete()

                                        if (continuation.isActive) continuation.resume(audioData)
                                    } else {
                                        Log.e(TAG, "onDone: audio file missing: $audioFile")
                                        if (continuation.isActive) continuation.resumeWithException(
                                            Exception("语音合成完成但未生成音频文件")
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (continuation.isActive) continuation.resumeWithException(e)
                                } finally {
                                    ttsInstance.shutdown()
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                Log.e(
                                    TAG,
                                    "onError: TTS synthesis failed! utteranceId=$utteranceId, " +
                                        "engine=${ttsInstance.defaultEngine}, fileExists=${audioFile.exists()}"
                                )
                                audioFile.delete()
                                if (continuation.isActive) continuation.resumeWithException(
                                    Exception("语音合成失败（系统语音引擎报错），建议到「设置 - 语音」改用云端语音")
                                )
                                ttsInstance.shutdown()
                            }
                        })

                        val result = ttsInstance.synthesizeToFile(
                            request.text,
                            null,
                            audioFile,
                            utteranceId
                        )

                        if (result != TextToSpeech.SUCCESS) {
                            Log.e(
                                TAG,
                                "generateSpeech: synthesizeToFile failed to start, " +
                                    "result=$result, engine=${ttsInstance.defaultEngine}"
                            )
                            if (continuation.isActive) continuation.resumeWithException(
                                Exception("语音引擎无法开始合成(result=$result)，建议到「设置 - 语音」改用云端语音")
                            )
                            ttsInstance.shutdown()
                        }
                    }

                } else {
                    Log.e(
                        TAG,
                        "generateSpeech: TextToSpeech init failed, status=$status, engine=${tts?.defaultEngine}"
                    )
                    if (continuation.isActive) continuation.resumeWithException(
                        Exception("手机自带语音引擎初始化失败(status=$status)，请检查系统 TTS 设置，或到「设置 - 语音」改用云端语音")
                    )
                }
            }
        tts = TextToSpeech(context, listener)

        continuation.invokeOnCancellation {
            tts?.shutdown()
        }
    }

        emit(
            AudioChunk(
                data = audioData,
                format = me.rerere.tts.model.AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString()
                )
            )
        )
    }
}
