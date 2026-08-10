package com.kesepain.kemoapp.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for SafeMarkdown's math pre-processor and Compose-native math AST.
 *
 * These tests intentionally assert semantic invariants (delimiter handling and
 * content preservation) instead of coupling to a concrete MathNode implementation.
 */
class SafeMarkdownMathParserTest {

    @Test
    fun doesNotRewriteDollarSignsInsideFencedCode() {
        val source = """```kotlin
val inline = "${'$'}x${'$'}"
val display = "${'$'}${'$'}a+b${'$'}${'$'}"
```"""

        val prepared = prepareWebCompatibleMarkdown(source)

        assertTrue("fenced code must remain byte-for-byte intact", prepared.contains(source))
        assertFalse("code-fence dollars must not become markdown code spans", prepared.contains("*`x`*"))
        assertFalse("code-fence display math must not become a math fence", prepared.contains("```math"))
    }

    @Test
    fun doesNotRewriteDollarSignsInsideInlineCode() {
        val source = "Use `\$x\$` and `\$\$a+b\$\$` literally."

        val prepared = prepareWebCompatibleMarkdown(source)

        assertTrue(prepared.contains(source))
        assertFalse(prepared.contains("*`x`*"))
        assertFalse(prepared.contains("```math"))
    }

    @Test
    fun escapedDollarDelimitersRemainLiteral() {
        val source = "Price is \\\$5 and the text \\\$x\\\$ is not a formula."

        val prepared = prepareWebCompatibleMarkdown(source)

        assertTrue("escaped dollars should survive as literal text", prepared.contains("\\\$5"))
        assertTrue(prepared.contains("\\\$x\\\$"))
        assertFalse(prepared.contains("*`5"))
        assertFalse(prepared.contains("*`x"))
    }

    @Test
    fun convertsInlineFormulaToReadableTextWithoutBackticks() {
        val prepared = prepareWebCompatibleMarkdown("Euler: \$e^{i\\pi}+1=0\$")

        assertFalse("native inline math should not be rendered as a markdown code span", prepared.contains('`'))
        assertFalse("formula delimiters should be consumed", prepared.contains('$'))
        assertTrue("formula text must be retained", prepared.contains("e"))
        assertTrue("the exponent/pi content must be retained", prepared.contains("pi", ignoreCase = true) || prepared.contains('π'))
        assertTrue(
            "the exponent must remain represented (superscript or caret form)",
            prepared.any { it in "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁽⁾ⁱⁿ" } || prepared.contains('^'),
        )
        assertTrue(prepared.contains("1"))
        assertTrue(prepared.contains("0"))
    }

    @Test
    fun convertsDoubleDollarBlockToMathFence() {
        val prepared = prepareWebCompatibleMarkdown("$$\\frac{a+b}{c}$$")

        assertTrue(prepared.contains("```math"))
        assertTrue(prepared.contains("\\frac") || (prepared.contains("a+b") && prepared.contains("c")))
        assertFalse("display delimiters should not leak into markdown", prepared.contains("$$"))
    }

    @Test
    fun convertsBracketDisplayBlockToMathFence() {
        val prepared = prepareWebCompatibleMarkdown("Identity: \\[x^2 + y^2 = z^2\\]")

        assertTrue(prepared.contains("```math"))
        assertTrue(prepared.contains("x"))
        assertTrue(prepared.contains("y"))
        assertTrue(prepared.contains("z"))
        assertTrue(prepared.contains("^2") || prepared.contains('²'))
        assertFalse(prepared.contains("\\["))
        assertFalse(prepared.contains("\\]"))
    }

    @Test
    fun keepsProseAfterInlineDisplayMathOutsideTheFence() {
        val prepared = prepareWebCompatibleMarkdown("前缀 ${'$'}${'$'}x^2${'$'}${'$'} 后缀")

        assertTrue(prepared.contains("```math\nx^2\n```\n"))
        assertTrue("text following display math must be preserved", prepared.contains("后缀"))
        assertFalse("the trailing prose must not be appended to the closing fence", prepared.contains("``` 后缀"))
    }

    @Test
    fun matrixDisplayPreservesEveryCell() {
        val source = """${'$'}${'$'}
\begin{matrix}
a & b \\
c & d
\end{matrix}
${'$'}${'$'}"""
        val prepared = prepareWebCompatibleMarkdown(source)

        assertTrue(prepared.contains("```math"))
        listOf("a", "b", "c", "d").forEach { cell ->
            assertTrue("matrix cell '$cell' was lost", prepared.contains(cell))
        }
        assertFalse(prepared.contains("$$"))
    }

    @Test
    fun streamingKeepsAnUnfinishedMathFenceInLiveTail() {
        val content = "Intro.\n\n```math\nx^2 + y^2 ="

        val parts = splitStreamingMarkdown(content)

        assertTrue(parts.completedBlocks.contains("Intro."))
        assertTrue(parts.liveTail.startsWith("```math"))
        assertTrue(parts.liveTail.contains("x^2 + y^2 ="))
    }

    @Test
    fun parsesFractionAndRootWithoutDroppingOperands() {
        val fraction = parseMathExpression("\\frac{a+b}{c}").toPlainText()
        val root = parseMathExpression("\\sqrt{x+1}").toPlainText()

        assertContainsAll(fraction, "a", "b", "c")
        assertContainsAll(root, "x", "1")
    }

    @Test
    fun parsesScriptsWithoutDroppingBaseOrScript() {
        val plain = parseMathExpression("x_i^2").toPlainText()

        assertTrue(plain.contains("x"))
        assertTrue(plain.contains("i") || plain.contains("ᵢ"))
        assertTrue(plain.contains("2") || plain.contains("²"))
    }

    @Test
    fun parsesMatrixWithoutDroppingRowsOrCells() {
        val plain = parseMathExpression("""\begin{pmatrix}a&b\\c&d\end{pmatrix}""").toPlainText()

        assertContainsAll(plain, "a", "b", "c", "d")
    }

    private fun assertContainsAll(value: String, vararg fragments: String) {
        fragments.forEach { fragment ->
            assertTrue("'$fragment' missing from plain text '$value'", value.contains(fragment))
        }
    }
}
