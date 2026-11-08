package com.ai.assistance.operit.util.network

import java.io.BufferedReader
import java.io.InputStreamReader
import com.ai.assistance.operit.util.logging.AppLogger

object PortProcessKiller {
    private const val TAG = "PortProcessKiller"

    fun killListeners(port: Int): List<Int> {
        if (port <= 0) return emptyList()

        val command =
            """
            PORT=$port
            pids=""
            if command -v ss >/dev/null 2>&1; then
              pids=${'$'}(ss -ltnpH "sport = :$port" 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | sort -u)
            elif command -v netstat >/dev/null 2>&1; then
              pids=${'$'}(netstat -ltnp 2>/dev/null | awk '${'$'}4 ~ /:$port$/ {print ${'$'}7}' | cut -d/ -f1 | grep '^[0-9][0-9]*$' | sort -u)
            elif command -v lsof >/dev/null 2>&1; then
              pids=${'$'}(lsof -nP -iTCP:$port -sTCP:LISTEN -t 2>/dev/null | sort -u)
            fi
            if [ -n "${'$'}pids" ]; then
              for pid in ${'$'}pids; do
                kill -9 "${'$'}pid" 2>/dev/null || true
                echo "${'$'}pid"
              done
            fi
            """.trimIndent()

        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output =
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLines()
                }
            val errorOutput =
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.readText().trim()
                }
            val exitCode = process.waitFor()
            val killedPids = output.mapNotNull { it.trim().toIntOrNull() }.distinct()

            if (killedPids.isNotEmpty()) {
                AppLogger.w(TAG, "Killed listener processes on port $port: $killedPids")
            } else {
                AppLogger.d(TAG, "No listener process found on port $port")
            }

            if (exitCode != 0 && errorOutput.isNotBlank()) {
                AppLogger.w(TAG, "Port cleanup command exited with code $exitCode on port $port: $errorOutput")
            }

            killedPids
        }.onFailure { error ->
            AppLogger.e(TAG, "Failed to clean listener processes on port $port", error)
        }.getOrDefault(emptyList())
    }
}
