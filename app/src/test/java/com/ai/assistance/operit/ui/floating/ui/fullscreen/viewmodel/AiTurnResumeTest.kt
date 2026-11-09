package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 波浪模式下「AI 回合结束后恢复收音」的状态流转。
 *
 * 这些用例针对 issue #1332 里无法继续收音、也退不出语音态（软键盘因此没有入口）
 * 的那条路径：恢复判定必须由回合信号的下降沿触发，而不是定时采样。
 *
 * `awaitAiTurnFinished` 返回「是否观测到完整回合」，于是「应当恢复」和「应当继续等待」
 * 两种语义都能用普通断言表达，不需要并发与调度器配合。
 */
class AiTurnResumeTest {

    @Test
    fun idleThenTurnStartThenFinish_resumes() = runTest {
        // 发送消息到回合变忙之间会先出现 false，这是正常时序。
        assertTrue(awaitAiTurnFinished(flowOf(false, true, false)))
    }

    @Test
    fun turnAlreadyActiveAtObservation_resumesWhenItFallsIdle() = runTest {
        assertTrue(awaitAiTurnFinished(flowOf(true, false)))
    }

    @Test
    fun burstTurn_stillResumes() = runTest {
        // 回归用例：回合的开始与结束挤在一起时，旧的每 120ms 采样写法会整段错过
        // busy=true，于是永远不满足「先 busy 再 idle」，恢复标志永久闩死。
        assertTrue(awaitAiTurnFinished(flowOf(false, true, true, false)))
    }

    @Test
    fun onlyFirstFallingEdgeResumes() = runTest {
        // 只认本轮的第一个下降沿：同一轮里后续出现的 busy 不应阻塞本轮恢复。
        assertTrue(awaitAiTurnFinished(flowOf(true, false, true, false)))
    }

    @Test
    fun turnNotStartedYet_keepsWaiting() = runTest {
        // 回合还没开始就恢复收音，会把随后播报的 TTS 录进麦克风。
        assertFalse(awaitAiTurnFinished(flowOf(false, false, false)))
    }

    @Test
    fun turnStillSpeaking_keepsWaiting() = runTest {
        // TTS 播报尚未结束时不得恢复收音。
        assertFalse(awaitAiTurnFinished(flowOf(false, true, true)))
    }
}
