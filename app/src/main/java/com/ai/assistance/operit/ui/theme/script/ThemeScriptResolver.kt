package com.ai.assistance.operit.ui.theme.script

import com.ai.assistance.operit.core.tools.javascript.QuickJsNativeRuntime
import com.ai.assistance.operit.data.model.theme.forMode
import com.ai.assistance.operit.data.model.theme.ThemeScriptAppearance
import com.ai.assistance.operit.data.model.theme.ThemeScriptContext
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** 独立实例只计算外观数据；工具引擎、Java 桥和异步作业不参与主题求值。 */
object ThemeScriptResolver {
    suspend fun resolve(bundle: ThemeScriptBundle, context: ThemeScriptContext): ThemeScriptAppearance =
        withContext(Dispatchers.Default) {
            QuickJsNativeRuntime.create(object : QuickJsNativeRuntime.HostBridge {
                override fun onCall(method: String, argsJson: String?): String? =
                    error("Theme scripts do not expose native host calls")
            }).use { runtime ->
                runtime.setExecutionLimits(memoryBytes = 16L * 1024 * 1024, timeoutMs = 100)
                ThemeScriptContract.validate(bundle.manifest)
                require(bundle.source.toByteArray(Charsets.UTF_8).size <= ThemeScriptContract.MAX_SOURCE_BYTES) { "Theme script is too large" }
                val input = context.copy(appearance = bundle.manifest.appearance.forMode(context.mode))
                val encoded = ThemeScriptContract.json.encodeToString(input)
                val setup = """
                    "use strict";
                    Object.defineProperty(globalThis, "NativeInterface", {
                      value: undefined, writable: false, configurable: false
                    });
                    globalThis.__okthemeRender = (function () {
                      const freezeObject = Object.freeze;
                      const isFrozen = Object.isFrozen;
                      const keys = Object.keys;
                      function freeze(value) {
                        if (value && typeof value === "object" && !isFrozen(value)) {
                          keys(value).forEach(function (key) { freeze(value[key]); });
                          freezeObject(value);
                        }
                        return value;
                      }
                      const input = freeze($encoded);
                      const module = { exports: {} };
                      (function (module, exports) {
                        "use strict";
                        ${bundle.source}
                      })(module, module.exports);
                      const theme = module.exports;
                      if (!theme || theme.apiVersion !== 1 || typeof theme.render !== "function") {
                        throw new Error("Expected module.exports = { apiVersion: 1, render(context) }");
                      }
                      return function () {
                        const result = theme.render(input);
                        if (!result || typeof result !== "object" || typeof result.then === "function") {
                          throw new Error("Theme render must return a synchronous appearance object");
                        }
                        return result;
                      };
                    })();
                """.trimIndent()
                requireSuccess(runtime.eval(setup, "oktheme.js"))
                val result = runtime.callFunction("__okthemeRender", "[]", "oktheme.render")
                requireSuccess(result)
                ThemeScriptContract.decodeResult(requireNotNull(result.valueJson), bundle.manifest).forMode(context.mode)
            }
        }

    private fun requireSuccess(result: QuickJsNativeRuntime.EvalResult) {
        check(result.success) {
            listOfNotNull(result.errorMessage, result.errorStack).joinToString("\n").take(4096)
        }
    }
}