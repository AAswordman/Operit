package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MathMlPlainTextConverterTest {
    @Test fun convert_rendersFractionsRootsAndSuperscripts() {
        val mathMl =
            """
            <math>
              <semantics>
                <mrow>
                  <mi>x</mi>
                  <mo>=</mo>
                  <mfrac>
                    <mrow>
                      <mo>−</mo><mi>b</mi><mo>±</mo>
                      <msqrt>
                        <mrow>
                          <msup><mi>b</mi><mn>2</mn></msup>
                          <mo>−</mo><mn>4</mn><mi>a</mi><mi>c</mi>
                        </mrow>
                      </msqrt>
                    </mrow>
                    <mrow><mn>2</mn><mi>a</mi></mrow>
                  </mfrac>
                </mrow>
                <annotation encoding="application/x-tex">ignored</annotation>
              </semantics>
            </math>
            """.trimIndent()

        assertEquals(
            "x = (− b ± √(b² − 4ac))/(2a)",
            MathMlPlainTextConverter.convert(mathMl)
        )
    }

    @Test fun convert_rendersSubscriptsAndSummationLimits() {
        val mathMl =
            """
            <math>
              <mrow>
                <msubsup>
                  <mo>∑</mo>
                  <mrow><mi>n</mi><mo>=</mo><mn>0</mn></mrow>
                  <mi>∞</mi>
                </msubsup>
                <msup><mi>i</mi><mn>2</mn></msup>
              </mrow>
            </math>
            """.trimIndent()

        assertEquals(
            "∑_(n = 0)^(∞) i²",
            MathMlPlainTextConverter.convert(mathMl)
        )
    }

    @Test fun convert_rendersMatricesAsRowsAndColumns() {
        val mathMl =
            """
            <math>
              <mtable>
                <mtr><mtd><mi>a</mi></mtd><mtd><mi>b</mi></mtd></mtr>
                <mtr><mtd><mi>c</mi></mtd><mtd><mi>d</mi></mtd></mtr>
              </mtable>
            </math>
            """.trimIndent()

        assertEquals(
            "[a, b; c, d]",
            MathMlPlainTextConverter.convert(mathMl)
        )
    }
}
