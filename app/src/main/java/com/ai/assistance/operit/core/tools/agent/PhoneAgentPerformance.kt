package com.ai.assistance.operit.core.tools.agent

/** Timings are cumulative; condition_wait_ms is a subset of execution_ms, not an extra stage. */
internal class PhoneAgentPerformance {
    var observations = 0
    var screenshots = 0
    var modelCalls = 0
    var actions = 0
    var localSequences = 0
    var observationMs = 0L
    var modelMs = 0L
    var executionMs = 0L
    var conditionWaitMs = 0L
    var pausedMs = 0L

    fun summary(): String =
        "observations=$observations screenshots=$screenshots model_calls=$modelCalls actions=$actions " +
            "local_sequences=$localSequences observation_ms=$observationMs model_ms=$modelMs " +
            "execution_ms=$executionMs condition_wait_ms=$conditionWaitMs paused_ms=$pausedMs"

    fun reset() {
        observations = 0; screenshots = 0; modelCalls = 0; actions = 0; localSequences = 0
        observationMs = 0; modelMs = 0; executionMs = 0; conditionWaitMs = 0; pausedMs = 0
    }
}
