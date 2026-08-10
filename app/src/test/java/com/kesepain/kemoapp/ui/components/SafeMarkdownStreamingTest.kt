package com.kesepain.kemoapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeMarkdownStreamingTest {
    @Test
    fun keepsOnlyUnfinishedParagraphInLiveTail() {
        val parts = splitStreamingMarkdown("第一段。\n\n第二段正在输出")

        assertEquals(listOf("第一段。"), parts.completedBlocks)
        assertEquals("第二段正在输出", parts.liveTail)
    }

    @Test
    fun doesNotSplitBlankLinesInsideFence() {
        val content = "```kotlin\nval a = 1\n\nval b = 2\n```\n下一段"
        val parts = splitStreamingMarkdown(content)

        assertEquals(listOf("```kotlin\nval a = 1\n\nval b = 2\n```"), parts.completedBlocks)
        assertEquals("下一段", parts.liveTail)
    }

    @Test
    fun leavesUnclosedFenceAsLiveTail() {
        val content = "说明。\n\n```kotlin\nval value ="
        val parts = splitStreamingMarkdown(content)

        assertEquals(listOf("说明。"), parts.completedBlocks)
        assertEquals("```kotlin\nval value =", parts.liveTail)
    }

    @Test
    fun matchesWebSingleTildeAndEmojiRules() {
        val prepared = prepareWebCompatibleMarkdown("1~12，PC13~15；~~已废弃~~ :rocket:")

        assertEquals("1\\~12，PC13\\~15；~~已废弃~~ 🚀", prepared)
    }

    @Test
    fun neutralizesUnsafeLinkSchemes() {
        val prepared = prepareWebCompatibleMarkdown("[safe](https://example.com) [unsafe](javascript:alert(1))")

        assertEquals("[safe](https://example.com) [unsafe]()", prepared)
    }

    @Test
    fun preparesMathForNativeRenderingWithoutDroppingContent() {
        val prepared = prepareWebCompatibleMarkdown("公式 \$E=mc^2\$\n\n\$\$a+b\$\$")

        assertEquals("公式 E = mc²\n\n```math\na+b\n```", prepared)
    }

    @Test
    fun keepsBlankLinesInsideStreamingDisplayMath() {
        val content = "前文。\n\n$$\na+b\n\nc+d\n$$\n后文正在输出"

        val parts = splitStreamingMarkdown(content)

        assertEquals(listOf("前文。", "$$\na+b\n\nc+d\n$$"), parts.completedBlocks)
        assertEquals("后文正在输出", parts.liveTail)
    }
}
