/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.asr

import kotlinx.coroutines.flow.StateFlow

interface ASRController {
    val state: StateFlow<ASRState>
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun dispose()

    /**
     * 丢掉已累积的录音, 只保留最后 [keepTailMs] 毫秒 (预卷)。
     *
     * 语音通话抢话时需要它。AI 说话期间麦克风一直开着 (打断检测要读音量),
     * 缓冲里因此混进了从扬声器录回来的 AI 声音。确认抢话后如果直接拿这个缓冲去
     * 转写, 结果里会掺着 AI 自己刚说的话。
     *
     * 但也不能整个清空: 打断是在用户已经说了几百毫秒之后才确认的, 清空等于把
     * 用户开头那几个字吞掉。保留最后约 1 秒, 刚好覆盖"从开口到确认打断"这段,
     * 用户的第一个字不会丢。
     *
     * 只有需要自己攒 PCM 再整段上传的 controller 才需要实现 (非流式);
     * 流式 controller 是边录边传, 没有可截断的缓冲, 用空默认实现。
     */
    fun resetBufferKeepingTail(keepTailMs: Long) {}

    /**
     * 是否允许 controller 自己在检测到静音后停止录音并提交转写。
     *
     * 默认 true, 保持原有行为。语音通话在 AI 说话期间会把它置为 false:
     * 那段时间麦克风仍然开着 (打断检测需要读音量), 但扬声器传出的 AI 声音会被
     * 录进来。如果 controller 照常"检测到静音就停下来转写", 它会把 AI 自己的
     * 回声送去 ASR 转写一遍 —— 白花一次转写费用, 而且拿到的文本毫无意义。
     *
     * 流式 controller 不需要这个开关, 因此给了空默认实现。
     */
    fun setAutoStopOnSilence(enabled: Boolean) {}
}

