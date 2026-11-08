package com.ai.assistance.operit.core.tools.agent

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.builtin.ToolGetter
import com.ai.assistance.operit.util.media.ImagePoolManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, read-only collection benchmark. No model request or phone input is sent. */
@RunWith(AndroidJUnit4::class)
class PhoneObservationBenchmarkTest {
    @Test fun compareNodesAndPreparedScreenshotsOnTheForegroundPage() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires a manually prepared foreground page and explicit opt-in",
            args.getString("phone_observation_benchmark") == "true")
        val rounds = requireNotNull(args.getString("phone_benchmark_rounds")).toInt()
        require(rounds in 4..30) { "phone_benchmark_rounds must be 4..30" }
        val expectedPackage = requireNotNull(args.getString("phone_benchmark_package"))
        require(expectedPackage.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val handler = ActionHandler(context, metrics.widthPixels, metrics.heightPixels, ToolGetter.getUITools(context))
        val performance = PhoneAgentPerformance()
        val driver = AccessibilityPhoneUiDriver(context,
            inputScope = { error("Benchmark cannot send input") },
            tapAt = { _, _ -> error("Benchmark cannot tap") }, performance = performance)
        val samples = JSONArray()
        runBlocking(Dispatchers.Default) {
            for (round in 0 until rounds) {
                val modes = if (round % 2 == 0) listOf("nodes", "screenshot") else listOf("screenshot", "nodes")
                for (mode in modes) {
                    val sample = JSONObject().put("round", round).put("mode", mode)
                    var imageId: String? = null
                    val started = System.nanoTime()
                    var preparedNs: Long? = null
                    try {
                        if (mode == "nodes") {
                            val state = driver.read()
                            check(state.packageName == expectedPackage) { "Foreground package changed" }
                            val summary = state.summary(args.getString("phone_benchmark_task").orEmpty())
                            preparedNs = System.nanoTime() - started
                            check(summary.shown.isNotEmpty()) { "No usable nodes in summary" }
                            sample.put("read_nodes", state.nodes.size).put("shown_nodes", summary.shown.size)
                                .put("payload_characters", summary.payload.length)
                        } else {
                            val link = handler.captureScreenshotForAgent()
                            preparedNs = System.nanoTime() - started
                            check(!link.isNullOrBlank()) { "Screenshot preparation failed" }
                            sample.put("image_prepared", true)
                            // Delete only the image created by this sample, without clearing chat images.
                            val id = Regex("""id="([^"]+)""" ).find(link)?.groupValues?.get(1)
                            check(id != null) { "Screenshot reference has no image ID" }
                            imageId = id
                        }
                        sample.put("success", true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        sample.put("success", false).put("error", e.javaClass.simpleName)
                        Log.e("PhoneObservationBenchmark", "Observation sample failed: $mode", e)
                    } finally {
                        sample.put("elapsed_ms", (preparedNs ?: (System.nanoTime() - started)) / 1_000_000.0)
                        samples.put(sample)
                        imageId?.let { id ->
                            try {
                                ImagePoolManager.removeImage(id)
                            } catch (e: Exception) {
                                sample.put("cleanup_error", e.javaClass.simpleName)
                                Log.e("PhoneObservationBenchmark", "Could not remove benchmark image", e)
                            }
                        }
                    }
                }
            }
        }
        val report = JSONObject().put("scope", "observation_preparation_only_no_model_or_actions")
            .put("expected_package", expectedPackage).put("first_pair_included", true)
            .put("samples", samples).put("node_read_metrics", performance.summary())
        for (mode in listOf("nodes", "screenshot")) {
            val matching = (0 until samples.length()).map { samples.getJSONObject(it) }
                .filter { it.getString("mode") == mode }
            val times = matching.filter { it.getBoolean("success") && it.getInt("round") > 0 }
                .map { it.getDouble("elapsed_ms") }.sorted()
            val stats = JSONObject().put("attempts", matching.size)
                .put("failures", matching.count { !it.getBoolean("success") })
                .put("warm_samples", times.size)
            if (times.isNotEmpty()) {
                val median = if (times.size % 2 == 0) (times[times.size / 2 - 1] + times[times.size / 2]) / 2 else times[times.size / 2]
                val p95Index = kotlin.math.ceil(times.size * 0.95).toInt() - 1
                stats.put("warm_median_ms", median).put("warm_p95_ms", times[p95Index])
            }
            report.put(mode, stats)
        }
        val output = File(context.filesDir, "phone-observation-benchmark.json")
        output.writeText(report.toString(2))
        Log.i("PhoneObservationBenchmark", "Report: ${output.absolutePath}; $report")
        assertTrue("Samples failed; inspect the saved report, do not interpret failures as fast reads",
            (0 until samples.length()).all { samples.getJSONObject(it).getBoolean("success") })
    }
}
