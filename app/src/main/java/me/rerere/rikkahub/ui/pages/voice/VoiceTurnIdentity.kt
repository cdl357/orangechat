/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.voice

import java.util.concurrent.atomic.AtomicLong

/**
 * 语音通话身份协议。
 *
 * ## 为什么一个 status 枚举不够
 * 语音通话里同时有好几条异步链路在跑: ASR 转写、模型流式生成、TTS 合成、
 * 音频播放、VAD 计时、打断检测。它们的回调都可能"迟到" —— 在用户已经抢话、
 * 已经进入下一轮之后才返回。如果只看一个全局 [VoiceCallStatus], 迟到的旧结果
 * 会直接改写新一轮的状态: 上一句的 TTS 片段插进这一句、上一轮的
 * "生成完成"事件把新一轮踢回聆听、旧协程的取消动作停掉新的播放。
 *
 * ## 三层身份
 * - [callId]: 一通完整通话。挂断再打是新的 call。
 * - [turnId]: 用户的一次发言 (一问一答算一个 turn)。抢话会开新 turn。
 * - [generationId]: 对这一轮回答的某次生成。同一 turn 内重试会换 generation。
 *
 * 所有异步结果在改状态之前, 先用 [VoiceTurnTracker.isActive] 验一次身份,
 * 不匹配就整个丢弃。这样"旧任务污染新轮次"在结构上就不可能发生, 而不是靠
 * 加更多布尔标志去打补丁。
 */
data class VoiceTurnIdentity(
    val callId: Long,
    val turnId: Long,
    val generationId: Long,
) {
    override fun toString(): String = "call=$callId/turn=$turnId/gen=$generationId"
}

/**
 * 单调递增的身份分发器。
 *
 * 所有 id 都是进程内单调递增的 Long, 不复用。复用会让"迟到的旧结果"有机会
 * 撞上同一个 id 而被误认为合法, 这是 PDF 里 clip claim 加 nonce 想解决的同一个问题;
 * 单调递增在单进程内已经足够, 不需要额外 nonce。
 */
class VoiceTurnTracker {
    private val callSeq = AtomicLong(0)
    private val turnSeq = AtomicLong(0)
    private val genSeq = AtomicLong(0)

    @Volatile
    private var current: VoiceTurnIdentity? = null

    /** 当前活跃身份, null 表示通话未开始或已结束 */
    val active: VoiceTurnIdentity?
        get() = current

    /** 开一通新通话, 同时开出第一个 turn 和 generation */
    fun newCall(): VoiceTurnIdentity {
        val id = VoiceTurnIdentity(
            callId = callSeq.incrementAndGet(),
            turnId = turnSeq.incrementAndGet(),
            generationId = genSeq.incrementAndGet(),
        )
        current = id
        return id
    }

    /**
     * 开新一轮 (用户又说了一句, 或者抢话)。
     * 通话未开始时返回 null —— 不隐式补一个 call 出来, 那样会掩盖调用顺序错误。
     */
    fun newTurn(): VoiceTurnIdentity? {
        val prev = current ?: return null
        val id = prev.copy(
            turnId = turnSeq.incrementAndGet(),
            generationId = genSeq.incrementAndGet(),
        )
        current = id
        return id
    }

    /** 同一轮内换一次生成 (重试) */
    fun newGeneration(): VoiceTurnIdentity? {
        val prev = current ?: return null
        val id = prev.copy(generationId = genSeq.incrementAndGet())
        current = id
        return id
    }

    /**
     * 身份校验。这是整个协议唯一的判断入口, 所有异步边界都调它。
     * identity 为 null (调用方没拿到身份) 一律判为失效, fail closed。
     */
    fun isActive(identity: VoiceTurnIdentity?): Boolean {
        if (identity == null) return false
        return identity == current
    }

    /** 只校验是否还在同一通话里 (挂断检测用, 不关心 turn/generation) */
    fun isSameCall(identity: VoiceTurnIdentity?): Boolean {
        if (identity == null) return false
        return identity.callId == current?.callId
    }

    /** 通话结束, 之后任何迟到结果都不再被接受 */
    fun endCall() {
        current = null
    }
}
