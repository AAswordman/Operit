package com.ai.assistance.operit.core.tools.agent

/** Read/prepare timings overlap observation/execution. Never sum them into total_ms again. */
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
    var totalMs = 0L
    var nodeReads = 0
    var nodeReadFailures = 0
    var nodeReadNs = 0L
    var hierarchyNs = 0L
    var parseNs = 0L
    var summaryNs = 0L
    var reusedObservations = 0
    var screenshotAttempts = 0
    var screenshotNs = 0L
    var nodeModelCalls = 0
    var nodeModelMs = 0L
    var imageModelCalls = 0
    var imageModelMs = 0L

    fun summary(): String =
        "observations=$observations screenshots=$screenshots model_calls=$modelCalls actions=$actions " +
            "local_sequences=$localSequences observation_ms=$observationMs model_ms=$modelMs " +
            "execution_ms=$executionMs condition_wait_ms=$conditionWaitMs paused_ms=$pausedMs " +
            "total_ms=$totalMs node_reads=$nodeReads node_read_failures=$nodeReadFailures " +
            "node_read_ms=${nodeReadNs / 1_000_000} hierarchy_ms=${hierarchyNs / 1_000_000} " +
            "parse_ms=${parseNs / 1_000_000} summary_ms=${summaryNs / 1_000_000} " +
            "reused_observations=$reusedObservations screenshot_attempts=$screenshotAttempts " +
            "screenshot_ms=${screenshotNs / 1_000_000} node_model_calls=$nodeModelCalls " +
            "node_model_ms=$nodeModelMs image_model_calls=$imageModelCalls image_model_ms=$imageModelMs"

    fun reset() {
        observations = 0; screenshots = 0; modelCalls = 0; actions = 0; localSequences = 0
        observationMs = 0; modelMs = 0; executionMs = 0; conditionWaitMs = 0; pausedMs = 0
        totalMs = 0; nodeReads = 0; nodeReadFailures = 0; nodeReadNs = 0; hierarchyNs = 0
        parseNs = 0; summaryNs = 0; reusedObservations = 0; screenshotAttempts = 0; screenshotNs = 0
        nodeModelCalls = 0; nodeModelMs = 0; imageModelCalls = 0; imageModelMs = 0
    }
}
