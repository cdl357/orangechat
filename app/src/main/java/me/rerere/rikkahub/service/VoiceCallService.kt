/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.hooks.createCustomAsrState
import me.rerere.rikkahub.ui.hooks.createCustomTtsState
import me.rerere.rikkahub.ui.pages.voice.BargeInDetector
import me.rerere.rikkahub.ui.pages.voice.BargeInEvent
import me.rerere.rikkahub.ui.pages.voice.BargeInInput
import me.rerere.rikkahub.ui.pages.voice.VoiceCallStatus
import me.rerere.rikkahub.ui.pages.voice.VoiceCallUiState
import me.rerere.rikkahub.ui.pages.voice.VoiceTurnIdentity
import me.rerere.rikkahub.ui.pages.voice.VoiceTurnTracker
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallService"

/** duck 阶段把 TTS 压到多低。不是 0 —— 完全静音的话用户会以为 AI 断了 */
private const val DUCK_VOLUME = 0.25f

/** 说话时长超过这个值就算"较长发言", 停句阈值放宽 */
private const val LONG_SPEECH_MS = 1_800L

/** 短发言的停句静音阈值 */
private const val SHORT_ENDPOINT_MS = 900L

/** 较长发言的停句静音阈值, 给思考停顿留空间 */
private const val LONG_ENDPOINT_MS = 1_350L

/** 单句硬上限, 从真实起音时刻开始算 */
private const val MAX_UTTERANCE_MS = 60_000L

/**
 * 抢话确认后保留多少毫秒录音作为预卷。
 *
 * 打断是在用户连续说了约 520ms 之后才确认的, 加上检测循环的采样间隔,
 * 保留 1 秒能覆盖"从开口到确认打断"这整段, 用户的第一个字不会被吞掉。
 */
private const val PREROLL_MS = 1_000L

/**
 * 语音通话后台服务
 *
 * 把原来 VoiceCallVM 里的业务逻辑迁移成"独立运行、跟随 Service 生命周期"的形式.
 * 用户在 VoiceCallPage 手动开始通话后, 切到后台/退出页面, 通话依然继续跑,
 * 有持续通知栏, 点通知能回到通话页面. 只有用户主动点"挂断"才真正结束.
 *
 * 同一时刻只允许存在一路通话 (由 _activeConversationId 这个 companion object 级别的
 * StateFlow 做单例保护).
 */
class VoiceCallService : Service(), KoinComponent {
    private val chatService: ChatService by inject()
    private val httpClient: OkHttpClient by inject()
    private val settingsStore: SettingsStore by inject()

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "VoiceCallService coroutine exception", e)
        }
    )

    private lateinit var conversationId: Uuid
    private lateinit var asr: CustomAsrState
    private lateinit var tts: CustomTtsState

    private val _uiState = MutableStateFlow(VoiceCallUiState())
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    val conversation: StateFlow<Conversation>
        get() = chatService.getConversationFlow(conversationId)

    // 任务协程
    private var vadJob: Job? = null
    private var speakingMonitorJob: Job? = null
    private var conversationMonitorJob: Job? = null
    private var asrMonitorJob: Job? = null
    private var interruptDetectJob: Job? = null
    private var playbackTrackerJob: Job? = null
    private var lastSpokenText: String = ""

    // 跟踪 AI 消息的增量, 用于流式 TTS
    private var lastAssistantText: String = ""
    private var hasSentCurrentMessage = false

    // 流式 TTS: 记录已发送给 TTS 的文本长度
    private var ttsSentLength: Int = 0

    // 静音状态 (独立于 _uiState.isMuted, 检测循环里直接读这个字段更快)
    private var isMuted: Boolean = false

    /**
     * 身份协议。语音通话里有 6 条异步链路并行 (ASR / 模型流 / TTS 合成 / 音频播放 /
     * VAD / 打断检测), 它们的回调都会迟到。只靠一个 status 枚举的话, 迟到的旧结果
     * 会改写新一轮的状态: 上一句的 TTS 插进这一句、上一轮的"生成完成"把新一轮
     * 踢回聆听。所有异步结果改状态之前先验一次身份, 不匹配整个丢弃。
     */
    private val turnTracker = VoiceTurnTracker()

    /**
     * 两阶段抢话检测器。
     *
     * 旧实现是"单帧音量 > 0.15f 立刻打断", 而麦克风全程开着、AI 的声音会从扬声器
     * 被录回来, 于是 AI 每次刚开口就被自己的回声打断, 界面立刻弹回"正在聆听"
     * —— 这就是通话完全用不了的直接原因。
     */
    private var bargeInDetector = BargeInDetector()

    // TTS 播放起止时刻, 供打断检测做启动冷却和回声尾窗判断
    private var playbackStartedAt: Long = 0L
    private var playbackEndedAt: Long = 0L
    private var lastPlaybackActive: Boolean = false

    /**
     * 发出这一轮消息时的身份快照。
     *
     * "生成完成"事件从 chatService 回来时不带身份信息, 只能靠这个快照判断它属于
     * 哪一轮。必须在 sendMessage 的时候记下来, 不能在收到事件时读 turnTracker.active
     * —— 那样校验恒成立, 等于没校验, 迟到的旧完成事件照样能把新一轮踢回聆听。
     */
    @Volatile
    private var pendingGenerationIdentity: VoiceTurnIdentity? = null

    companion object {
        private val _activeConversationId = MutableStateFlow<String?>(null)
        val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

        fun isRunning(): Boolean = _activeConversationId.value != null

        /**
         * 启动服务: 调用方 (VoiceCallPage) 负责在自己判断"没有冲突"之后才调这个方法.
         */
        fun start(context: Context, conversationId: String) {
            val intent = Intent(context, VoiceCallService::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "启动 VoiceCallService 失败, conversationId=$conversationId", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VoiceCallService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "停止 VoiceCallService 失败", e)
            }
        }

        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val ACTION_HANG_UP = "me.rerere.rikkahub.VOICE_CALL_HANG_UP"
        const val NOTIFICATION_ID = 40001
    }

    // Binder, 供 VoiceCallPage bindService 用
    inner class LocalBinder : Binder() {
        fun getService(): VoiceCallService = this@VoiceCallService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 用户点了通知栏上的"挂断"按钮
        if (intent?.action == ACTION_HANG_UP) {
            endCall()
            stopSelf()
            return START_NOT_STICKY
        }

        val convIdStr = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        if (convIdStr == null) {
            Log.e(TAG, "onStartCommand 缺少 conversationId 参数, 无法启动通话")
            stopSelf()
            return START_NOT_STICKY
        }

        // 已经在跑同一个对话的通话: 不要重复 startCall, 只刷新前台通知
        if (_activeConversationId.value == convIdStr) {
            return START_NOT_STICKY
        }

        // 兜底: 已经在跑别的对话的通话, 防御性丢弃
        if (_activeConversationId.value != null && _activeConversationId.value != convIdStr) {
            Log.w(
                TAG,
                "已有通话 ${_activeConversationId.value} 在进行, 忽略新的 start 请求 $convIdStr"
            )
            return START_NOT_STICKY
        }

        try {
            conversationId = Uuid.parse(convIdStr)
        } catch (e: Exception) {
            Log.e(TAG, "conversationId 解析失败: $convIdStr", e)
            stopSelf()
            return START_NOT_STICKY
        }

        _activeConversationId.value = convIdStr

        // 关键修复: 必须先同步调用 startForeground, 用一个初始状态的通知占位.
        // Android 要求 startForegroundService() 调用后 5 秒内必须调用 startForeground(),
        // 否则触发 ForegroundServiceDidNotStartInTimeException 崩溃.
        // 不能等 ASR/TTS 异步初始化完成后才调用, 真正的初始化放到下面的协程里做.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(_uiState.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败, conversationId=$conversationId", e)
            _activeConversationId.value = null
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                // 关键修复: 两个工厂函数现在是 suspend 函数, 会真正挂起等待
                // provider 设置完成后才返回实例, 消除了之前 controller 为 null 的竞态.
                asr = createCustomAsrState(applicationContext, httpClient, settingsStore)
                tts = createCustomTtsState(applicationContext, settingsStore)

                startCall()

                // 订阅 uiState 变化, 实时刷新通知内容
                launch {
                    uiState.collect { state ->
                        try {
                            val manager =
                                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, buildNotification(state))
                        } catch (e: Exception) {
                            Log.e(TAG, "刷新通话通知失败", e)
                        }
                    }
                }

                // Service 自己订阅 asr.state, 同步振幅数据 + 捕获底层 ASR 错误
                launch {
                    asr.state.collect { asrState ->
                        updateAmplitudes(asrState.amplitudes)
                        if (asrState.status == me.rerere.asr.ASRStatus.Error) {
                            val msg = asrState.errorMessage ?: "语音识别发生未知错误"
                            Log.e(TAG, "ASR 底层报错, conversationId=$conversationId, msg=$msg")
                            _uiState.update {
                                it.copy(
                                    status = VoiceCallStatus.Error,
                                    errorMessage = "语音识别错误: $msg"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化语音通话失败, conversationId=$conversationId", e)
                _uiState.update {
                    it.copy(
                        status = VoiceCallStatus.Error,
                        errorMessage = "初始化失败: ${e.message}"
                    )
                }
            }
        }

        // 不用 START_STICKY: 通话被系统杀死不应该自动重启接着录音
        return START_NOT_STICKY
    }

    /**
     * 开始语音通话
     *
     * ASR 在整个通话期间持续录音 (不再像原来那样只在 Listening 状态开启).
     * 这里只调用一次 asr.start(), 作为整场通话唯一的录音启动点
     * (除非用户中途静音又取消).
     */
    fun startCall() {
        if (_uiState.value.status != VoiceCallStatus.Idle) return
        lastAssistantText = ""
        lastSpokenText = ""
        hasSentCurrentMessage = false
        ttsSentLength = 0
        isMuted = false

        // 开一通新通话: 之前所有迟到的结果从这一刻起全部失效
        turnTracker.newCall()
        bargeInDetector = BargeInDetector()
        playbackStartedAt = 0L
        playbackEndedAt = 0L
        lastPlaybackActive = false
        startPlaybackTracker()

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Listening,
                userTranscript = "",
                errorMessage = null,
                isMuted = false
            )
        }

        try {
            asr.start { transcript ->
                _uiState.update { it.copy(userTranscript = transcript) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 ASR 失败, conversationId=$conversationId", e)
            _uiState.update {
                it.copy(
                    status = VoiceCallStatus.Error,
                    errorMessage = "麦克风启动失败: ${e.message}"
                )
            }
            return
        }

        startVadDetection()
        startAsrMonitor()
        startConversationMonitor()
    }

    /**
     * 从别的状态切回 Listening 时复位状态 + VAD 计时器.
     * 不再调用 asr.stop()/asr.start() (ASR 现在贯穿全程).
     */
    private fun startListening() {
        tts.stop()
        // duck 过就必须恢复音量, 否则下一轮 AI 的声音一直是压低的
        tts.setVolume(1f)
        ttsSentLength = 0
        lastAssistantText = ""
        hasSentCurrentMessage = false

        // 回到聆听 = 开新一轮。旧 turn 的模型流、TTS 片段、播放回调从此全部失效,
        // 不会再把这一轮踢回上一轮的状态。
        turnTracker.newTurn()
        bargeInDetector.reset()

        // AI 说话期间麦克风一直开着, 缓冲里攒了一整段从扬声器录回来的 AI 声音。
        // 不清掉的话, 用户接下来说一句话时, 提交去转写的音频里会掺着 AI 刚说的话。
        // 保留一小段尾巴而不是清空: 用户可能已经开口了, 清空会吞掉开头。
        asr.resetBufferKeepingTail(PREROLL_MS)

        // AI 不说话了, 把 ASR 的自动停止交回去 (它要负责判断用户说完没)
        asr.setAutoStopOnSilence(true)

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Listening,
                userTranscript = "",
                errorMessage = null
            )
        }

        // 停止"打断检测"协程 (Speaking 状态才需要它)
        interruptDetectJob?.cancel()

        // 重启 ASR: 非流式 ASR (SiliconFlow) 是“录一段→停”的一次性模式,
        // AI 说完话回到 Listening 时它已停, 不重启则音波球不动、说话发不出去.
        // 流式 ASR 的 start() 有 isRecording 守卫, 重复调用无副作用.
        if (!isMuted) {
            runCatching {
                asr.start { transcript ->
                    _uiState.update { it.copy(userTranscript = transcript) }
                }
            }.onFailure { Log.e(TAG, it.toString(), it) }
        }

        startVadDetection()
    }

    /**
     * VAD: 检测用户停顿后自动发送 (仅 Listening 状态生效).
     *
     * ## 自适应停句
     * 原来是固定 800ms 静音就发送。固定阈值有个绕不开的矛盾:
     * - 阈值短, 说长句中间正常思考停顿会被抢断, 半句话就被发出去;
     * - 阈值长, 说"嗯" "好" 这种短句要傻等很久。
     *
     * 现在按已说话时长分档: 短发言 (< [LONG_SPEECH_MS]) 用 900ms 收口, 反应快;
     * 说得比较长了就放宽到 1350ms, 给思考停顿留空间。
     *
     * ## 一句话硬上限
     * [MAX_UTTERANCE_MS] 从真实起音时刻开始算, 不是从进入 Listening 开始算 ——
     * 后者会把"用户还没开口的沉默"也算进去, 导致人刚说到一半就被强行截断。
     */
    private fun startVadDetection() {
        vadJob?.cancel()
        val identity = turnTracker.active
        vadJob = serviceScope.launch {
            var lastTranscript = ""
            var silenceStartTime: Long = 0L
            var lastAmplitudeTime: Long = System.currentTimeMillis()
            var speechStartTime: Long = 0L
            val minTranscriptLength = 2
            val amplitudeTimeoutMs = 2000L

            while (true) {
                delay(100)
                // 身份校验: 这一轮已被取代就退出, 不能拿旧轮的计时去发新一轮的消息
                if (!turnTracker.isActive(identity)) break
                if (_uiState.value.status != VoiceCallStatus.Listening) break
                if (isMuted) continue // 静音期间不检测, 也不发送
                if (!_uiState.value.autoSendEnabled) continue

                val currentTranscript = _uiState.value.userTranscript
                val amplitudes = _uiState.value.amplitudes
                val recentAmplitude = amplitudes.takeLast(3).average().toFloat()

                // 检测音量活动 - 如果有声音就重置计时
                if (recentAmplitude > 0.05f) {
                    lastAmplitudeTime = System.currentTimeMillis()
                    // 记住真实起音时刻, 硬上限从这里开始算
                    if (speechStartTime == 0L) {
                        speechStartTime = System.currentTimeMillis()
                    }
                }

                // 一句话硬上限: 防止环境噪声让一轮无限拖下去
                if (speechStartTime > 0L &&
                    System.currentTimeMillis() - speechStartTime >= MAX_UTTERANCE_MS &&
                    currentTranscript.length >= minTranscriptLength
                ) {
                    Log.d(TAG, "VAD 到达单句硬上限, 强制发送: $currentTranscript")
                    sendCurrentMessage()
                    break
                }

                if (currentTranscript != lastTranscript) {
                    // 转写还在变化, 重置静音计时
                    lastTranscript = currentTranscript
                    silenceStartTime = 0L
                } else if (currentTranscript.length >= minTranscriptLength) {
                    // 转写稳定且有内容, 开始/继续计时
                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    }
                    val silentFor = System.currentTimeMillis() - silenceStartTime
                    val amplitudeSilentFor = System.currentTimeMillis() - lastAmplitudeTime

                    // 自适应阈值: 说得越久, 越容忍中间的思考停顿
                    val spokenMs = if (speechStartTime > 0L) {
                        lastAmplitudeTime - speechStartTime
                    } else {
                        0L
                    }
                    val silenceThresholdMs = if (spokenMs < LONG_SPEECH_MS) {
                        SHORT_ENDPOINT_MS
                    } else {
                        LONG_ENDPOINT_MS
                    }

                    // 触发条件: 转写稳定且静音足够, 或音量持续低迷
                    if (silentFor >= silenceThresholdMs || amplitudeSilentFor >= amplitudeTimeoutMs) {
                        Log.d(
                            TAG,
                            "VAD triggered auto-send: $currentTranscript " +
                                "(silentFor=$silentFor, threshold=$silenceThresholdMs, " +
                                "spokenMs=$spokenMs, ampSilent=$amplitudeSilentFor)"
                        )
                        sendCurrentMessage()
                        break
                    }
                }
            }
        }
    }

    /**
     * 发送当前转写的消息.
     * 不再调用 asr.stop() (ASR 要持续跑到整场通话结束).
     */
    private fun sendCurrentMessage() {
        val transcript = _uiState.value.userTranscript.trim()
        vadJob?.cancel()

        if (transcript.isBlank()) {
            // 没有有效内容, 回到监听
            startListening()
            return
        }

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Processing,
                assistantText = ""
            )
        }
        ttsSentLength = 0
        lastAssistantText = ""

        // 记下这一轮的身份。"生成完成"事件回来时不带身份, 只能靠这个快照判断
        // 它属于哪一轮; 用户抢话后旧轮的完成事件会被 onGenerationDone 丢弃。
        pendingGenerationIdentity = turnTracker.active

        try {
            chatService.sendMessage(
                conversationId,
                listOf(UIMessagePart.Text(transcript))
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "发送消息失败, conversationId=$conversationId, transcript=$transcript",
                e
            )
            _uiState.update {
                it.copy(
                    status = VoiceCallStatus.Error,
                    errorMessage = "发送失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 监听对话流变化, 实现:
     * 1. 流式 TTS (检测到新句子即朗读)
     * 2. AI 开始输出时立即进入 Speaking 状态, 让用户可以打断
     * 3. AI 回复完成后回到 Listening
     */
    private fun startConversationMonitor() {
        conversationMonitorJob?.cancel()
        conversationMonitorJob = serviceScope.launch {
            conversation.collect { conv ->
                if (_uiState.value.status != VoiceCallStatus.Processing &&
                    _uiState.value.status != VoiceCallStatus.Speaking
                ) return@collect

                val lastMessage = conv.currentMessages.lastOrNull()
                if (lastMessage?.role != MessageRole.ASSISTANT) return@collect

                val currentText = lastMessage.toText()

                // 更新 UI 显示的 AI 回复
                _uiState.update { it.copy(assistantText = currentText) }

                // 流式 TTS: 只朗读新增的部分
                if (currentText.length > ttsSentLength) {
                    val newText = currentText.substring(ttsSentLength)
                    // 按句子分割, 朗读完整句子
                    val sentences = extractCompleteSentences(newText)
                    for (sentence in sentences) {
                        if (sentence.isNotBlank()) {
                            tts.enqueueText(sentence)
                            Log.d(TAG, "Streaming TTS: $sentence")
                        }
                    }
                    ttsSentLength = currentText.length - getPendingRemainder(newText).length
                }

                // 一旦 AI 有内容输出, 立即切换到 Speaking 状态
                // 这样用户随时可以打断, UI 反馈更即时
                if (_uiState.value.status == VoiceCallStatus.Processing && currentText.isNotBlank()) {
                    _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }
                    startInterruptDetection()
                }

                lastAssistantText = currentText
            }
        }

        startGenerationDoneMonitor()
    }

    /**
     * 监听生成完成 -> 等待 TTS 播放完成 -> 回到 Listening。
     *
     * 拆成独立方法是因为 [interruptSpeaking] 会 cancel 掉它, 打断后必须重建;
     * 原来它内联在 startConversationMonitor 里, 打断一次之后这条监听就永久没了,
     * 下一轮 AI 说完再也回不到聆听。
     */
    private fun startGenerationDoneMonitor() {
        speakingMonitorJob?.cancel()
        speakingMonitorJob = serviceScope.launch {
            chatService.generationDoneFlow.collect { convId ->
                if (convId != conversationId) return@collect
                // 注意: 这里必须传"发消息那一刻记下的身份", 不能传 turnTracker.active。
                // 传 active 的话它永远等于当前身份, 校验恒成立, 等于没有校验。
                onGenerationDone(pendingGenerationIdentity)
            }
        }
    }

    private suspend fun onGenerationDone(identity: VoiceTurnIdentity?) {
        // 身份校验: 这个"生成完成"事件属于哪一轮?
        // 用户抢话后旧轮次的完成事件会迟到, 照旧处理的话它会把新一轮
        // 强行推进 Speaking 再等一遍 TTS, 新一轮就此卡死。
        if (!turnTracker.isActive(identity)) {
            Log.d(TAG, "丢弃迟到的生成完成事件: $identity, 当前=${turnTracker.active}")
            return
        }

        // 朗读最后剩余的文本
        val finalText = _uiState.value.assistantText
        if (finalText.length > ttsSentLength) {
            val remaining = finalText.substring(ttsSentLength)
            if (remaining.isNotBlank()) {
                tts.enqueueText(remaining)
                ttsSentLength = finalText.length
            }
        }

        _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }
        startInterruptDetection()
        waitForTtsToFinish()

        // 等 TTS 的过程里用户可能已经抢话进了新一轮, 再验一次身份
        if (!turnTracker.isActive(identity)) {
            Log.d(TAG, "TTS 播完时该轮已失效, 不改状态: $identity")
            return
        }

        // 回到监听
        if (_uiState.value.status == VoiceCallStatus.Speaking) {
            startListening()
        }
    }

    private suspend fun waitForTtsToFinish() {
        // 等待 TTS 开始播放
        var waitStart = System.currentTimeMillis()
        while (!tts.isSpeaking.value && System.currentTimeMillis() - waitStart < 5000) {
            delay(100)
        }
        // 等待 TTS 播放完成.
        // 不能只靠 isSpeaking: 它在 worker 的 finally 里才会变 false,
        // 一旦 worker 挂在网络请用/音频播放上 (isSpeaking 永远 true),
        // 这里就死循环, 通话永远卡在 "正在传达".
        // 改用 "活动超时": 跟踪 TTS 最后一次处于活动状态的时间,
        // 连续 5 秒没有新的播放活动(不是 Playing/Buffering 且 isSpeaking 为 false)
        // 就认为说完了. 另勠 5 分钟硬截止兜底.
        val idleTimeoutMs = 5_000L
        val hardDeadlineMs = 300_000L
        val startTime = System.currentTimeMillis()
        var lastActiveTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val status = tts.playbackState.value.status
            val active = tts.isSpeaking.value ||
                status == me.rerere.tts.model.PlaybackStatus.Playing ||
                status == me.rerere.tts.model.PlaybackStatus.Buffering
            if (active) {
                lastActiveTime = now
            }
            // 连续 idleTimeoutMs 没活动 → 说完了
            if (!active && now - lastActiveTime >= idleTimeoutMs) {
                break
            }
            // 硬截止兜底 (TTS 真卡死)
            if (now - startTime > hardDeadlineMs) {
                Log.w(TAG, "TTS 播放超过 5 分钟未结束, 强制停止以防卡死")
                tts.stop()
                break
            }
            delay(300)
        }
        // 额外等待状态更新
        delay(300)
    }

    /**
     * 从增量文本中提取完整的句子 (以句号/问号/感叹号/换行结尾)
     */
    private fun extractCompleteSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char == '。' || char == '？' || char == '！' || char == '.' ||
                char == '?' || char == '!' || char == '\n'
            ) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) {
                    result.add(sentence)
                }
                current.clear()
            }
        }
        // 保存未完成的部分 (不朗读, 等下次)
        return result
    }

    /**
     * 获取增量文本中未形成完整句子的剩余部分
     */
    private fun getPendingRemainder(text: String): String {
        val lastSentenceEnd =
            text.lastIndexOfAny(charArrayOf('。', '？', '！', '.', '?', '!', '\n'))
        return if (lastSentenceEnd >= 0 && lastSentenceEnd < text.length - 1) {
            text.substring(lastSentenceEnd + 1)
        } else if (lastSentenceEnd < 0) {
            text
        } else {
            ""
        }
    }

    /**
     * Speaking 状态下的打断检测.
     *
     * 与 startVadDetection (判断"该发送了") 职责不同:
     * 这里只关心"用户是否开始说话了", 一旦检测到就立即打断, 不等静音判断.
     *
     * 用比 Listening 状态更高的音量阈值 (0.15f vs 0.05f), 降低被 AI 自己声音误触发的概率.
     * 残留风险: AEC 不是 100% 完美, 外放音量很大或低端机型硬件 AEC 差时仍可能误触发,
     * 后续可加音量差阈值调优, 但不阻塞现在的实现.
     */
    private fun startInterruptDetection() {
        interruptDetectJob?.cancel()
        val identity = turnTracker.active
        bargeInDetector.reset()

        // AI 说话期间不许 ASR 自己"检测到静音就停下来转写":
        // 那样它会把扬声器传回来的 AI 声音当成一句话提交去转写, 白花钱且无意义。
        // 麦克风仍然开着, 因为下面的检测循环需要读音量。
        asr.setAutoStopOnSilence(false)

        interruptDetectJob = serviceScope.launch {
            val frameMs = 60L
            var lastTranscriptLength = _uiState.value.userTranscript.length

            while (true) {
                delay(frameMs)

                // 身份校验: 这一轮已经被取代 (用户抢过话/挂断) 就直接退出,
                // 绝不能拿旧 turn 的判断去动新一轮的播放
                if (!turnTracker.isActive(identity)) break
                if (_uiState.value.status != VoiceCallStatus.Speaking) break
                if (isMuted) continue

                val amplitudes = _uiState.value.amplitudes
                // 取最近一帧而不是三帧平均: 平均会把短促的起音抹平, 让真实抢话
                // 迟迟攒不够连续时长。连续性由 BargeInDetector 自己累积保证。
                val amplitude = amplitudes.lastOrNull() ?: 0f

                val currentLength = _uiState.value.userTranscript.length
                val transcriptGrew = currentLength > lastTranscriptLength
                lastTranscriptLength = currentLength

                val event = bargeInDetector.push(
                    BargeInInput(
                        amplitude = amplitude,
                        frameMs = frameMs,
                        nowMs = System.currentTimeMillis(),
                        playbackActive = lastPlaybackActive,
                        playbackStartedAtMs = playbackStartedAt,
                        playbackEndedAtMs = playbackEndedAt,
                        transcriptGrew = transcriptGrew,
                    )
                )

                when (event) {
                    BargeInEvent.Duck -> {
                        // 疑似用户开口: 先压低音量, 生成继续。这一步可逆,
                        // 顺带也降低了回声强度, 减少误判连锁。
                        Log.d(TAG, "barge-in duck: amp=$amplitude, $identity")
                        tts.setVolume(DUCK_VOLUME)
                    }

                    BargeInEvent.Restore -> {
                        // 短促误触发 (咳嗽/键盘/桌面碰撞) 消失了, 恢复原音量,
                        // 不打断这一轮回答
                        Log.d(TAG, "barge-in restore: $identity")
                        tts.setVolume(1f)
                    }

                    BargeInEvent.Interrupt -> {
                        Log.d(TAG, "barge-in 确认打断: amp=$amplitude, $identity")
                        // 预卷截断在 startListening() 里统一做 (正常结束和抢话
                        // 两条路径都要), 这里不重复调。
                        interruptSpeaking()
                        break
                    }

                    null -> Unit
                }
            }
        }
    }

    /**
     * 跟踪 TTS 播放的起止时刻。
     *
     * 打断检测需要知道三件事才能把回声和真人分开:
     * - 现在是否正在出声 (决定用高门槛还是低门槛)
     * - 最近一次开始播放是什么时候 (启动冷却, 避开扬声器爆音)
     * - 最近一次结束播放是什么时候 (回声尾窗, AudioTrack 停止和声音消失有延迟)
     *
     * 单独一个协程盯着 playbackState, 而不是在检测循环里读 —— 检测循环的
     * 采样间隔是 60ms, 播放状态跳变可能落在两次采样之间, 起止时刻会漂。
     */
    private fun startPlaybackTracker() {
        playbackTrackerJob?.cancel()
        playbackTrackerJob = serviceScope.launch {
            tts.playbackState.collect { state ->
                val active = state.status == me.rerere.tts.model.PlaybackStatus.Playing ||
                    state.status == me.rerere.tts.model.PlaybackStatus.Buffering
                if (active && !lastPlaybackActive) {
                    playbackStartedAt = System.currentTimeMillis()
                } else if (!active && lastPlaybackActive) {
                    playbackEndedAt = System.currentTimeMillis()
                }
                lastPlaybackActive = active
            }
        }
    }

    /**
     * 用户打断 AI 说话 (Barge-in).
     * 不再调用 asr.start() (ASR 一直是开着的), 只做状态切换 + cancel 协程.
     */
    fun interruptSpeaking() {
        if (_uiState.value.status != VoiceCallStatus.Speaking) return
        speakingMonitorJob?.cancel()
        interruptDetectJob?.cancel()
        // startListening() 里会 newTurn() 让旧 generation 的一切结果失效,
        // 并恢复被 duck 压低的音量
        startListening()
        // 重建生成完成监听: 上面 cancel 掉了, 不重建的话下一轮 AI 说完
        // 永远回不到聆听, 通话会卡在"正在说话"
        startGenerationDoneMonitor()
    }

    /**
     * 监听 ASR 状态 (用于非流式 ASR 如 SiliconFlow).
     * 当 ASR 从 Recording -> Idle 且转写不为空时, 立即发送.
     *
     * 加了 !isMuted 判断, 避免静音操作本身触发的 Recording→非Recording 跳变被误判成"该发送了".
     */
    private fun startAsrMonitor() {
        asrMonitorJob?.cancel()
        asrMonitorJob = serviceScope.launch {
            var wasRecording = false
            asr.state.collect { asrState ->
                val isRecording = asrState.isRecording

                // 检测到从 Recording 变为非 Recording
                if (wasRecording && !isRecording && !isMuted && _uiState.value.status == VoiceCallStatus.Listening) {
                    val transcript = asrState.transcript.trim()
                    if (transcript.isNotEmpty() && _uiState.value.autoSendEnabled) {
                        Log.d(TAG, "ASR monitor: Auto-send after ASR completed: $transcript")
                        sendCurrentMessage()
                    } else {
                        // 转写为空(没说话/未识别到): 非流式 ASR (SiliconFlow)
                        // 此时已停在 Idle, 不重启的话音波球不动、下一句说话发不出去.
                        // 流式 ASR 的 start() 有 isRecording 守卫, 重复调用无副作用.
                        if (!isMuted && _uiState.value.status == VoiceCallStatus.Listening) {
                            runCatching {
                                asr.start { t -> _uiState.update { it.copy(userTranscript = t) } }
                            }.onFailure { Log.e(TAG, it.toString(), it) }
                        }
                    }
                }

                wasRecording = isRecording
            }
        }
    }

    /**
     * 切换静音. 在任何状态下都要能生效/取消, 不再判断 status.
     * 静音 = 模型听不到; 取消静音 = 不管 Listening 还是 Speaking 都重新开始监听.
     */
    fun toggleMute() {
        isMuted = !isMuted
        _uiState.update { it.copy(isMuted = isMuted) }

        try {
            if (isMuted) {
                asr.stop()
            } else {
                // 不管当前是 Listening 还是 Speaking, 取消静音都要重新开始监听
                asr.start { transcript ->
                    _uiState.update { it.copy(userTranscript = transcript) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换静音状态失败, isMuted=$isMuted", e)
            _uiState.update { it.copy(errorMessage = "麦克风切换失败: ${e.message}") }
        }
    }

    /**
     * 切换自动发送模式. UI 上不再挂载按钮, 但保留方法 (autoSendEnabled 字段仍在用).
     */
    fun toggleAutoSend() {
        _uiState.update { it.copy(autoSendEnabled = !it.autoSendEnabled) }
    }

    /**
     * 挂断 / 结束通话.
     * 额外复位 _activeConversationId 和移除前台通知.
     */
    fun endCall() {
        vadJob?.cancel()
        speakingMonitorJob?.cancel()
        conversationMonitorJob?.cancel()
        asrMonitorJob?.cancel()
        interruptDetectJob?.cancel()
        playbackTrackerJob?.cancel()
        // 通话结束: 之后任何迟到的异步结果身份校验都会失败, 不会再动状态
        turnTracker.endCall()
        // 恢复音量, 否则下一通电话继承上一通被 duck 压低的音量
        runCatching { tts.setVolume(1f) }
        asr.stop()
        tts.stop()
        _uiState.update {
            it.copy(status = VoiceCallStatus.Idle)
        }
        _activeConversationId.value = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground 失败", e)
        }
    }

    /**
     * 更新振幅数据 (供 UI 动画使用)
     */
    fun updateAmplitudes(amplitudes: List<Float>) {
        _uiState.update { it.copy(amplitudes = amplitudes) }
    }

    /**
     * 构建通话通知. 通话中这种更醒目、带操作按钮的通知.
     */
    private fun buildNotification(state: VoiceCallUiState): android.app.Notification {
        val contentText = when (state.status) {
            VoiceCallStatus.Listening -> "正在聆听..."
            VoiceCallStatus.Processing -> "正在思考..."
            VoiceCallStatus.Speaking -> "正在说话..."
            VoiceCallStatus.Error -> state.errorMessage ?: "通话出错"
            VoiceCallStatus.Idle -> "通话中"
        }

        // 点击通知本体: 回到 RouteActivity 并导航到 VoiceCallPage
        val contentIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("openVoiceCallConversationId", conversationId.toString())
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 通知上的"挂断"按钮: 直接发一个带 ACTION_HANG_UP 的 Intent 给自己这个 Service
        val hangUpIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceCallService::class.java).apply { action = ACTION_HANG_UP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("语音通话")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.small_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "挂断", hangUpIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 兜底, 防止外部通过 stopService 直接杀掉时状态没清理干净
            endCall()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 清理失败", e)
        }
        serviceScope.cancel()
    }
}

