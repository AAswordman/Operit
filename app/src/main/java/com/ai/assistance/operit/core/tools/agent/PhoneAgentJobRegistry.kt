package com.ai.assistance.operit.core.tools.agent

import com.ai.assistance.operit.util.logging.AppLogger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

object PhoneAgentJobRegistry {
    private const val TAG = "PhoneAgentJobRegistry"

    private val jobsByAgentId = ConcurrentHashMap<String, MutableSet<Job>>()

    fun register(agentId: String, job: Job) {
        val existing = jobsByAgentId[agentId]
        val set =
            existing
                ?: run {
                    val newSet = Collections.newSetFromMap(ConcurrentHashMap<Job, Boolean>())
                    val raced = jobsByAgentId.putIfAbsent(agentId, newSet)
                    raced ?: newSet
                }
        set.add(job)

        job.invokeOnCompletion {
            unregister(agentId, job)
        }

        AppLogger.d(TAG, "Registered job for agentId=$agentId, activeAgents=${jobsByAgentId.size}")
    }

    fun unregister(agentId: String, job: Job) {
        val set = jobsByAgentId[agentId] ?: return
        set.remove(job)
        if (set.isEmpty()) {
            jobsByAgentId.remove(agentId)
        }
    }

    fun cancelAgent(agentId: String, reason: String = "User cancelled") {
        val jobs = jobsByAgentId[agentId]?.toList() ?: return
        if (jobs.isEmpty()) return

        AppLogger.d(TAG, "Cancelling ${jobs.size} PhoneAgent jobs for agentId=$agentId")

        jobs.forEach { job ->
            try {
                job.cancel(CancellationException(reason))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to cancel job for agentId=$agentId", e)
            }
        }
    }

    fun cancelAll(reason: String = "User cancelled") {
        val snapshot = jobsByAgentId.entries.flatMap { (agentId, set) ->
            set.map { job -> agentId to job }
        }

        if (snapshot.isEmpty()) return

        AppLogger.d(TAG, "Cancelling ${snapshot.size} PhoneAgent jobs for ${jobsByAgentId.size} agents")

        snapshot.forEach { (agentId, job) ->
            try {
                job.cancel(CancellationException(reason))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to cancel job for agentId=$agentId", e)
            }
        }
    }
}
