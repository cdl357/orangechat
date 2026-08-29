/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两阶段抢话检测的时序测试。
 *
 * 这类系统必须测"时序"而不只是测返回值, 所以全部用虚拟时钟 (自己累加 nowMs),
 * 不在测试里真的 sleep —— 真 sleep 会让套件又慢又不稳定。
 */
class BargeInDetectorTest {

    private val frameMs = 60L

    /**
     * 喂 N 毫秒的音频, 返回这期间收集到的事件序列。
     * clock 用引用传递的方式模拟虚拟时钟前进。
     */
    private fun feed(
        detector: BargeInDetector,
        durationMs: Long,
        amplitude: Float,
        clock: LongArray,
        playbackActive: Boolean = true,
        playbackStartedAtMs: Long = 0L,
        playbackEndedAtMs: Long = 0L,
        transcriptGrew: Boolean = false,
    ): List<BargeInEvent> {
        val events = mutableListOf<BargeInEvent>()
        var fed = 0L
        while (fed < durationMs) {
            clock[0] += frameMs
            detector.push(
                BargeInInput(
                    amplitude = amplitude,
                    frameMs = frameMs,
                    nowMs = clock[0],
                    playbackActive = playbackActive,
                    playbackStartedAtMs = playbackStartedAtMs,
                    playbackEndedAtMs = playbackEndedAtMs,
                    transcriptGrew = transcriptGrew,
                )
            )?.let { events.add(it) }
            fed += frameMs
        }
        return events
    }

    @Test
    fun `播放期回声不会触发打断`() {
        // 回声典型强度 0.20 左右, 低于播放期门槛 0.30。
        // 这是旧实现最致命的 bug: 单帧 > 0.15 就打断, AI 每次刚开口就被自己打断。
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val events = feed(
            detector,
            durationMs = 3_000L,
            amplitude = 0.20f,
            clock = clock,
            playbackActive = true,
            playbackStartedAtMs = 1_000L, // 早就过了启动冷却
        )
        assertTrue("回声不该产生任何事件, 实际: $events", events.isEmpty())
        assertFalse(detector.isDucked)
    }

    @Test
    fun `连续真人声先duck再interrupt`() {
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)

        // 240ms 连续人声 -> duck
        val duckEvents = feed(
            detector, durationMs = 300L, amplitude = 0.45f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Duck), duckEvents)
        assertTrue(detector.isDucked)

        // 继续到累计 520ms -> interrupt
        val interruptEvents = feed(
            detector, durationMs = 300L, amplitude = 0.45f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Interrupt), interruptEvents)
    }

    @Test
    fun `短促误触发只duck然后restore不打断`() {
        // 咳嗽、键盘声、桌面碰撞: 够响但不持续
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)

        val duck = feed(
            detector, durationMs = 300L, amplitude = 0.5f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Duck), duck)

        // 声音消失 160ms 以上 -> restore, 整轮回答没被打断
        val restore = feed(
            detector, durationMs = 300L, amplitude = 0.02f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Restore), restore)
        assertFalse(detector.isDucked)
    }

    @Test
    fun `播放启动冷却期内不判断`() {
        // 扬声器刚启动的爆音 + 音频焦点切换噪声, 可能很响
        val detector = BargeInDetector()
        val clock = longArrayOf(1_000L)
        val startedAt = 1_000L
        val events = feed(
            detector, durationMs = 240L, amplitude = 0.9f, clock = clock,
            playbackActive = true, playbackStartedAtMs = startedAt
        )
        assertTrue("冷却期内不该有事件, 实际: $events", events.isEmpty())
    }

    @Test
    fun `回声尾窗内仍按播放期高门槛处理`() {
        // AudioTrack 停止和实际声音消失之间有延迟, 尾窗必须存在
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val endedAt = 10_000L
        val events = feed(
            detector, durationMs = 600L, amplitude = 0.20f, clock = clock,
            playbackActive = false,
            playbackEndedAtMs = endedAt,
        )
        // 前 220ms 在尾窗内 (0.20 < 0.30 不算人声), 之后按 baseThreshold 0.12 算人声,
        // 需要连续 520ms 才 interrupt, 600ms 里尾窗外只有约 380ms -> 只到 duck
        assertTrue(
            "尾窗内不该立刻 interrupt, 实际: $events",
            !events.contains(BargeInEvent.Interrupt)
        )
    }

    @Test
    fun `非播放期用较低门槛`() {
        // 没有回声污染时, 用户轻声说话也该被接住
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val events = feed(
            detector, durationMs = 600L, amplitude = 0.15f, clock = clock,
            playbackActive = false,
        )
        assertTrue("应当至少 duck, 实际: $events", events.contains(BargeInEvent.Duck))
        assertTrue("应当 interrupt, 实际: $events", events.contains(BargeInEvent.Interrupt))
    }

    @Test
    fun `转写增长单独出现不触发打断`() {
        // 非流式 ASR 的转写会迟到: 上一句的结果可能在本轮 Speaking 期间才落下来。
        // 只凭长度变化就打断会导致 AI 频繁被"幽灵抢话"。
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val events = feed(
            detector, durationMs = 2_000L, amplitude = 0.01f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L,
            transcriptGrew = true,
        )
        assertTrue("静音下转写增长不该打断, 实际: $events", events.isEmpty())
    }

    @Test
    fun `已duck后转写增长可提前确认打断`() {
        // 已经 duck 说明确实有连续人声; 此时转写增长是可信的确认证据,
        // 不必等满 520ms
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)

        val duck = feed(
            detector, durationMs = 300L, amplitude = 0.45f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Duck), duck)

        clock[0] += frameMs
        val event = detector.push(
            BargeInInput(
                amplitude = 0.45f,
                frameMs = frameMs,
                nowMs = clock[0],
                playbackActive = true,
                playbackStartedAtMs = 1_000L,
                transcriptGrew = true,
            )
        )
        assertEquals(BargeInEvent.Interrupt, event)
    }

    @Test
    fun `interrupt之后不再产生事件直到reset`() {
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        feed(
            detector, durationMs = 700L, amplitude = 0.5f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        // 已经 interrupt 过, 后续帧一律沉默 (避免同一轮重复打断)
        val after = feed(
            detector, durationMs = 1_000L, amplitude = 0.9f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertTrue("interrupt 后不该再有事件, 实际: $after", after.isEmpty())

        detector.reset()
        val afterReset = feed(
            detector, durationMs = 300L, amplitude = 0.5f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertEquals(listOf(BargeInEvent.Duck), afterReset)
    }

    @Test
    fun `断续的声音不会累积成假打断`() {
        // "说一下停一下"的环境噪声: 每次都不够长, 证据要衰减而不是累加
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val all = mutableListOf<BargeInEvent>()
        repeat(6) {
            all += feed(
                detector, durationMs = 120L, amplitude = 0.5f, clock = clock,
                playbackActive = true, playbackStartedAtMs = 1_000L
            )
            all += feed(
                detector, durationMs = 240L, amplitude = 0.01f, clock = clock,
                playbackActive = true, playbackStartedAtMs = 1_000L
            )
        }
        assertFalse("断续噪声不该 interrupt, 实际: $all", all.contains(BargeInEvent.Interrupt))
    }

    @Test
    fun `零音量不产生任何事件`() {
        val detector = BargeInDetector()
        val clock = longArrayOf(10_000L)
        val events = feed(
            detector, durationMs = 3_000L, amplitude = 0f, clock = clock,
            playbackActive = true, playbackStartedAtMs = 1_000L
        )
        assertTrue(events.isEmpty())
        assertNull(
            detector.push(
                BargeInInput(0f, frameMs, clock[0], playbackActive = false)
            )
        )
    }
}
