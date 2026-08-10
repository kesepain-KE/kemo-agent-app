package com.kesepain.kemoapp.ui.screens.chat

import com.kesepain.kemoapp.data.stream.ChatEntry
import com.kesepain.kemoapp.data.stream.ChatMediaUi
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.data.stream.GuidanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRenderItemsTest {
    @Test
    fun splitsParagraphHeadingTableAndFenceIntoLazyBlocks() {
        val content = """
            第一段。

            ## 标题

            | A | B |
            | - | - |
            | 1 | 2 |

            ```kotlin
            val value = 1

            println(value)
            ```
        """.trimIndent()

        val blocks = splitStaticMarkdownBlocks(content)

        assertEquals(4, blocks.size)
        assertEquals("第一段。", blocks[0])
        assertEquals("## 标题", blocks[1])
        assertTrue(blocks[2].startsWith("| A | B |"))
        assertTrue(blocks[3].startsWith("```kotlin"))
        assertTrue(blocks[3].contains("\n\n"))
    }

    @Test
    fun completedAssistantReplyBecomesMultipleStableRenderItems() {
        val entry = ChatEntry(
            id = "assistant-1",
            role = ChatRole.ASSISTANT,
            text = "第一段。\n\n第二段。",
        )

        val items = buildChatRenderItems(listOf(entry), streaming = false)

        assertEquals(3, items.size)
        assertTrue(items[0] is ChatRenderItem.AssistantMarkdown)
        assertTrue(items[1] is ChatRenderItem.AssistantMarkdown)
        assertTrue(items[2] is ChatRenderItem.AssistantFooter)
        assertTrue((items[0] as ChatRenderItem.AssistantMarkdown).first)
        assertFalse((items[0] as ChatRenderItem.AssistantMarkdown).last)
        assertTrue((items[2] as ChatRenderItem.AssistantFooter).last)
    }

    @Test
    fun streamingTailStaysLightweightWhileCompletedBlocksAreStatic() {
        val entry = ChatEntry(
            id = "assistant-live",
            role = ChatRole.ASSISTANT,
            text = "完成段落。\n\n仍在生成",
        )

        val items = buildChatRenderItems(listOf(entry), streaming = true)
            .filterIsInstance<ChatRenderItem.AssistantMarkdown>()

        assertEquals(2, items.size)
        assertFalse(items[0].liveTail)
        assertTrue(items[1].liveTail)
    }

    @Test
    fun keepsBlankLinesInsideDisplayMathAsOneLazyBlock() {
        val content = "前文。\n\n$$\na+b\n\nc+d\n$$\n\n后文。"

        val blocks = splitStaticMarkdownBlocks(content)

        assertEquals(3, blocks.size)
        assertEquals("前文。", blocks[0])
        assertEquals("$$\na+b\n\nc+d\n$$", blocks[1])
        assertEquals("后文。", blocks[2])
    }

    @Test
    fun guidanceUsesItsOwnLazyItemWithoutCountingAsUserContent() {
        val entry = ChatEntry(
            id = "guidance-1",
            role = ChatRole.GUIDANCE,
            text = "先检查网络",
            guidanceStatus = GuidanceStatus.ACCEPTED,
        )

        val items = buildChatRenderItems(listOf(entry), streaming = true)

        assertEquals(1, items.size)
        assertTrue(items.single() is ChatRenderItem.Guidance)
    }

    @Test
    fun mediaOnlyAssistantReplyRendersAStableMediaItem() {
        val entry = ChatEntry(
            id = "assistant-media",
            role = ChatRole.ASSISTANT,
            media = listOf(ChatMediaUi("asset-1", "image", "answer.png", "answer.png", mimeType = "image/png", size = 128)),
        )

        val items = buildChatRenderItems(listOf(entry), streaming = false)

        assertEquals(1, items.size)
        assertTrue(items.single() is ChatRenderItem.AssistantMedia)
    }
}
