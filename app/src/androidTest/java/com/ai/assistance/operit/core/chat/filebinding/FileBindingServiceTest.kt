package com.ai.assistance.operit.core.chat.filebinding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class FileBindingServiceTest {

    private lateinit var fileBindingService: FileBindingService
    private lateinit var applyLineBasedPatchMethod: Method

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fileBindingService = FileBindingService(context)

        // Use reflection to make the private method accessible for testing
        applyLineBasedPatchMethod = FileBindingService::class.java.getDeclaredMethod(
            "applyLineBasedPatch",
            String::class.java,
            String::class.java
        ).apply {
            isAccessible = true
        }
    }

    private fun invokeApplyLineBasedPatch(originalContent: String, patch: String): Pair<Boolean, String> {
        @Suppress("UNCHECKED_CAST")
        return applyLineBasedPatchMethod.invoke(fileBindingService, originalContent, patch) as Pair<Boolean, String>
    }

    @Test
    fun testComplexMixedOperations_ShouldApplyCorrectly() {
        // Original file content (13 lines)
        val originalContent = """fun main() {
    println("Hello, Original World!")
    // This is a comment
    val x = 10
    val y = 20
    val z = x + y
    println("The sum is " + z)
    // Another comment
    for (i in 1..5) {
        println(i)
    }
    println("End of program")
}"""

        // Patch with 4 operations: DELETE (lines 9-11), REPLACE (lines 4-6), INSERT (after line 1), INSERT (after line 13)
        val patch = """
// [START-DELETE:9-11]
// [END-DELETE]
// [START-REPLACE:4-6]
    val x = 100
    val y = 200
    val sum = x + y
// [END-REPLACE]
// [START-INSERT:after_line=1]
    // This is an inserted line
    val insertedVar = "I'm new here!"
// [END-INSERT]
// [START-INSERT:after_line=13]
// This is the final line.
// [END-INSERT]
""".trim()

        // Expected result after applying all operations
        val expectedContent = """fun main() {
    // This is an inserted line
    val insertedVar = "I'm new here!"
    println("Hello, Original World!")
    // This is a comment
    val x = 100
    val y = 200
    val sum = x + y
    println("The sum is " + z)
    // Another comment
    println("End of program")
}
// This is the final line."""

        val (success, resultContent) = invokeApplyLineBasedPatch(originalContent, patch)

        assertEquals(true, success)
        assertEquals(expectedContent, resultContent)
    }

    @Test
    fun testBoundaryOperations_AtStartAndEndOfFile() {
        val originalContent = """
line 1
line 2
line 3
line 4
line 5
        """.trimIndent()

        // Test operations at the boundaries:
        // 1. Replace the very first line.
        // 2. Insert after the very last line.
        // 3. Delete the last two lines of the *original* content.
        val patch = """
// [START-REPLACE:1-1]
First line replaced
// [END-REPLACE]
// [START-INSERT:after_line=5]
Appended after last line
// [END-INSERT]
// [START-DELETE:4-5]
// [END-DELETE]
        """.trimIndent()

        // Execution is bottom-to-top:
        // 1. DELETE 4-5 -> content is now lines 1, 2, 3. (3 lines total)
        // 2. INSERT after_line=5 -> this line no longer exists. The logic should handle this by clamping to the new end of file.
        //    So it will insert after line 3.
        // 3. REPLACE 1-1 -> Replaces the first line.
        val expectedContent = """
First line replaced
line 2
line 3
Appended after last line
        """.trimIndent()

        val (success, resultContent) = invokeApplyLineBasedPatch(originalContent, patch)
        assertEquals(true, success)
        assertEquals(expectedContent, resultContent.trim())
    }

    @Test
    fun testInsertOnlyOperations_InMiddleOfFile() {
        val originalContent = """
Line 1
Line 2
Line 5
Line 6
        """.trimIndent()

        val patch = """
// [START-INSERT:after_line=2]
Line 3
Line 4
// [END-INSERT]
        """.trimIndent()

        val expectedContent = """
Line 1
Line 2
Line 3
Line 4
Line 5
Line 6
        """.trimIndent()

        val (success, resultContent) = invokeApplyLineBasedPatch(originalContent, patch)
        assertEquals(true, success)
        assertEquals(expectedContent, resultContent.trim())
    }

    @Test
    fun testFuzzyReplace_ShouldNotEarlyStopOnWrongWindowSize() = runBlocking {
        val originalContent = """
<div id=\"catDetail\" class=\"cat-detail\">
    <p>点击上面的小猫图片查看详细信息</p>
</div>
</div>
</div>
</div>
</div>
<script src=\"js/app.js\"></script>
        """.trimIndent()

        val oldContent = """
<div id=\"catDetail\" class=\"cat-detail\">
    <p>点击上面的小猫图片查看详细信息</p>
</div>
</div>
</div>
</div>
</div>
        """.trimIndent()

        val newContent = """
<div id=\"catDetail\" class=\"cat-detail\">
    <p>点击上面的小猫图片查看详细信息</p>
</div>
</div>
        """.trimIndent()

        val (resultContent, _) = fileBindingService.processFileBindingOperations(
            originalContent = originalContent,
            operations =
                listOf(
                    FileBindingService.StructuredEditOperation(
                        action = FileBindingService.StructuredEditAction.REPLACE,
                        oldContent = oldContent,
                        newContent = newContent
                    )
                )
        )

        val expectedContent = """
<div id=\"catDetail\" class=\"cat-detail\">
    <p>点击上面的小猫图片查看详细信息</p>
</div>
</div>
<script src=\"js/app.js\"></script>
        """.trimIndent()

        assertEquals(expectedContent, resultContent.trim())
    }

    @Test
    fun testHighComplexityMixedOperations_ShouldSucceed() {
        val originalContent = """
fun main() {
    println("Hello, World!")

    val x = 10
    val y = 20

    if (x > 5) {
        println("x is greater than 5")
    } else {
        println("x is not greater than 5")
    }

    for (i in 1..5) {
        println("loop iteration ${"$"}i")
    }

    // This is a comment
    val z = x + y
    println("Result: ${"$"}z")
}
        """.trimIndent()

        val patch = """
// [START-REPLACE:2-2]
    println("Hello, Complex World!")
// [END-REPLACE]
// [START-INSERT:after_line=6]
    // A new check
    if (y == 20) {
        println("y is 20!")
    }
// [END-INSERT]
// [START-DELETE:7-11]
// [END-DELETE]
// [START-REPLACE:13-15]
    // New loop
    (1..3).forEach {
        println("New iteration ${"$"}it")
    }
// [END-REPLACE]
// [START-INSERT:after_line=18]
    val a = z * 2
    println("New value: ${"$"}a")
// [END-INSERT]
        """.trimIndent()

        // Manually derived expected content by applying patches from bottom to top.
        val expectedContent = """
fun main() {
    println("Hello, Complex World!")

    val x = 10
    val y = 20

    // A new check
    if (y == 20) {
        println("y is 20!")
    }

    // New loop
    (1..3).forEach {
        println("New iteration ${"$"}it")
    }

    // This is a comment
    val z = x + y
    val a = z * 2
    println("New value: ${"$"}a")
    println("Result: ${"$"}z")
}
        """.trimIndent()

        val (success, resultContent) = invokeApplyLineBasedPatch(originalContent, patch)
        assertEquals(true, success)
        assertEquals(expectedContent, resultContent.trim())
    }

    @Test
    fun testExtremeComplexity_TortureTest() {
        val originalContent = """
// File: Calculator.kt
package com.example.math

class Calculator {
    private var lastResult: Double = 0.0

    /**
     * Adds two integers and returns the sum.
     * Also updates the lastResult.
     */
    fun add(a: Int, b: Int): Int {
        val result = a + b
        lastResult = result.toDouble()
        return result
    }

    /*
     * This is a block comment
     * for the subtract function.
     */
    fun subtract(a: Int, b: Int): Int {
        // Simple subtraction
        val result = a - b
        lastResult = result.toDouble()
        return result
    }

    fun multiply(a: Int, b: Int): Int {
        return a * b
    }

    fun getLastResult(): Double {
        return lastResult
    }
}
        """.trimIndent()

        // This is a torture test. It combines multiple complex operations
        // to test the robustness of the patching logic in a clean, logical way.
        val patch = """
// [START-REPLACE:1-4]
// File: AdvancedCalculator.kt
package com.example.adv_math

class AdvancedCalculator {
// [END-REPLACE]
// [START-INSERT:after_line=13]
        // A new comment line
// [END-INSERT]
// [START-REPLACE:21-26]
    fun subtract(a: Int, b: Int): Int {
        // A more complex subtraction
        val result = a - b - 1
        lastResult = result.toDouble()
        return result
    }
// [END-REPLACE]
// [START-DELETE:28-31]
// [END-DELETE]
// [START-INSERT:after_line=34]
    fun power(base: Double, exp: Double): Double {
        return Math.pow(base, exp)
    }
// [END-INSERT]
        """.trimIndent()

        // Manually derived expected content by applying the clean patch from bottom to top.
        val expectedContent = """
// File: AdvancedCalculator.kt
package com.example.adv_math

class AdvancedCalculator {
    private var lastResult: Double = 0.0

    /**
     * Adds two integers and returns the sum.
     * Also updates the lastResult.
     */
    fun add(a: Int, b: Int): Int {
        val result = a + b
        lastResult = result.toDouble()
        // A new comment line
        return result
    }

    /*
     * This is a block comment
     * for the subtract function.
     */
    fun subtract(a: Int, b: Int): Int {
        // A more complex subtraction
        val result = a - b - 1
        lastResult = result.toDouble()
        return result
    }

    fun getLastResult(): Double {
        return lastResult
    }
    fun power(base: Double, exp: Double): Double {
        return Math.pow(base, exp)
    }
}
        """.trimIndent()

        val (success, resultContent) = invokeApplyLineBasedPatch(originalContent, patch)
        assertEquals(true, success)
        assertEquals(expectedContent, resultContent.trim())
    }
}
