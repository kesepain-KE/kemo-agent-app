package com.kesepain.kemoapp.ui.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.stream.ChatEntry
import com.kesepain.kemoapp.data.stream.ChatAttachmentUi
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.data.stream.ToolCallUi
import com.kesepain.kemoapp.data.stream.ToolStatus
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.SafeMarkdown
import com.kesepain.kemoapp.ui.components.JsonRecord
import com.kesepain.kemoapp.ui.components.records
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatScreen(
    entries: List<ChatEntry>,
    conversations: JsonElement?,
    streaming: Boolean,
    chatClosed: Boolean,
    onLoadHistory: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteAllConversations: () -> Unit,
    onSend: (String) -> Unit,
    onClearConversation: () -> Unit,
    onSaveConversation: () -> Unit,
    onCompressConversation: () -> Unit,
    onSaveAndNewConversation: () -> Unit,
    pendingAttachments: List<ChatAttachmentUi>,
    attachmentUploading: Boolean,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val density = LocalDensity.current
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    val bottomThresholdPx = with(density) { 48.dp.roundToPx() }
    val imeBottom = WindowInsets.ime.getBottom(density)
    var chatBottomInWindow by remember { mutableIntStateOf(0) }
    val composerOffsetY = if (imeBottom > 0 && chatBottomInWindow > 0) {
        minOf(0, view.height - imeBottom - chatBottomInWindow)
    } else 0
    var autoFollow by rememberSaveable { mutableStateOf(true) }
    val last = entries.lastOrNull()
    val historyItems = conversations.records("sessions", "items", "conversations")
    var pendingHistory by remember { mutableStateOf<JsonRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<JsonRecord?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    DisposableEffect(window) {
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose { if (previousMode != null) window.setSoftInputMode(previousMode) }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            snapshotFlow {
                listState.isNearContentEnd(bottomThresholdPx)
            }.distinctUntilChanged().collect { nearBottom -> autoFollow = nearBottom }
        }
    }
    LaunchedEffect(listState, bottomThresholdPx) {
        snapshotFlow {
            listState.isNearContentEnd(bottomThresholdPx)
        }.distinctUntilChanged().collect { nearBottom -> if (nearBottom) autoFollow = true }
    }
    LaunchedEffect(entries.size, last?.text?.length, last?.reasoning?.length, last?.tools?.hashCode(), autoFollow, isDragged) {
        if (autoFollow && !isDragged && entries.isNotEmpty()) listState.scrollToLatestContent(entries.lastIndex)
    }
    LaunchedEffect(streaming, autoFollow, isDragged, entries.size) {
        while (streaming && autoFollow && !isDragged && entries.isNotEmpty()) {
            listState.scrollToLatestContent(entries.lastIndex)
            delay(80)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerShape = RectangleShape, windowInsets = WindowInsets(0, 0, 0, 0)) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { drawer.close() } }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                    IconButton(onClick = { pendingDeleteAll = true }, enabled = historyItems.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.delete_all_history)) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (historyItems.isEmpty()) item { IllustratedEmptyState(stringResource(R.string.no_history)) }
                    items(historyItems, key = { it.text("session_id", "id") }) { item ->
                        HistoryConversationCard(item, onDelete = { pendingDelete = item }) { pendingHistory = item }
                    }
                }
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                chatBottomInWindow = coordinates.boundsInWindow().bottom.roundToInt()
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tab_chat), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onLoadHistory(); scope.launch { drawer.open() } }) { Text(stringResource(R.string.history)) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (entries.isEmpty()) item {
                        IllustratedEmptyState(stringResource(R.string.chat_greeting))
                    }
                    items(entries, key = { it.id }) { entry ->
                        if (entry.role == ChatRole.USER) UserBubble(entry) else AssistantBubble(entry, streaming && entry.id == entries.lastOrNull()?.id)
                    }
                }
            }
            ChatComposer(
                modifier = Modifier.align(Alignment.BottomCenter).offset { androidx.compose.ui.unit.IntOffset(0, composerOffsetY) },
                text = text,
                onTextChanged = { text = it },
                streaming = streaming,
                chatClosed = chatClosed,
                onClearConversation = onClearConversation,
                onSaveConversation = onSaveConversation,
                onCompressConversation = onCompressConversation,
                onSaveAndNewConversation = onSaveAndNewConversation,
                attachments = pendingAttachments,
                attachmentUploading = attachmentUploading,
                onAddAttachment = onAddAttachment,
                onRemoveAttachment = onRemoveAttachment,
            ) {
                val value = text.trim()
                if (value.isNotEmpty() || pendingAttachments.isNotEmpty()) { onSend(value); text = "" }
            }
        }
    }

    pendingHistory?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingHistory = null },
            title = { Text(stringResource(R.string.switch_history_title)) },
            text = { Text(stringResource(R.string.switch_history_message, item.text("title", "name").ifBlank { stringResource(R.string.untitled_conversation) })) },
            confirmButton = {
                TextButton(onClick = {
                    val id = item.text("session_id", "id")
                    pendingHistory = null
                    if (id.isNotBlank()) {
                        onSelectConversation(id)
                        scope.launch { drawer.close() }
                    }
                }) { Text(stringResource(R.string.confirm_switch)) }
            },
            dismissButton = { TextButton(onClick = { pendingHistory = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_history_title)) },
            text = { Text(stringResource(R.string.delete_history_message, item.text("title").ifBlank { stringResource(R.string.untitled_conversation) })) },
            confirmButton = {
                TextButton(onClick = {
                    val id = item.text("session_id", "id")
                    pendingDelete = null
                    if (id.isNotBlank()) onDeleteConversation(id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pendingDeleteAll) {
        AlertDialog(
            onDismissRequest = { pendingDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_history_title)) },
            text = { Text(stringResource(R.string.delete_all_history_message)) },
            confirmButton = {
                TextButton(onClick = { pendingDeleteAll = false; onDeleteAllConversations() }) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun LazyListState.isNearContentEnd(thresholdPx: Int): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index != info.totalItemsCount - 1) return false
    val remaining = lastVisible.offset + lastVisible.size + info.afterContentPadding - info.viewportEndOffset
    return remaining <= thresholdPx
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun LazyListState.scrollToLatestContent(lastIndex: Int) {
    if (lastIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) scrollToItem(lastIndex)
    repeat(3) {
        withFrameNanos { }
        if (!canScrollForward) return
        scrollBy(1_000_000f)
    }
}

@Composable
private fun HistoryConversationCard(item: JsonRecord, onDelete: () -> Unit, onClick: () -> Unit) {
    val title = item.text("title", "name", "summary_title").ifBlank { stringResource(R.string.untitled_conversation) }
    val summary = item.text("summary", "preview", "last_message", "description").ifBlank { stringResource(R.string.no_summary) }
    val savedAt = formatHistoryDate(item.text("updated_at", "saved_at", "closed_at", "created_at"))
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_history)) }
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (savedAt.isNotBlank()) Text(savedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun formatHistoryDate(value: String): String {
    if (value.isBlank()) return ""
    val instant = value.toDoubleOrNull()?.let { number ->
        if (number > 10_000_000_000.0) Instant.ofEpochMilli(number.toLong()) else Instant.ofEpochSecond(number.toLong())
    } ?: runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    return instant?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(it) }
        ?: value.take(19).replace('T', ' ')
}

@Composable
private fun UserBubble(entry: ChatEntry) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(modifier = Modifier.fillMaxWidth(.86f), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.text.isNotBlank()) Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.5.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    softWrap = true,
                )
                entry.attachments.forEach { attachment ->
                    Text("📎 ${attachment.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(entry: ChatEntry, streaming: Boolean) {
    var reasoningExpanded by rememberSaveable(entry.id) { mutableStateOf(streaming) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(streaming) { reasoningExpanded = streaming }
    Surface(modifier = Modifier.fillMaxWidth(.92f), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entry.reasoning.isNotBlank()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !streaming) { reasoningExpanded = !reasoningExpanded }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.reasoning_process), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 7.dp).weight(1f))
                            Icon(if (reasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                        }
                        AnimatedVisibility(reasoningExpanded) {
                            Text(
                                entry.reasoning,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            entry.tools.forEach { ToolCallCard(it) }
            if (entry.text.isNotBlank()) {
                if (entry.reasoning.isNotBlank() || entry.tools.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SafeMarkdown(entry.text, streaming = streaming, modifier = Modifier.fillMaxWidth())
            } else if (streaming && entry.reasoning.isBlank() && entry.tools.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.streaming), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!streaming && entry.text.isNotBlank()) {
                entry.usage?.let { usage ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.usage_tokens, usage.totalTokens), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.usage_cache, String.format(Locale.US, "%.1f%%", usage.cacheHitRate * 100.0)),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.usage_elapsed, formatElapsed(usage.elapsedMs)),
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(entry.text)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_reply), modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(milliseconds: Long): String = when {
    milliseconds <= 0 -> "--"
    milliseconds < 1_000 -> "${milliseconds}ms"
    else -> String.format(Locale.US, "%.1fs", milliseconds / 1_000.0)
}

@Composable
private fun ToolCallCard(tool: ToolCallUi) {
    var expanded by rememberSaveable(tool.callId) { mutableStateOf(false) }
    val showResult = tool.status != ToolStatus.RUNNING && tool.resultPreview.isNotBlank()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tool.name.ifBlank { stringResource(R.string.tool_call) },
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when (tool.status) {
                    ToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    ToolStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, stringResource(R.string.tool_success), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    ToolStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.tool_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (tool.arguments.isNotBlank()) {
                        ToolDetailBlock(stringResource(R.string.tool_arguments), tool.arguments)
                    }
                    if (showResult) {
                        ToolDetailBlock(stringResource(R.string.tool_result), tool.resultPreview)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolDetailBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.5.sp, fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChatComposer(
    modifier: Modifier = Modifier,
    text: String,
    onTextChanged: (String) -> Unit,
    streaming: Boolean,
    chatClosed: Boolean,
    onClearConversation: () -> Unit,
    onSaveConversation: () -> Unit,
    onCompressConversation: () -> Unit,
    onSaveAndNewConversation: () -> Unit,
    attachments: List<ChatAttachmentUi>,
    attachmentUploading: Boolean,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onAddAttachment(uri) }
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (attachments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                attachments.forEach { attachment ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveAttachment(attachment.path) },
                        label = { Text(attachment.name, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.remove_attachment), Modifier.size(16.dp)) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(56.dp).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !streaming && !attachmentUploading) {
                    if (attachmentUploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AttachFile, stringResource(R.string.add_attachment))
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (text.isBlank()) Text(stringResource(R.string.chat_hint), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = !streaming) { Icon(Icons.Default.MoreVert, stringResource(R.string.quick_actions)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_conversation)) }, onClick = { menuExpanded = false; onClearConversation() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.save_conversation)) }, onClick = { menuExpanded = false; onSaveConversation() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.compress_context)) }, onClick = { menuExpanded = false; onCompressConversation() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.save_and_new)) }, onClick = { menuExpanded = false; onSaveAndNewConversation() })
                    }
                }
            }
            IconButton(
                onClick = onSend,
                enabled = !streaming && !chatClosed && !attachmentUploading,
                modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
