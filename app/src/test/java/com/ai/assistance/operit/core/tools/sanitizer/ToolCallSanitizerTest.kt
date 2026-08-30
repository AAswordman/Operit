package com.ai.assistance.operit.core.tools.sanitizer

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ToolCallSanitizer — covers the exact failure scenario from Operit §10:
 * "Tool-call param ถูก XML tag ปนเปื้อนจนพัง JSON parsing"
 */
class ToolCallSanitizerTest {

    @Test
    fun `sanitize removes tool_call XML tags`() {
        val raw = """<tool_call name="bash">{"command": "ls -la"}</tool_call>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<tool_call"))
        assertFalse(result.contains("</tool_call>"))
        assertTrue(result.contains("ls -la"))
    }

    @Test
    fun `sanitize removes tool_result XML tags`() {
        val raw = """<tool_result>file1.txt\nfile2.txt</tool_result>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<tool_result"))
        assertTrue(result.contains("file1.txt"))
    }

    @Test
    fun `sanitize removes function_call XML tags`() {
        val raw = """<function_call>{"name": "search", "args": {"q": "test"}}</function_call>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<function_call"))
        assertTrue(result.contains("search"))
    }

    @Test
    fun `sanitize removes gemini thought_signature meta tags`() {
        val raw = """answer<meta provider="gemini:thought_signature">abc123</meta>more text"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<meta"))
        assertFalse(result.contains("thought_signature"))
        assertTrue(result.contains("answer"))
        assertTrue(result.contains("more text"))
    }

    @Test
    fun `sanitize removes CDATA wrappers`() {
        val raw = """<![CDATA[{"command": "echo hello"}]]>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<![CDATA["))
        assertFalse(result.contains("]]>"))
        assertTrue(result.contains("echo hello"))
    }

    @Test
    fun `sanitize unescapes XML entities`() {
        val raw = """{"command": "echo &lt;hello&gt; &amp; &quot;world&quot;"}"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertTrue(result.contains("<hello>"))
        assertTrue(result.contains("&"))
        assertTrue(result.contains("\"world\""))
    }

    @Test
    fun `sanitize strips markdown code fences`() {
        val raw = """```json
{"command": "ls -la"}
```"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("```"))
        assertTrue(result.contains("ls -la"))
    }

    @Test
    fun `sanitize strips markdown bold and italic`() {
        val raw = """**bold** and *italic* text"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("**"))
        assertFalse(result.contains("*italic*"))
        assertTrue(result.contains("bold"))
        assertTrue(result.contains("italic"))
    }

    @Test
    fun `sanitize handles mixed XML and markdown contamination`() {
        val raw = """<tool_call name="bash">```json
{"command": "echo <hello>"}
```</tool_call>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<tool_call"))
        assertFalse(result.contains("```"))
        assertTrue(result.contains("echo"))
        assertTrue(result.contains("<hello>"))
    }

    @Test
    fun `sanitize handles empty and blank strings`() {
        assertEquals("", ToolCallSanitizer.sanitize(""))
        assertEquals("   ", ToolCallSanitizer.sanitize("   "))
        assertEquals("test", ToolCallSanitizer.sanitize("test"))
    }

    @Test
    fun `sanitize removes multiple consecutive newlines`() {
        val raw = "line1\n\n\n\n\nline2"
        val result = ToolCallSanitizer.sanitize(raw)
        assertEquals("line1\n\nline2", result)
    }

    @Test
    fun `sanitizeAndValidate returns Success for valid JSON-like content`() {
        val raw = """{"command": "ls"}"""
        val result = ToolCallSanitizer.sanitizeAndValidate(raw)
        assertTrue(result is ToolCallSanitizer.SanitizeResult.Success)
    }

    @Test
    fun `sanitizeAndValidate wraps plain strings in quotes`() {
        val raw = "hello world"
        val result = ToolCallSanitizer.sanitizeAndValidate(raw)
        assertTrue(result is ToolCallSanitizer.SanitizeResult.Success)
        assertEquals("\"hello world\"", (result as ToolCallSanitizer.SanitizeResult.Success).sanitized)
    }

    @Test
    fun `sanitize handles nested XML in JSON string values`() {
        val raw = """{"output": "<result>success</result>"}"""
        val result = ToolCallSanitizer.sanitize(raw)
        // The <result> tag inside the JSON value should be stripped
        assertFalse(result.contains("<result>"))
        assertTrue(result.contains("success"))
    }

    @Test
    fun `sanitize removes arguments XML tags`() {
        val raw = """<arguments>{"key": "value"}</arguments>"""
        val result = ToolCallSanitizer.sanitize(raw)
        assertFalse(result.contains("<arguments"))
        assertTrue(result.contains("key"))
    }
}
