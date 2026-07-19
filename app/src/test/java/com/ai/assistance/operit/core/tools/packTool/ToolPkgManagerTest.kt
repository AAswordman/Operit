package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import java.util.ArrayDeque
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ToolPkgManagerTest {
    @Test
    fun `execution context cannot be shared by different containers`() {
        val engine = mock<JsEngine>()
        val manager = createManager(engine)

        assertSame(
            engine,
            manager.getToolPkgExecutionEngine(
                contextKey = "toolpkg_main:package-a",
                containerPackageName = "package-a"
            )
        )

        assertThrows(IllegalStateException::class.java) {
            manager.getToolPkgExecutionEngine(
                contextKey = "toolpkg_main:package-a",
                containerPackageName = "package-b"
            )
        }
    }

    @Test
    fun `destroying container releases every owned context only`() {
        val mainEngine = mock<JsEngine>()
        val xmlEngine = mock<JsEngine>()
        val otherEngine = mock<JsEngine>()
        val manager = createManager(mainEngine, xmlEngine, otherEngine)

        manager.getToolPkgExecutionEngine("toolpkg_main:package-a", "package-a")
        manager.getToolPkgExecutionEngine("toolpkg_xml_render:package-a:screen:node", "package-a")
        manager.getToolPkgExecutionEngine("toolpkg_main:package-b", "package-b")

        manager.destroyToolPkgExecutionEngines("package-a")

        assertNull(manager.findToolPkgExecutionEngine("toolpkg_main:package-a"))
        assertNull(manager.findToolPkgExecutionEngine("toolpkg_xml_render:package-a:screen:node"))
        assertSame(otherEngine, manager.findToolPkgExecutionEngine("toolpkg_main:package-b"))
        verify(mainEngine).destroy()
        verify(xmlEngine).destroy()
        verify(otherEngine, never()).destroy()
    }

    @Test
    fun `stale context release does not destroy replacement engine`() {
        val previousEngine = mock<JsEngine>()
        val replacementEngine = mock<JsEngine>()
        val manager = createManager(previousEngine, replacementEngine)
        val contextKey = "toolpkg_xml_render:package-a:screen:node"

        manager.getToolPkgExecutionEngine(contextKey, "package-a")
        manager.releaseToolPkgExecutionEngine(contextKey, previousEngine)
        manager.getToolPkgExecutionEngine(contextKey, "package-a")

        manager.releaseToolPkgExecutionEngine(contextKey, previousEngine)

        assertSame(replacementEngine, manager.findToolPkgExecutionEngine(contextKey))
        verify(previousEngine).destroy()
        verify(replacementEngine, never()).destroy()
    }

    @Test
    fun `destroy closes registry and rejects later acquisition`() {
        val engine = mock<JsEngine>()
        val manager = createManager(engine)
        manager.getToolPkgExecutionEngine("toolpkg_main:package-a", "package-a")

        manager.destroy()

        verify(engine).destroy()
        assertThrows(IllegalStateException::class.java) {
            manager.getToolPkgExecutionEngine("toolpkg_main:package-a", "package-a")
        }
    }

    private fun createManager(vararg engines: JsEngine): ToolPkgManager {
        val availableEngines = ArrayDeque(engines.toList())
        return ToolPkgManager(mock<Context>()) {
            availableEngines.removeFirst()
        }
    }
}
