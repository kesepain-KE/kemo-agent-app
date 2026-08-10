package com.kesepain.kemoapp.ui.screens.chat

import com.kesepain.kemoapp.data.stream.ChatEntry
import com.kesepain.kemoapp.data.stream.ChatMediaUi
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.ui.components.splitStreamingMarkdown
import com.kesepain.kemoapp.ui.components.scanDisplayMathLine

internal sealed interface ChatRenderItem {
    val key: String
    val contentType: String

    data class User(val entry: ChatEntry) : ChatRenderItem {
        override val key = "user:${entry.id}"
        override val contentType = "user"
    }

    data class Guidance(val entry: ChatEntry) : ChatRenderItem {
        override val key = "guidance:${entry.id}"
        override val contentType = "guidance"
    }

    data class AssistantMeta(
        val entry: ChatEntry,
        val first: Boolean,
        val last: Boolean,
        val dividerAfter: Boolean,
    ) : ChatRenderItem {
        override val key = "assistant:${entry.id}:meta"
        override val contentType = "assistant-meta"
    }

    data class AssistantMarkdown(
        val entryId: String,
        val fullText: String,
        val block: String,
        val blockIndex: Int,
        val liveTail: Boolean,
        val first: Boolean,
        val last: Boolean,
    ) : ChatRenderItem {
        override val key = "assistant:$entryId:block:$blockIndex:${if (liveTail) "live" else "done"}"
        override val contentType = if (liveTail) "assistant-live-text" else "assistant-markdown"
    }

    data class AssistantPlaceholder(
        val entryId: String,
        val first: Boolean,
        val last: Boolean,
    ) : ChatRenderItem {
        override val key = "assistant:$entryId:placeholder"
        override val contentType = "assistant-placeholder"
    }

    data class AssistantMedia(
        val entryId: String,
        val media: ChatMediaUi,
        val first: Boolean,
        val last: Boolean,
    ) : ChatRenderItem {
        override val key = "assistant:$entryId:media:${media.assetId}:${media.path}"
        override val contentType = "assistant-media"
    }

    data class AssistantFooter(
        val entry: ChatEntry,
        val first: Boolean,
        val last: Boolean,
    ) : ChatRenderItem {
        override val key = "assistant:${entry.id}:footer"
        override val contentType = "assistant-footer"
    }
}

internal fun buildChatRenderItems(entries: List<ChatEntry>, streaming: Boolean): List<ChatRenderItem> = buildList {
    entries.forEachIndexed { entryIndex, entry ->
        when (entry.role) {
            ChatRole.USER -> {
                add(ChatRenderItem.User(entry))
                return@forEachIndexed
            }
            ChatRole.GUIDANCE -> {
                add(ChatRenderItem.Guidance(entry))
                return@forEachIndexed
            }
            ChatRole.ASSISTANT -> Unit
        }

        val isLiveEntry = streaming && entryIndex == entries.lastIndex
        val draftSegments = mutableListOf<(Boolean, Boolean) -> ChatRenderItem>()
        val hasMeta = entry.reasoning.isNotBlank() || entry.tools.isNotEmpty()
        val markdownBlocks = if (entry.text.isBlank()) {
            emptyList()
        } else if (isLiveEntry) {
            val split = splitStreamingMarkdown(entry.text)
            buildList {
                split.completedBlocks.forEachIndexed { index, block -> add(Triple(block, index, false)) }
                if (split.liveTail.isNotBlank()) add(Triple(split.liveTail, split.completedBlocks.size, true))
            }
        } else {
            splitStaticMarkdownBlocks(entry.text).mapIndexed { index, block -> Triple(block, index, false) }
        }

        if (hasMeta) {
            draftSegments += { first, last ->
                ChatRenderItem.AssistantMeta(
                    entry = entry,
                    first = first,
                    last = last,
                    dividerAfter = markdownBlocks.isNotEmpty(),
                )
            }
        }
        markdownBlocks.forEach { (block, index, liveTail) ->
            draftSegments += { first, last ->
                ChatRenderItem.AssistantMarkdown(
                    entryId = entry.id,
                    fullText = entry.text,
                    block = block,
                    blockIndex = index,
                    liveTail = liveTail,
                    first = first,
                    last = last,
                )
            }
        }
        entry.media.forEach { media ->
            draftSegments += { first, last -> ChatRenderItem.AssistantMedia(entry.id, media, first, last) }
        }
        if (isLiveEntry && !hasMeta && markdownBlocks.isEmpty() && entry.media.isEmpty()) {
            draftSegments += { first, last -> ChatRenderItem.AssistantPlaceholder(entry.id, first, last) }
        }
        if (!isLiveEntry && entry.text.isNotBlank()) {
            draftSegments += { first, last -> ChatRenderItem.AssistantFooter(entry, first, last) }
        }
        if (draftSegments.isEmpty()) {
            draftSegments += { first, last -> ChatRenderItem.AssistantPlaceholder(entry.id, first, last) }
        }

        draftSegments.forEachIndexed { index, factory ->
            add(factory(index == 0, index == draftSegments.lastIndex))
        }
    }
}

internal fun splitStaticMarkdownBlocks(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val blocks = mutableListOf<String>()
    val current = StringBuilder()
    var insideFence = false
    var fenceMarker = ""
    var mathDelimiter = ""

    fun flush() {
        current.toString().trim().takeIf(String::isNotEmpty)?.let(blocks::add)
        current.clear()
    }

    value.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> ""
        }
        val standalone = !insideFence && mathDelimiter.isEmpty() && (
            trimmed.matches(Regex("#{1,6}\\s+.*")) ||
                trimmed.matches(Regex("([-*_])(?:\\s*\\1){2,}\\s*"))
            )

        if (standalone && current.isNotBlank()) flush()
        if (!insideFence && mathDelimiter.isEmpty() && line.isBlank()) {
            flush()
            return@forEach
        }

        if (current.isNotEmpty()) current.append('\n')
        current.append(line)

        if (mathDelimiter.isEmpty() && fence.isNotEmpty()) {
            if (!insideFence) {
                insideFence = true
                fenceMarker = fence
            } else if (fence == fenceMarker) {
                insideFence = false
                fenceMarker = ""
                flush()
            }
        } else if (!insideFence) {
            if (standalone) {
                flush()
            } else {
                val math = scanDisplayMathLine(line, mathDelimiter)
                mathDelimiter = math.delimiter
                if (math.closed && mathDelimiter.isEmpty()) flush()
            }
        }
    }
    flush()
    return blocks
}
