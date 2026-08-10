package com.kesepain.kemoapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SafeMarkdown(content: String, streaming: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
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

    val safeContent = remember(renderedContent) { degradeMathToCode(renderedContent) }
    val body = if (compact) {
        MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.5.sp)
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
            inlineCode = body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            code = body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        )
    } else {
        markdownTypography(
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            link = body.copy(color = MaterialTheme.colorScheme.primary),
            inlineCode = body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            code = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        )
    }
    if (hasUnclosedFence(safeContent)) {
        Text(renderedContent, modifier = modifier, style = body, softWrap = true)
    } else {
        Markdown(
            content = safeContent,
            modifier = modifier,
            typography = typography,
            error = { Text(renderedContent, modifier = it, style = body, softWrap = true) },
        )
    }
}

private fun hasUnclosedFence(value: String): Boolean = Regex("```|~~~").findAll(value).count() % 2 != 0

private fun degradeMathToCode(value: String): String {
    val blocks = Regex("""\$\$([\s\S]*?)\$\$""").replace(value) { match ->
        "\n```text\n${match.groupValues[1].trim()}\n```\n"
    }
    return Regex("""(?<!\\)\$([^\n$]+?)(?<!\\)\$""").replace(blocks) { match ->
        "*`${match.groupValues[1]}`*"
    }
}
