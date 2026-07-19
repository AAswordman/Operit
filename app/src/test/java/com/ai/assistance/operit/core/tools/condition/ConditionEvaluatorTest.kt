package com.ai.assistance.operit.core.tools.condition

import com.ai.assistance.operit.util.AppLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ConditionEvaluatorTest {

    @Test fun emptyExpression_defaultsToTrue() {
        assertTrue(ConditionEvaluator.evaluate("", emptyMap()))
    }

    @Test fun whitespaceExpression_defaultsToTrue() {
        assertTrue(ConditionEvaluator.evaluate("   ", emptyMap()))
    }

    @Test fun booleanTrueLiteral_isTrue() {
        assertTrue(ConditionEvaluator.evaluate("true", emptyMap()))
    }

    @Test fun booleanFalseLiteral_isFalse() {
        assertFalse(ConditionEvaluator.evaluate("false", emptyMap()))
    }

    @Test fun identifierReadsBooleanCapability() {
        assertTrue(ConditionEvaluator.evaluate("enabled", mapOf("enabled" to true)))
    }

    @Test fun missingIdentifier_isFalse() {
        assertFalse(ConditionEvaluator.evaluate("missing", emptyMap()))
    }

    @Test fun unaryNot_invertsTruthyValue() {
        assertTrue(ConditionEvaluator.evaluate("!disabled", mapOf("disabled" to false)))
    }

    @Test fun andOperator_requiresBothSides() {
        assertTrue(ConditionEvaluator.evaluate("a && b", mapOf("a" to true, "b" to true)))
    }

    @Test fun andOperator_shortCircuitsFalse() {
        assertFalse(ConditionEvaluator.evaluate("a && b", mapOf("a" to false, "b" to true)))
    }

    @Test fun orOperator_acceptsOneTruthySide() {
        assertTrue(ConditionEvaluator.evaluate("a || b", mapOf("a" to false, "b" to true)))
    }

    @Test fun precedence_andBeforeOr() {
        assertTrue(ConditionEvaluator.evaluate("true || false && false", emptyMap()))
    }

    @Test fun parentheses_overridePrecedence() {
        assertFalse(ConditionEvaluator.evaluate("(true || false) && false", emptyMap()))
    }

    @Test fun equality_comparesNumbers() {
        assertTrue(ConditionEvaluator.evaluate("score == 3", mapOf("score" to 3)))
    }

    @Test fun equality_comparesStrings() {
        assertTrue(ConditionEvaluator.evaluate("name == 'operit'", mapOf("name" to "operit")))
    }

    @Test fun equality_comparesBooleans() {
        assertTrue(ConditionEvaluator.evaluate("flag == true", mapOf("flag" to true)))
    }

    @Test fun equality_comparesNulls() {
        assertTrue(ConditionEvaluator.evaluate("value == null", mapOf("value" to null)))
    }

    @Test fun inequality_detectsDifferentValues() {
        assertTrue(ConditionEvaluator.evaluate("name != 'other'", mapOf("name" to "operit")))
    }

    @Test fun greaterThan_comparesNumbers() {
        assertTrue(ConditionEvaluator.evaluate("score > 2", mapOf("score" to 3)))
    }

    @Test fun greaterThanOrEqual_comparesNumbers() {
        assertTrue(ConditionEvaluator.evaluate("score >= 3", mapOf("score" to 3)))
    }

    @Test fun lessThan_comparesNumbers() {
        assertTrue(ConditionEvaluator.evaluate("score < 4", mapOf("score" to 3)))
    }

    @Test fun lessThanOrEqual_comparesNumbers() {
        assertTrue(ConditionEvaluator.evaluate("score <= 3", mapOf("score" to 3)))
    }

    @Test fun inOperator_matchesArrayLiteral() {
        assertTrue(ConditionEvaluator.evaluate("'a' in ['a', 'b']", emptyMap()))
    }

    @Test fun inOperator_matchesCapabilityArray() {
        assertTrue(ConditionEvaluator.evaluate("'chat' in features", mapOf("features" to listOf("chat", "tool"))))
    }

    @Test fun inOperator_returnsFalseWhenMissing() {
        assertFalse(ConditionEvaluator.evaluate("'voice' in features", mapOf("features" to listOf("chat", "tool"))))
    }

    @Test fun doubleQuotedStringLiteral_isSupported() {
        assertTrue(ConditionEvaluator.evaluate("name == \"operit\"", mapOf("name" to "operit")))
    }

    @Test fun singleQuotedStringEscape_isSupported() {
        assertTrue(ConditionEvaluator.evaluate("name == 'it\\'s'", mapOf("name" to "it's")))
    }

    @Test fun dottedIdentifier_isReadAsSingleCapabilityKey() {
        assertTrue(ConditionEvaluator.evaluate("device.rooted", mapOf("device.rooted" to true)))
    }

    @Test fun numericBooleanComparison_usesNumberSemantics() {
        assertTrue(ConditionEvaluator.evaluate("true >= false", emptyMap()))
    }

    @Test fun stringComparison_isLexicographical() {
        assertTrue(ConditionEvaluator.evaluate("'b' > 'a'", emptyMap()))
    }

    @Test fun decimalNumberLiteral_isSupported() {
        assertTrue(ConditionEvaluator.evaluate("score == 3.5", mapOf("score" to 3.5)))
    }

    @Test fun leadingDotNumberLiteral_isSupported() {
        assertTrue(ConditionEvaluator.evaluate("score == .5", mapOf("score" to 0.5)))
    }

    @Test fun arrayLiteral_isTruthyWhenNotEmpty() {
        assertTrue(ConditionEvaluator.evaluate("[1]", emptyMap()))
    }

    @Test fun emptyArrayLiteral_isFalsy() {
        assertFalse(ConditionEvaluator.evaluate("[]", emptyMap()))
    }

    @Test fun emptyStringLiteral_isFalsy() {
        assertFalse(ConditionEvaluator.evaluate("''", emptyMap()))
    }

    @Test fun nonEmptyStringLiteral_isTruthy() {
        assertTrue(ConditionEvaluator.evaluate("'ok'", emptyMap()))
    }

    @Test fun malformedCharacter_returnsFalse() {
        assertInvalidExpression("@")
    }

    @Test fun unterminatedString_returnsFalse() {
        assertInvalidExpression("'abc")
    }

    @Test fun unbalancedParenthesis_returnsFalse() {
        assertInvalidExpression("(true")
    }

    private fun assertInvalidExpression(expression: String) {
        Mockito.mockStatic(AppLogger::class.java).use {
            assertFalse(ConditionEvaluator.evaluate(expression, emptyMap()))
        }
    }
}
