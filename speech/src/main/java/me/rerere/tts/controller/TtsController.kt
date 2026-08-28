/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.controller

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import java.util.Locale
import java.util.UUID

private const val TAG = "TtsController"

/**
 * TTS 控制器（重构版）
 * - 负责文本分片、预取合成、排队播放与状态上报
 * - 对外 API 与原版兼容
 */
class TtsController(
    context: Context,
    private val ttsManager: TTSManager
) {
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 组件
    private val appContext: Context = context.applicationContext
    private val chunker = TextChunker(maxChunkLength = 160)
    private val synthesizer = TtsSynthesizer(ttsManager)
    private val audio = AudioPlayer(context)

    // Provider & 作业
    private var currentProvider: TTSProviderSetting? = null
    private var workerJob: Job? = null
    private var isPaused = false
    private var systemTtsProbe: TextToSpeech? = null
    // 探测轮次：每次 setProvider 都自增，用来作废"在途"的旧探测回调
    private var probeGeneration: Int = 0

    // 队列与缓存（基于稳定 ID）
    private val queue: java.util.concurrent.ConcurrentLinkedQueue<TtsChunk> = java.util.concurrent.ConcurrentLinkedQueue()
    private val allChunks: MutableList<TtsChunk> = mutableListOf()
    private val cache = java.util.concurrent.ConcurrentHashMap<UUID, kotlinx.coroutines.Deferred<TTSResponse>>()
    private var lastPrefetchedIndex: Int = -1

    // 行为参数
    private val chunkDelayMs = 120L
    private val prefetchCount = 4

    // 状态流（保留与旧版兼容的 StateFlow）
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChunk = MutableStateFlow(0)
    val currentChunk: StateFlow<Int> = _currentChunk.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    // 统一播放状态（融合音频播放 + 分片进度）
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        // 同步底层播放器状态到统一状态，并补充分片信息
        scope.launch {
            audio.playbackState.collectLatest { audioState ->
                _playbackState.update {
                    audioState.copy(
                        currentChunkIndex = _currentChunk.value,
                        totalChunks = _totalChunks.value,
                        status = if (!_isAvailable.value) PlaybackStatus.Idle else audioState.status
                    )
                }
            }
        }
    }

    /** 选择/取消选择 Provider */
    fun setProvider(provider: TTSProviderSetting?) {
        currentProvider = provider
        // 作废所有在途的旧探测回调（切到云端 provider 时也要作废，否则旧回调可能改写 isAvailable）
        probeGeneration++
        systemTtsProbe?.shutdown()
        systemTtsProbe = null
        when (provider) {
            null -> {
                _isAvailable.update { false }
                stop()
            }
            is TTSProviderSetting.SystemTTS -> {
                // 保守策略：探测通过前一律视为不可用，避免按钮亮着但点了没反应
                _isAvailable.update { false }
                verifySystemTts()
            }
            else -> _isAvailable.update { true }
        }
    }

    /**
     * 探测系统 TTS 引擎是否真的可用：
     * 1. OnInitListener 的 status == SUCCESS
     * 2. setLanguage() 返回值不是 LANG_MISSING_DATA / LANG_NOT_SUPPORTED
     * 两者都通过才把 isAvailable 置为 true；任一失败置为 false 并写入可读的错误信息
     */
    private fun verifySystemTts() {
        val myGeneration = ++probeGeneration
        // 用数组做单元格持有实例：OnInitListener 可能在 TextToSpeech 构造函数返回之前
        // 就被回调（部分厂商引擎如此），此时字段 systemTtsProbe 还没被赋值。
        // 局部单元格在 lambda 创建时就已存在，回调里无论早晚都能拿到同一个引用。
        val cell = arrayOfNulls<TextToSpeech>(1)
        val listener = TextToSpeech.OnInitListener { status ->
            val probe = cell[0]
            // 这轮探测已被后来的 setProvider 作废：直接释放，不碰任何状态
            if (myGeneration != probeGeneration) {
                Log.i(TAG, "verifySystemTts: stale probe(gen=$myGeneration), discard")
                probe?.shutdown()
                return@OnInitListener
            }
            if (currentProvider !is TTSProviderSetting.SystemTTS) {
                probe?.shutdown()
                return@OnInitListener
            }
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "verifySystemTts: init failed, status=$status")
                _isAvailable.update { false }
                _error.update {
                    "手机自带语音引擎初始化失败(status=$status)，请检查系统 TTS 设置，或到「设置 - 语音」改用云端语音"
                }
                return@OnInitListener
            }
            if (probe == null) {
                // 理论上不该发生（cell 在 lambda 之前创建）。保守判为不可用，并说明原因。
                Log.e(TAG, "verifySystemTts: probe instance unavailable in callback")
                _isAvailable.update { false }
                _error.update {
                    "语音引擎探测失败（引擎实例未就绪），请重试或到「设置 - 语音」改用云端语音"
                }
                return@OnInitListener
            }
            val locale = Locale.getDefault()
            val langResult = try {
                probe.setLanguage(locale)
            } catch (e: Exception) {
                Log.e(TAG, "verifySystemTts: setLanguage threw", e)
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "verifySystemTts: language $locale not supported, langResult=$langResult")
                _isAvailable.update { false }
                _error.update {
                    "手机语音引擎不支持当前语言($locale)，请检查系统 TTS 语音数据，或到「设置 - 语音」改用云端语音"
                }
            } else {
                Log.i(TAG, "verifySystemTts: ok, locale=$locale, langResult=$langResult")
                _isAvailable.update { true }
            }
        }
        val instance = TextToSpeech(appContext, listener)
        cell[0] = instance
        systemTtsProbe = instance
    }

    /**
     * 朗读文本
     * - flush=true: 清空当前进度并重新开始
     * - flush=false: 继续队列，追加朗读
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val provider = currentProvider
        if (provider == null) {
            _error.update { "未选择语音提供商，请到「设置 - 语音」中选择" }
            return
        }

        val newChunks = chunker.split(text)
        if (newChunks.isEmpty()) return

        if (flush) {
            internalReset()
            allChunks.addAll(newChunks)
            queue.addAll(newChunks)
            _currentChunk.update { 0 }
        } else {
            // 追加时，重映射 index 以保持全局顺序
            val startIndex = (allChunks.lastOrNull()?.index ?: -1) + 1
            val remapped = newChunks.mapIndexed { i, c -> c.copy(index = startIndex + i) }
            allChunks.addAll(remapped)
            queue.addAll(remapped)
        }
        _totalChunks.update { queue.size }
        _error.update { null }

        _playbackState.update {
            it.copy(
                currentChunkIndex = _currentChunk.value,
                totalChunks = _totalChunks.value,
                status = PlaybackStatus.Buffering
            )
        }

        if (workerJob?.isActive != true) startWorker()
        prefetchFrom((_currentChunk.value).coerceAtLeast(0))
    }

    private fun internalReset() {
        // Reset current session while keeping provider availability
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Reset")) }
        cache.clear()
        lastPrefetchedIndex = -1
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _error.update { null }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** 暂停播放（保留进度） */
    fun pause() {
        isPaused = true
        audio.pause()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    /** 恢复播放 */
    fun resume() {
        isPaused = false
        audio.resume()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    /** 快进当前音频 */
    fun fastForward(ms: Long = 5_000) {
        audio.seekBy(ms)
    }

    /** 设置播放速度 */
    fun setSpeed(speed: Float) {
        audio.setSpeed(speed)
    }

    /** 跳过下一段（不打断当前正在播放） */
    fun skipNext() {
        if (queue.isNotEmpty()) {
            queue.poll()
            _totalChunks.update { queue.size }
        }
    }

    /** 停止并清空状态 */
    fun stop() {
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Stopped")) }
        cache.clear()
        lastPrefetchedIndex = -1
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** 释放资源 */
    fun dispose() {
        stop()
        probeGeneration++
        systemTtsProbe?.shutdown()
        systemTtsProbe = null
        scope.cancel()
        audio.release()
    }

    // region 内部：播放调度
    private fun startWorker() {
        val provider = currentProvider
        if (provider == null) {
            _error.update { "未选择语音提供商，请到「设置 - 语音」中选择" }
            return
        }

        workerJob = scope.launch {
            _isSpeaking.update { true }
            var processedCount = _currentChunk.value
            try {
                while (isActive) {
                    if (isPaused) {
                        delay(80)
                        continue
                    }

                    val chunk = queue.poll() ?: break

                    // 更新状态（1-based）
                    _currentChunk.update { processedCount + 1 }
                    _totalChunks.update { queue.size + 1 }
                    _playbackState.update {
                        it.copy(
                            currentChunkIndex = _currentChunk.value,
                            totalChunks = _totalChunks.value
                        )
                    }

                    // 预取下一窗口
                    prefetchFrom(chunk.index + 1)

                    val response = try {
                        awaitOrCreate(chunk, provider)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Synthesis error for chunk index=${chunk.index}", e)
                        _error.update { e.message ?: "语音合成失败（未知错误）" }
                        processedCount++
                        continue
                    }

                    // 播放
                    try {
                        audio.play(response)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Playback error", e)
                        _error.update { e.message ?: "音频播放失败" }
                    }

                    if (queue.isNotEmpty()) delay(chunkDelayMs)

                    processedCount++
                }
            } finally {
                _isSpeaking.update { false }
                if (queue.isEmpty()) {
                    _playbackState.update { it.copy(status = PlaybackStatus.Ended) }
                }
            }
        }
    }

    private fun prefetchFrom(startIndex: Int) {
        val provider = currentProvider ?: return
        val begin = startIndex.coerceAtLeast(lastPrefetchedIndex + 1)
        val endExclusive = (begin + prefetchCount).coerceAtMost(allChunks.size)
        if (begin >= endExclusive) return

        for (i in begin until endExclusive) {
            val chunk = allChunks.getOrNull(i) ?: continue
            cache.computeIfAbsent(chunk.id) {
                scope.async(Dispatchers.IO) { synthesizer.synthesize(provider, chunk) }
            }
        }
        lastPrefetchedIndex = endExclusive - 1
    }

    private suspend fun awaitOrCreate(chunk: TtsChunk, provider: TTSProviderSetting): TTSResponse {
        val deferred = cache.computeIfAbsent(chunk.id) {
            scope.async(Dispatchers.IO) { synthesizer.synthesize(provider, chunk) }
        }
        return try {
            deferred.await()
        } finally {
            // 可按需保留缓存（此处保留，便于重播/重试）
        }
    }
    // endregion
}
