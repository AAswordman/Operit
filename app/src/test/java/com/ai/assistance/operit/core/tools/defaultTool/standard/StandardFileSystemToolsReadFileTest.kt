package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class StandardFileSystemToolsReadFileTest {

    @Test
    fun `read_file applies a supplied line range`() = runBlocking {
        withTextFile("one\ntwo\nthree\nfour") { path ->
            val result =
                StandardFileSystemTools(mock<Context>()).readFile(
                    AITool(
                        name = "read_file",
                        parameters =
                            listOf(
                                ToolParameter("path", path),
                                ToolParameter("start_line", "2"),
                                ToolParameter("end_line", "3")
                            )
                    )
                )

            assertTrue(result.success)
            assertEquals("read_file", result.toolName)
            assertEquals("2| two\n3| three", (result.result as FilePartContentData).content)
        }
    }

    @Test
    fun `read_file without a range still reads the regular file content`() = runBlocking {
        withTextFile("one\ntwo\nthree\nfour") { path ->
            val result =
                StandardFileSystemTools(mock<Context>()).readFile(
                    AITool(
                        name = "read_file",
                        parameters = listOf(ToolParameter("path", path))
                    )
                )

            assertTrue(result.success)
            assertEquals("read_file", result.toolName)
            assertEquals(
                "1| one\n2| two\n3| three\n4| four",
                (result.result as FileContentData).content
            )
        }
    }

    private suspend fun withTextFile(content: String, assertion: suspend (String) -> Unit) {
        val directory = File("build/tmp/read-file-tests").apply { mkdirs() }
        val file = File.createTempFile("read-file-", ".txt", directory)
        try {
            file.writeText(content)
            assertion(androidAbsolutePath(file))
        } finally {
            file.delete()
        }
    }

    private fun androidAbsolutePath(file: File): String {
        val absolutePath = file.absolutePath.replace('\\', '/')
        return if (File.separatorChar == '\\') absolutePath.substringAfter(':') else absolutePath
    }
}
