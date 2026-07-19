package com.ai.assistance.operit.core.tools.condition

import com.ai.assistance.operit.util.AppLogger
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito

class ConditionEvaluatorParseFailureTest {

    @Test fun loneOperator_returnsFalse() {
        assertInvalidExpression("&&")
    }

    @Test fun brokenArrayLiteral_returnsFalse() {
        assertInvalidExpression("[1,]")
    }

    @Test fun unexpectedClosingParen_returnsFalse() {
        assertInvalidExpression(")")
    }

    private fun assertInvalidExpression(expression: String) {
        Mockito.mockStatic(AppLogger::class.java).use {
            assertFalse(ConditionEvaluator.evaluate(expression, emptyMap()))
        }
    }
}
