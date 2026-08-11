package com.kesepain.kemoapp.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Composable
fun SafeMarkdown(
    content: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onCopied: () -> Unit = {},
) {
    var renderedContent by remember { mutableStateOf(content) }
    val latestContent by rememberUpdatedState(content)

    LaunchedEffect(streaming) {
        if (streaming) {
            while (isActive) {
                delay(120)
                renderedContent = latestContent
            }
        } else {
            renderedContent = latestContent
        }
    }
    LaunchedEffect(content) {
        if (!streaming) renderedContent = content
    }

    val body = if (compact) {
        MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    }
    val typography = if (compact) {
        markdownTypography(
            h1 = body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            h2 = body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            h3 = body.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            quote = body,
            table = body,
            link = body.copy(color = MaterialTheme.colorScheme.primary),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace),
            code = body.copy(fontFamily = FontFamily.Monospace),
        )
    } else {
        markdownTypography(
            h1 = body.copy(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
            h2 = body.copy(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
            h3 = body.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            quote = body,
            table = body,
            link = body.copy(color = MaterialTheme.colorScheme.primary),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace),
            code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
    val imageTransformer = remember { NetworkMarkdownImageTransformer() }
    val components = remember(onCopied) {
        markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, style ->
                    if (language.equals("math", ignoreCase = true) || language.equals("latex", ignoreCase = true) || language.equals("tex", ignoreCase = true)) {
                        MathFormulaBlock(code, onCopied)
                    } else {
                        MarkdownCodeCard(code, language, style, onCopied)
                    }
                }
            },
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node, model.typography.code) { code, language, style ->
                    if (language.equals("math", ignoreCase = true) || language.equals("latex", ignoreCase = true) || language.equals("tex", ignoreCase = true)) {
                        MathFormulaBlock(code, onCopied)
                    } else {
                        MarkdownCodeCard(code, language, style, onCopied)
                    }
                }
            },
            table = { model ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    MarkdownTable(
                        content = model.content,
                        node = model.node,
                        style = model.typography.table,
                        headerBlock = { tableContent, header, width, style ->
                            MarkdownTableHeader(tableContent, header, width, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Visible)
                        },
                        rowBlock = { tableContent, row, width, style ->
                            MarkdownTableRow(tableContent, row, width, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Visible)
                        },
                    )
                }
            },
        )
    }

    if (streaming) {
        val parts = remember(renderedContent) { splitStreamingMarkdown(renderedContent) }
        Column(modifier) {
            parts.completedBlocks.forEachIndexed { index, block ->
                key(index, block) {
                    StaticMarkdownBlock(block, body, typography, components, imageTransformer, onCopied)
                }
            }
            if (parts.liveTail.isNotEmpty()) {
                Text(parts.liveTail, modifier = Modifier.fillMaxWidth(), style = body, softWrap = true)
            }
        }
    } else {
        StaticMarkdownBlock(renderedContent, body, typography, components, imageTransformer, onCopied, modifier)
    }
}

@Composable
private fun StaticMarkdownBlock(
    content: String,
    body: TextStyle,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents,
    imageTransformer: ImageTransformer,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val prepared = remember(content) { prepareWebCompatibleMarkdown(content) }
    if (hasUnclosedFence(prepared)) {
        Text(content, modifier = modifier, style = body, softWrap = true)
        return
    }
    val markdownState = remember(prepared) { MarkdownStateCache.get(prepared) }
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        typography = typography,
        components = components,
        imageTransformer = imageTransformer,
        loading = { Text(content, modifier = it, style = body, softWrap = true) },
        error = { Text(content, modifier = it, style = body, softWrap = true) },
    )
}

@Composable
private fun MarkdownCodeCard(
    code: String,
    language: String?,
    style: TextStyle,
    onCopied: () -> Unit,
) {
    var copied by remember(code) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language?.takeIf(String::isNotBlank)?.let { if (it.equals("mermaid", true)) "Mermaid" else it } ?: "Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        onCopied()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        modifier = Modifier.size(17.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SelectionContainer {
                Text(
                    code.trimEnd(),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    style = style.copy(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                )
            }
        }
    }
}

private class NetworkMarkdownImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val safeLink = link.trim().takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: return null
        var painter by remember(safeLink) { mutableStateOf<BitmapPainter?>(null) }
        LaunchedEffect(safeLink) {
            painter = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(safeLink).get().build()
                    IMAGE_CLIENT.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body ?: return@use null
                        if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                        val bytes = body.byteStream().use { it.readUpTo(MAX_IMAGE_BYTES + 1) }
                        if (bytes.size > MAX_IMAGE_BYTES) return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let(::BitmapPainter)
                    }
                }.getOrNull()
            }
        }
        return painter?.let {
            ImageData(
                painter = it,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(MaterialTheme.shapes.medium),
                contentDescription = "Markdown image",
            )
        }
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        private val IMAGE_CLIENT = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

/** API 26-compatible bounded read used for Markdown images with unknown content length. */
internal fun InputStream.readUpTo(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE * 2))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = maxBytes
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        when {
            count < 0 -> break
            count == 0 -> {
                val single = read()
                if (single < 0) break
                output.write(single)
                remaining -= 1
            }
            else -> {
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }
    return output.toByteArray()
}

internal data class StreamingMarkdownParts(
    val completedBlocks: List<String>,
    val liveTail: String,
)

internal fun splitStreamingMarkdown(value: String): StreamingMarkdownParts {
    if (value.isEmpty()) return StreamingMarkdownParts(emptyList(), "")
    val completed = mutableListOf<String>()
    var blockStart = 0
    var lineStart = 0
    var insideFence = false
    var fenceMarker = ""
    var mathDelimiter = ""

    while (lineStart < value.length) {
        val nextBreak = value.indexOf('\n', lineStart)
        val lineEnd = if (nextBreak >= 0) nextBreak else value.length
        val line = value.substring(lineStart, lineEnd)
        val trimmed = line.trimStart()
        var closedFence = false

        if (mathDelimiter.isEmpty() && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
            val marker = trimmed.take(3)
            if (!insideFence) {
                insideFence = true
                fenceMarker = marker
            } else if (marker == fenceMarker) {
                insideFence = false
                closedFence = true
            }
        }

        var closedMath = false
        if (!insideFence && !closedFence) {
            val math = scanDisplayMathLine(line, mathDelimiter)
            mathDelimiter = math.delimiter
            closedMath = math.closed
        }

        val hasLineBreak = nextBreak >= 0
        val safeBoundary = !insideFence && mathDelimiter.isEmpty() && hasLineBreak && (line.isBlank() || closedFence || closedMath)
        if (safeBoundary) {
            val endExclusive = lineEnd + 1
            value.substring(blockStart, endExclusive).trim().takeIf(String::isNotEmpty)?.let(completed::add)
            blockStart = endExclusive
            while (blockStart < value.length && value[blockStart] == '\n') blockStart++
        }

        if (!hasLineBreak) break
        lineStart = lineEnd + 1
    }

    return StreamingMarkdownParts(
        completedBlocks = completed,
        liveTail = value.substring(blockStart),
    )
}

private fun hasUnclosedFence(value: String): Boolean = Regex("```|~~~").findAll(value).count() % 2 != 0

internal fun prepareWebCompatibleMarkdown(value: String): String {
    val sanitized = sanitizeUnsafeLinks(value)
    val mathSafe = degradeMathToCode(sanitized)
    val emojiSafe = replaceCommonEmojiAliases(mathSafe)
    val tildeSafe = protectSingleTildes(emojiSafe)
    return applyWebHardBreaks(tildeSafe)
}

internal fun warmMarkdownState(value: String) {
    if (value.isBlank()) return
    val prepared = prepareWebCompatibleMarkdown(value)
    Regex("""```(?:math|latex|tex)\s*\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        .findAll(prepared)
        .forEach { match -> runCatching { parseMathExpression(match.groupValues[1].trim()) } }
    MarkdownStateCache.get(prepared)
}

private fun sanitizeUnsafeLinks(value: String): String = Regex("""\]\(\s*([a-zA-Z][a-zA-Z0-9+.-]*):((?:[^()]|\([^()]*\))*)\)""").replace(value) { match ->
    val scheme = match.groupValues[1].lowercase()
    if (scheme in setOf("http", "https", "mailto")) match.value else "]()"
}

private fun protectSingleTildes(value: String): String {
    var insideFence = false
    var marker = ""
    return value.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val lineMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> ""
        }
        if (lineMarker.isNotEmpty()) {
            if (!insideFence) {
                insideFence = true
                marker = lineMarker
            } else if (lineMarker == marker) {
                insideFence = false
                marker = ""
            }
            line
        } else if (insideFence) line else line.replace(Regex("(?<!~)~(?!~)")) { "\\~" }
    }
}

private fun applyWebHardBreaks(value: String): String {
    val lines = value.split('\n')
    var insideFence = false
    var marker = ""
    return lines.mapIndexed { index, line ->
        val trimmed = line.trimStart()
        val lineMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> ""
        }
        if (lineMarker.isNotEmpty()) {
            if (!insideFence) {
                insideFence = true
                marker = lineMarker
            } else if (lineMarker == marker) {
                insideFence = false
                marker = ""
            }
            line
        } else {
            val next = lines.getOrNull(index + 1)
            if (!insideFence && line.isNotBlank() && !next.isNullOrBlank() && isParagraphLine(line) && isParagraphLine(next)) "$line  " else line
        }
    }.joinToString("\n")
}

private fun isParagraphLine(value: String): Boolean {
    val line = value.trimStart()
    return line.isNotEmpty() &&
        !line.startsWith("#") && !line.startsWith(">") && !line.startsWith("|") &&
        !line.startsWith("```") && !line.startsWith("~~~") &&
        !line.matches(Regex("(?:[-+*]|\\d+[.)])\\s+.*"))
}

private fun degradeMathToCode(value: String): String {
    val output = StringBuilder(value.length + 32)
    var cursor = 0
    var fenceMarker = ""
    var inlineCodeTicks = 0

    fun escapedAt(position: Int): Boolean {
        var slashCount = 0
        var index = position - 1
        while (index >= 0 && value[index] == '\\') {
            slashCount++
            index--
        }
        return slashCount % 2 == 1
    }

    fun fenceAt(position: Int): String {
        val lineStart = position == 0 || value[position - 1] == '\n'
        if (!lineStart) return ""
        var index = position
        while (index < value.length && value[index] in charArrayOf(' ', '\t')) index++
        return when {
            value.startsWith("```", index) -> "```"
            value.startsWith("~~~", index) -> "~~~"
            else -> ""
        }
    }

    while (cursor < value.length) {
        val lineFence = if (inlineCodeTicks == 0) fenceAt(cursor) else ""
        if (lineFence.isNotEmpty()) {
            val markerStart = cursor + value.substring(cursor).takeWhile { it == ' ' || it == '\t' }.length
            val lineEnd = value.indexOf('\n', markerStart).let { if (it < 0) value.length else it }
            output.append(value, cursor, lineEnd)
            if (lineEnd < value.length) output.append('\n')
            fenceMarker = if (fenceMarker.isEmpty()) lineFence else if (fenceMarker == lineFence) "" else fenceMarker
            cursor = (lineEnd + 1).coerceAtMost(value.length)
            continue
        }
        if (fenceMarker.isNotEmpty()) {
            val lineEnd = value.indexOf('\n', cursor).let { if (it < 0) value.length else it }
            output.append(value, cursor, lineEnd)
            if (lineEnd < value.length) output.append('\n')
            cursor = (lineEnd + 1).coerceAtMost(value.length)
            continue
        }

        if (value[cursor] == '`' && !escapedAt(cursor)) {
            var count = 1
            while (cursor + count < value.length && value[cursor + count] == '`') count++
            inlineCodeTicks = if (inlineCodeTicks == 0) count else if (inlineCodeTicks == count) 0 else inlineCodeTicks
            output.append(value, cursor, cursor + count)
            cursor += count
            continue
        }
        if (inlineCodeTicks > 0) {
            output.append(value[cursor++])
            continue
        }

        val blockEndToken = when {
            value.startsWith("$$", cursor) && !escapedAt(cursor) -> "$$"
            value.startsWith("\\[", cursor) && !escapedAt(cursor) -> "\\]"
            else -> ""
        }
        if (blockEndToken.isNotEmpty()) {
            val openerLength = 2
            val contentStart = cursor + openerLength
            val close = value.indexOf(blockEndToken, contentStart)
            if (close >= 0) {
                val formula = value.substring(contentStart, close).trim()
                appendMathFence(output, formula)
                cursor = close + blockEndToken.length
                continue
            }
        }

        val inlineEndToken = when {
            value.startsWith("\\(", cursor) && !escapedAt(cursor) -> "\\)"
            value[cursor] == '$' && !escapedAt(cursor) && !value.startsWith("$$", cursor) -> "$"
            else -> ""
        }
        if (inlineEndToken.isNotEmpty()) {
            val openerLength = if (inlineEndToken == "$") 1 else 2
            val contentStart = cursor + openerLength
            var close = value.indexOf(inlineEndToken, contentStart)
            while (close >= 0 && (escapedAt(close) || (inlineEndToken == "$" && close > contentStart && value[close - 1].isWhitespace()))) {
                close = value.indexOf(inlineEndToken, close + inlineEndToken.length)
            }
            if (close >= 0 && '\n' !in value.substring(contentStart, close)) {
                val rawFormula = value.substring(contentStart, close)
                val formula = rawFormula.trim()
                val validDollarSpacing = inlineEndToken != "$" || (
                    rawFormula.isNotEmpty() && !rawFormula.first().isWhitespace() && !rawFormula.last().isWhitespace()
                    )
                if (formula.isNotEmpty() && validDollarSpacing && (inlineEndToken != "$" || looksLikeMathFormula(formula))) {
                    output.append(mathToReadableInline(formula))
                    cursor = close + inlineEndToken.length
                    continue
                }
            }
        }

        output.append(value[cursor++])
    }
    val rendered = output.toString()
    // A display-math block needs a separator before following prose, but an
    // input that ends exactly at the closing delimiter should not gain a
    // synthetic trailing line break. This keeps streaming snapshots stable
    // and avoids a one-line jump when the final token arrives.
    return if (rendered.endsWith('\n') && !value.endsWith('\n')) rendered.dropLast(1) else rendered
}

private fun appendMathFence(output: StringBuilder, formula: String) {
    if (output.isNotEmpty()) {
        if (output.endsWith("\n\n")) Unit
        else if (output.endsWith("\n")) output.append('\n')
        else output.append("\n\n")
    }
    // Keep the closing fence on a line of its own. Display math can appear
    // inline with surrounding prose (`prefix $$x$$ suffix`), and without the
    // trailing line break Markdown treats the suffix as part of the fence
    // line, leaving the block unclosed or swallowing the following content.
    output.append("```math\n").append(formula).append("\n```\n")
}

private fun looksLikeMathFormula(value: String): Boolean =
    value.any { it in "^_\\{}=+*/<>" || it.isDigit() } || value.contains(Regex("\\b[a-zA-Z]\b"))

private fun mathToReadableInline(value: String): String = runCatching {
    parseMathExpression(value).toPlainText()
}.getOrDefault(value).ifBlank { value }.let(::spaceInlineMathOperators)

private fun spaceInlineMathOperators(value: String): String = value
    .replace(Regex("\\s*([=+×÷≤≥≈≠])\\s*"), " $1 ")
    .replace(Regex("(?<=[\\p{L}\\p{N})²³⁴⁵⁶⁷⁸⁹])\\s*-\\s*(?=[\\p{L}\\p{N}(√])"), " - ")
    .replace(Regex(" {2,}"), " ")
    .trim()

private fun replaceCommonEmojiAliases(value: String): String {
    if (':' !in value) return value
    var insideFence = false
    var marker = ""
    return value.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val lineMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> ""
        }
        if (lineMarker.isNotEmpty()) {
            if (!insideFence) {
                insideFence = true
                marker = lineMarker
            } else if (lineMarker == marker) {
                insideFence = false
                marker = ""
            }
            line
        } else if (insideFence) line else EMOJI_ALIASES.entries.fold(line) { result, (alias, emoji) -> result.replace(":$alias:", emoji) }
    }
}

internal data class DisplayMathLineState(val delimiter: String, val closed: Boolean)

internal fun scanDisplayMathLine(line: String, activeDelimiter: String = ""): DisplayMathLineState {
    var delimiter = activeDelimiter
    var closed = false
    var cursor = 0
    var inlineTicks = 0

    fun escaped(position: Int): Boolean {
        var slashCount = 0
        var index = position - 1
        while (index >= 0 && line[index] == '\\') {
            slashCount++
            index--
        }
        return slashCount % 2 == 1
    }

    while (cursor < line.length) {
        if (delimiter.isEmpty() && line[cursor] == '`' && !escaped(cursor)) {
            var count = 1
            while (cursor + count < line.length && line[cursor + count] == '`') count++
            inlineTicks = if (inlineTicks == 0) count else if (inlineTicks == count) 0 else inlineTicks
            cursor += count
            continue
        }
        if (inlineTicks > 0) {
            cursor++
            continue
        }
        if (delimiter.isNotEmpty()) {
            if (line.startsWith(delimiter, cursor) && !escaped(cursor)) {
                delimiter = ""
                closed = true
                cursor += 2
            } else cursor++
            continue
        }
        when {
            line.startsWith("$$", cursor) && !escaped(cursor) -> {
                delimiter = "$$"
                cursor += 2
            }
            line.startsWith("\\[", cursor) && !escaped(cursor) -> {
                delimiter = "\\]"
                cursor += 2
            }
            else -> cursor++
        }
    }
    return DisplayMathLineState(delimiter, closed)
}

private val EMOJI_ALIASES = mapOf(
    "rocket" to "🚀", "white_check_mark" to "✅", "x" to "❌", "warning" to "⚠️",
    "bulb" to "💡", "sparkles" to "✨", "fire" to "🔥", "tada" to "🎉",
    "heart" to "❤️", "+1" to "👍", "-1" to "👎", "eyes" to "👀",
    "memo" to "📝", "lock" to "🔒", "unlock" to "🔓", "link" to "🔗",
)
