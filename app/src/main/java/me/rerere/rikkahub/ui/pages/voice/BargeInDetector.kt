/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.voice

/**
 * 两阶段自然抢话检测器 (barge-in)。
 *
 * ## 为什么需要它
 * 旧实现是"单帧音量超过 0.15f 就立刻打断"。麦克风在整场通话里持续开着,
 * AI 的声音从扬声器出来又被录回麦克风, 于是 AI 每次刚开口就被自己的回声打断,
 * 界面立刻弹回"正在聆听", 通话根本进行不下去。
 *
 * ## 两阶段策略
 * 1. 连续人声达到 [duckMs] -> [BargeInEvent.Duck]: 先把 TTS 音量压低, 不中断生成。
 *    这一步是可逆的, 代价很小, 所以阈值可以偏灵敏。
 * 2. 连续人声达到 [interruptMs] -> [BargeInEvent.Interrupt]: 确认用户真的要抢话,
 *    此时才取消生成、停 TTS、回到聆听。
 * 3. 压低后人声在 [restoreMs] 内消失 -> [BargeInEvent.Restore]: 恢复原音量,
 *    证据清零。短促的咳嗽、键盘声、桌面碰撞会走到这条路径, 不会误伤一整轮回答。
 *
 * ## 回声抑制的三道防线
 * - **启动冷却** [startupCooldownMs]: 播放刚开始的这段时间里只清证据不判断,
 *   避开扬声器启动的爆音和音频焦点切换噪声。
 * - **播放期高门槛** [playbackThreshold] 明显高于 [baseThreshold]: 播放期间以及
 *   播放结束后的 [echoTailMs] 尾窗内, 都要求更大的音量才算人声。尾窗是必要的,
 *   因为 AudioTrack 停止和实际声音消失之间有延迟。
 * - **连续性要求**: 单帧超阈值不算数, 必须连续累积。回声通常跟随 AI 语句的
 *   节奏起伏, 很难连续 520ms 都压住门槛。
 *
 * ## 转写增长只做确认, 不做触发
 * [BargeInInput.transcriptGrew] 单独出现不会打断。原因是非流式 ASR
 * (SiliconFlow 这类"录一段→停→转写") 的结果会迟到, 上一句的转写可能在
 * 本轮 Speaking 期间才落下来, 长度一变就误判成用户在抢话。
 * 只有在已经 duck (说明确实有连续人声) 之后, 转写增长才作为提前确认的证据。
 *
 * 这个类是纯逻辑, 不碰 Android API, 不发网络请求, 只回答"现在该不该 duck/interrupt",
 * 因此可以用虚拟时钟做单元测试 (见 BargeInDetectorTest)。
 */
class BargeInDetector(
    /** 连续人声多久后压低 TTS 音量 */
    private val duckMs: Long = 240L,
    /** 连续人声多久后确认打断 */
    private val interruptMs: Long = 520L,
    /** 压低后静音多久恢复原音量 */
    private val restoreMs: Long = 160L,
    /** 播放刚开始后的冷却窗口, 这段时间内不判断 */
    private val startupCooldownMs: Long = 260L,
    /** 播放结束后仍按播放期门槛处理的尾窗 */
    private val echoTailMs: Long = 220L,
    /** 非播放期的人声门槛 */
    private val baseThreshold: Float = 0.12f,
    /** 播放期 (含尾窗) 的人声门槛, 必须高于 baseThreshold */
    private val playbackThreshold: Float = 0.30f,
) {
    private var voicedMs: Long = 0L
    private var silentMs: Long = 0L
    private var ducked: Boolean = false
    private var interrupted: Boolean = false

    /** 当前是否处于压低音量状态, 供调用方决定要不要恢复 */
    val isDucked: Boolean
        get() = ducked

    /**
     * 喂一帧音频统计, 返回这一帧引发的事件 (没有就是 null)。
     *
     * 同一次 [push] 最多返回一个事件。Duck 和 Interrupt 不会在同一帧同时返回:
     * duckMs < interruptMs, 所以正常会先在某一帧返回 Duck, 后续帧再返回 Interrupt。
     */
    fun push(input: BargeInInput): BargeInEvent? {
        if (interrupted) return null

        val inPlaybackWindow = input.playbackActive ||
            (input.playbackEndedAtMs > 0L && input.nowMs - input.playbackEndedAtMs <= echoTailMs)

        // 启动冷却: 清掉证据直接返回, 避开扬声器启动噪声
        if (input.playbackActive &&
            input.playbackStartedAtMs > 0L &&
            input.nowMs - input.playbackStartedAtMs < startupCooldownMs
        ) {
            voicedMs = 0L
            silentMs = 0L
            return null
        }

        val threshold = if (inPlaybackWindow) playbackThreshold else baseThreshold
        val voiced = input.amplitude >= threshold

        if (voiced) {
            voicedMs += input.frameMs
            silentMs = 0L

            // 已经压低过音量, 说明确实有连续人声; 此时转写增长可以提前确认打断
            if (ducked && input.transcriptGrew) {
                interrupted = true
                return BargeInEvent.Interrupt
            }

            if (!ducked && voicedMs >= duckMs) {
                ducked = true
                return BargeInEvent.Duck
            }

            if (voicedMs >= interruptMs) {
                interrupted = true
                return BargeInEvent.Interrupt
            }

            return null
        }

        silentMs += input.frameMs
        if (ducked && silentMs >= restoreMs) {
            ducked = false
            voicedMs = 0L
            return BargeInEvent.Restore
        }
        // 未 duck 状态下的零星静音: 证据缓慢衰减, 避免"说一下停一下"累积成假打断
        if (!ducked && silentMs >= restoreMs) {
            voicedMs = 0L
        }
        return null
    }

    /** 新一轮开始时复位。注意 ducked 要由调用方负责恢复音量后再 reset。 */
    fun reset() {
        voicedMs = 0L
        silentMs = 0L
        ducked = false
        interrupted = false
    }
}

/**
 * 一帧检测输入。
 *
 * @param amplitude 这一帧的归一化音量 (0f~1f), 来自 ASR 侧的 RMS 计算
 * @param frameMs 这一帧代表多少毫秒 (即检测循环的 delay 间隔)
 * @param nowMs 当前时刻, 传入而非内部取, 便于测试用虚拟时钟
 * @param playbackActive TTS 此刻是否正在出声
 * @param playbackStartedAtMs 最近一次开始播放的时刻, 0 表示还没播过
 * @param playbackEndedAtMs 最近一次播放结束的时刻, 0 表示尚未结束过
 * @param transcriptGrew 自上一帧以来 ASR 转写是否变长
 */
data class BargeInInput(
    val amplitude: Float,
    val frameMs: Long,
    val nowMs: Long,
    val playbackActive: Boolean,
    val playbackStartedAtMs: Long = 0L,
    val playbackEndedAtMs: Long = 0L,
    val transcriptGrew: Boolean = false,
)

enum class BargeInEvent {
    /** 压低 TTS 音量, 生成继续 */
    Duck,

    /** 确认打断: 取消生成 + 停 TTS + 回到聆听 */
    Interrupt,

    /** 误触发消失, 恢复原音量 */
    Restore,
}
