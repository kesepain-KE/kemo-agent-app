package com.kesepain.kemoapp.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.FilePreviewUi
import com.kesepain.kemoapp.data.stream.ChatEntry
import com.kesepain.kemoapp.data.stream.ChatAttachmentUi
import com.kesepain.kemoapp.data.stream.ChatMediaUi
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.data.stream.GuidanceStatus
import com.kesepain.kemoapp.data.stream.ToolCallUi
import com.kesepain.kemoapp.data.stream.ToolStatus
import com.kesepain.kemoapp.ui.components.DetailBottomSheet
import com.kesepain.kemoapp.ui.components.BackgroundPerformanceController
import com.kesepain.kemoapp.ui.components.IllustratedEmptyState
import com.kesepain.kemoapp.ui.components.SafeMarkdown
import com.kesepain.kemoapp.ui.components.ChatMediaCard
import com.kesepain.kemoapp.ui.components.warmMarkdownState
import com.kesepain.kemoapp.ui.components.JsonRecord
import com.kesepain.kemoapp.ui.components.records
import com.kesepain.kemoapp.ui.screens.files.FilePreviewDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatScreen(
    entries: List<ChatEntry>,
    conversations: JsonElement?,
    status: JsonElement?,
    streaming: Boolean,
    chatClosed: Boolean,
    reasoningEffort: String,
    reasoningEffortOptions: List<String>,
    reasoningEffortAvailable: Boolean,
    reasoningEffortBusy: Boolean,
    onReasoningEffortChange: (String) -> Unit,
    onLoadHistory: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteAllConversations: () -> Unit,
    onSend: (String, String) -> Unit,
    onClearConversation: () -> Unit,
    onRetryLastResponse: (String) -> Unit,
    onCompressConversation: () -> Unit,
    onSaveAndNewConversation: () -> Unit,
    onCopied: () -> Unit,
    pendingAttachments: List<ChatAttachmentUi>,
    attachmentUploading: Boolean,
    guidanceSubmitting: Boolean,
    chatStopping: Boolean,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onStop: () -> Unit,
    onLoadMedia: (String, String) -> Unit,
    onDownloadMedia: (String) -> Unit,
    onLoadAttachment: (String) -> Unit,
    filePreview: FilePreviewUi?,
    filePreviewDownloading: Boolean,
    onPreviewAttachment: (String, String, String) -> Unit,
    onCloseFilePreview: () -> Unit,
    onDownloadAttachment: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val density = LocalDensity.current
    val view = LocalView.current
    val bottomThresholdPx = with(density) { 48.dp.roundToPx() }
    val imeBottom = WindowInsets.ime.getBottom(density)
    var chatBottomInWindow by remember { mutableIntStateOf(0) }
    val composerOffsetY = if (imeBottom > 0 && chatBottomInWindow > 0) {
        minOf(0, view.height - imeBottom - chatBottomInWindow)
    } else 0
    var autoFollow by rememberSaveable { mutableStateOf(true) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val jumpButtonGapPx = with(density) { 10.dp.roundToPx() }
    val showJumpToBottom by remember {
        derivedStateOf { !autoFollow && listState.canScrollForward }
    }
    val historyItems = conversations.records("sessions", "items", "conversations")
    val renderItems = remember(entries, streaming) { buildChatRenderItems(entries, streaming) }
    var pendingHistory by remember { mutableStateOf<JsonRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<JsonRecord?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }
    var showContextDetails by rememberSaveable { mutableStateOf(false) }
    val statusRoot = status as? JsonObject
    val runtimeContext = statusRoot.obj("runtime").obj("context")
    val overviewContext = statusRoot.obj("overview").obj("context")
    val localTokenEstimate = entries.asReversed().firstNotNullOfOrNull { it.usage?.totalTokens } ?: 0
    val tokenSnapshot = contextTokenSnapshot(
        status = status,
    )
    val contextCapacity = tokenSnapshot.capacityTokens
    val contextPercent = tokenSnapshot.percent
    val reportedRounds = runtimeContext.long("rounds") ?: overviewContext.long("rounds") ?: 0L
    val localRounds = entries.count { it.role == ChatRole.USER && (it.text.isNotBlank() || it.attachments.isNotEmpty()) }.toLong()
    val rounds = maxOf(reportedRounds, localRounds)
    val roundLimit = runtimeContext.long("round_limit") ?: overviewContext.long("round_limit") ?: 0L

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
    LaunchedEffect(renderItems.size, autoFollow, isDragged) {
        if (autoFollow && !isDragged && entries.isNotEmpty()) {
            withFrameNanos { }
            listState.followLatestContent(renderItems.size)
        }
    }
    LaunchedEffect(renderItems.size) {
        withContext(Dispatchers.Default) {
            renderItems.filterIsInstance<ChatRenderItem.AssistantMarkdown>()
                .asSequence()
                .filterNot { it.liveTail }
                .forEach { warmMarkdownState(it.block) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(BackgroundPerformanceController::setChatScrolling)
    }
    DisposableEffect(listState) {
        onDispose { BackgroundPerformanceController.setChatScrolling(false) }
    }
    LaunchedEffect(streaming, autoFollow, isDragged, renderItems.size) {
        while (streaming && autoFollow && !isDragged && entries.isNotEmpty()) {
            withFrameNanos { }
            listState.followLatestContent(renderItems.size)
            delay(120)
        }
        if (autoFollow && !isDragged && entries.isNotEmpty()) {
            withFrameNanos { }
            listState.followLatestContent(renderItems.size)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            // Give the sheet a stable width. If it is allowed to wrap its (sometimes empty)
            // history content, wide-screen layouts can measure a very narrow drawer and leave
            // its edge visible even while DrawerValue is Closed.
            ModalDrawerSheet(
                modifier = Modifier.width(360.dp),
                drawerShape = RectangleShape,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f).height(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showContextDetails = true }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text(
                            stringResource(R.string.chat_runtime_summary, rounds, roundLimit, contextPercent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ContextProgressBar(contextPercent)
                    }
                    ReasoningEffortPicker(
                        value = reasoningEffort,
                        options = reasoningEffortOptions,
                        enabled = !streaming && reasoningEffortAvailable && !reasoningEffortBusy,
                        loading = reasoningEffortBusy,
                        onValueChange = onReasoningEffortChange,
                    )
                    IconButton(onClick = { onLoadHistory(); scope.launch { drawer.open() } }) {
                        Icon(Icons.Default.History, stringResource(R.string.history))
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    if (entries.isEmpty()) item {
                        IllustratedEmptyState(stringResource(R.string.chat_greeting))
                    }
                    items(
                        items = renderItems,
                        key = { it.key },
                        contentType = { it.contentType },
                    ) { item ->
                        when (item) {
                            is ChatRenderItem.User -> UserBubble(
                                item.entry,
                                onCopied,
                                onLoadAttachment,
                                onPreviewAttachment,
                                onDownloadAttachment,
                            )
                            is ChatRenderItem.Guidance -> GuidanceBubble(
                                item.entry,
                                onCopied,
                                onLoadAttachment,
                                onPreviewAttachment,
                                onDownloadAttachment,
                            )
                            is ChatRenderItem.AssistantMeta -> AssistantMetaSegment(item, streaming && item.entry.id == entries.lastOrNull()?.id, onCopied)
                            is ChatRenderItem.AssistantMarkdown -> AssistantMarkdownSegment(item, onCopied)
                            is ChatRenderItem.AssistantMedia -> AssistantMediaSegment(item, onLoadMedia, onDownloadMedia)
                            is ChatRenderItem.AssistantPlaceholder -> AssistantPlaceholderSegment(item)
                            is ChatRenderItem.AssistantFooter -> AssistantFooterSegment(item, onCopied)
                        }
                    }
                    item(key = "chat-end-anchor") { Spacer(Modifier.height(1.dp)) }
                }
            }
            if (showJumpToBottom) {
                SmallFloatingActionButton(
                    onClick = {
                        autoFollow = true
                        scope.launch { listState.animateScrollToItem(renderItems.size) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).offset {
                        androidx.compose.ui.unit.IntOffset(0, composerOffsetY - composerHeightPx - jumpButtonGapPx)
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.ArrowDownward, stringResource(R.string.jump_to_latest))
                }
            }
            ChatComposer(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { composerHeightPx = it.height }
                    .offset { androidx.compose.ui.unit.IntOffset(0, composerOffsetY) },
                text = text,
                onTextChanged = { text = it },
                streaming = streaming,
                chatClosed = chatClosed,
                onClearConversation = onClearConversation,
                onRetryLastResponse = { onRetryLastResponse(reasoningEffort) },
                onCompressConversation = onCompressConversation,
                onSaveAndNewConversation = onSaveAndNewConversation,
                attachments = pendingAttachments,
                attachmentUploading = attachmentUploading,
                guidanceSubmitting = guidanceSubmitting,
                chatStopping = chatStopping,
                onAddAttachment = onAddAttachment,
                onRemoveAttachment = onRemoveAttachment,
                onStop = onStop,
            ) {
                val value = text.trim()
                if (value.isNotEmpty() || pendingAttachments.isNotEmpty()) { onSend(value, reasoningEffort); text = "" }
            }
        }
    }

    if (showContextDetails) {
        DetailBottomSheet(
            title = stringResource(R.string.context_window_details),
            onDismissRequest = { showContextDetails = false },
        ) {
            ContextDetailsContent(
                root = statusRoot,
                fallbackRounds = rounds,
                fallbackRoundLimit = roundLimit,
                fallbackContextPercent = contextPercent,
                fallbackContextCapacity = contextCapacity,
                fallbackContextUsed = localTokenEstimate.toLong(),
                tokenSnapshot = tokenSnapshot,
            )
        }
    }
    filePreview?.let { preview ->
        FilePreviewDialog(
            preview = preview,
            downloading = filePreviewDownloading,
            onDismiss = onCloseFilePreview,
            onDownload = { onDownloadAttachment(preview.path) },
        )
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

private suspend fun LazyListState.followLatestContent(endAnchorIndex: Int) {
    if (endAnchorIndex < 0) return
    scrollToItem(endAnchorIndex)
}

@Composable
private fun HistoryConversationCard(item: JsonRecord, onDelete: () -> Unit, onClick: () -> Unit) {
    val title = item.text("title", "name", "summary_title").ifBlank { stringResource(R.string.untitled_conversation) }
    val summary = item.text("summary", "preview", "last_message", "description").ifBlank { stringResource(R.string.no_summary) }
    val savedAt = formatHistoryDate(item.text("updated_at", "saved_at", "closed_at", "created_at"))
    val rounds = item.number("rounds", "session_total_rounds", "total_rounds")?.toLong() ?: 0L
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_rounds, rounds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (savedAt.isNotBlank()) Text(savedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun ContextProgressBar(percent: Double, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier.fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                .fillMaxHeight().background(MaterialTheme.colorScheme.primary),
        )
    }
}

private data class ContextMetricUi(val label: String, val value: String, val unit: String = "")

@Composable
private fun ContextDetailsContent(
    root: JsonObject?,
    fallbackRounds: Long,
    fallbackRoundLimit: Long,
    fallbackContextPercent: Double,
    fallbackContextCapacity: Long,
    fallbackContextUsed: Long,
    tokenSnapshot: ContextTokenSnapshotUi,
) {
    val overview = root.obj("overview")
    val runtime = root.obj("runtime")
    val window = overview.obj("context_window")
    val conversation = window.obj("conversation")
    val tasks = window.obj("tasks")
    val capabilities = window.obj("capabilities")
    val knowledge = window.obj("knowledge")
    val messages = window.obj("messages")
    val integrations = window.obj("integrations")
    val injectionPolicy = window.obj("injection_policy")
    val runtimeContext = runtime.obj("context")

    val sessionId = runtime.text("session_id").ifBlank { overview.text("session_id") }
    val systemTokens = tokenSnapshot.systemPromptTokens
    val toolTokens = tokenSnapshot.toolSchemaTokens
    val conversationTokens = tokenSnapshot.conversationTokens + tokenSnapshot.summaryTokens
    // An explicit context_snapshot is authoritative even when it reports that
    // the selected server-side session is unavailable. Do not combine a stale
    // local usage estimate with zero-valued API breakdown fields: that was the
    // source of the misleading "72K total / 0 components" panel.
    val totalTokens = if (tokenSnapshot.available) tokenSnapshot.totalTokens else 0L
    val capacityTokens = tokenSnapshot.capacityTokens.takeIf { it > 0L } ?: fallbackContextCapacity
    val percent = if (tokenSnapshot.available) tokenSnapshot.percent else 0.0
    val unavailableValue = stringResource(R.string.context_data_unavailable)

    val foregroundRounds = conversation.long("foreground_rounds")
        ?: runtimeContext.long("rounds")
        ?: fallbackRounds
    val archivedRounds = conversation.long("archived_rounds") ?: 0L
    val sessionRounds = conversation.long("session_total_rounds")
        ?: overview.obj("context").long("session_total_rounds")
        ?: fallbackRounds
    val toolCalls = conversation.long("session_tool_calls")
        ?: conversation.long("total_tool_calls")
        ?: 0L

    if (sessionId.isNotBlank()) {
        Text(
            sessionId,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_injection_policy),
        subtitle = stringResource(R.string.context_read_only_snapshot),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ContextInjectionBubble(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.context_perception_injection),
                policy = contextInjectionPolicyLabel(injectionPolicy.text("perception")),
            )
            ContextInjectionBubble(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.context_expand_injection),
                policy = contextInjectionPolicyLabel(injectionPolicy.text("expand")),
            )
        }
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_token_overview),
        subtitle = stringResource(R.string.context_runtime_estimate),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_system_prompt), if (tokenSnapshot.available) formatCompactCount(systemTokens) else unavailableValue, if (tokenSnapshot.available) "Token" else ""),
                ContextMetricUi(stringResource(R.string.context_tool_definitions), if (tokenSnapshot.available) formatCompactCount(toolTokens) else unavailableValue, if (tokenSnapshot.available) "Token" else ""),
                ContextMetricUi(stringResource(R.string.context_conversation_summary), if (tokenSnapshot.available) formatCompactCount(conversationTokens) else unavailableValue, if (tokenSnapshot.available) "Token" else ""),
                ContextMetricUi(stringResource(R.string.context_current_total), if (tokenSnapshot.available) formatCompactCount(totalTokens) else unavailableValue, if (tokenSnapshot.available) "Token" else ""),
                ContextMetricUi(stringResource(R.string.context_capacity_limit), formatCompactCount(capacityTokens), "Token"),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.context_capacity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (tokenSnapshot.available) stringResource(R.string.status_percent_value, percent) else unavailableValue,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        ContextProgressBar(percent)
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_conversation_stats),
        subtitle = stringResource(R.string.context_foreground_archive),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_foreground_rounds), foregroundRounds.toString(), stringResource(R.string.context_unit_rounds)),
                ContextMetricUi(stringResource(R.string.context_archived_rounds), archivedRounds.toString(), stringResource(R.string.context_unit_rounds)),
                ContextMetricUi(stringResource(R.string.context_session_total_rounds), sessionRounds.toString(), stringResource(R.string.context_unit_rounds)),
                ContextMetricUi(stringResource(R.string.context_tool_calls), toolCalls.toString(), stringResource(R.string.context_unit_times)),
                ContextMetricUi(stringResource(R.string.status_rounds), "$fallbackRounds / $fallbackRoundLimit"),
            ),
        )
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_tasks_schedule),
        subtitle = stringResource(R.string.context_current_schedule),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_active_plans), (tasks.long("active_plans") ?: 0L).toString(), stringResource(R.string.context_unit_items)),
                ContextMetricUi(stringResource(R.string.context_waiting_crons), (tasks.long("waiting_crons") ?: 0L).toString(), stringResource(R.string.context_unit_items)),
            ),
        )
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_tools_agents),
        subtitle = stringResource(R.string.context_capability_state),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_enabled_tools), (capabilities.long("tools_enabled") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
                ContextMetricUi(stringResource(R.string.context_disabled_tools), (capabilities.long("tools_disabled") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
                ContextMetricUi(stringResource(R.string.context_subagents), (capabilities.long("agents_enabled") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
            ),
        )
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_knowledge_state),
        subtitle = stringResource(R.string.context_current_injection_scope),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_enabled_knowledge), (knowledge.long("enabled") ?: 0L).toString(), stringResource(R.string.context_unit_items)),
                ContextMetricUi(stringResource(R.string.context_disabled_knowledge), (knowledge.long("disabled") ?: 0L).toString(), stringResource(R.string.context_unit_items)),
            ),
        )
    }

    ContextDetailPanel(
        title = stringResource(R.string.context_external_integrations),
        subtitle = stringResource(R.string.context_connection_counts),
    ) {
        ContextMetricGrid(
            listOf(
                ContextMetricUi(stringResource(R.string.context_external_messages), (messages.long("connected") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
                ContextMetricUi(stringResource(R.string.context_connected_expands), (integrations.long("expands") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
                ContextMetricUi(stringResource(R.string.context_connected_senses), (integrations.long("senses") ?: 0L).toString(), stringResource(R.string.context_unit_count)),
            ),
        )
    }
}

@Composable
private fun ContextDetailPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun ContextInjectionBubble(modifier: Modifier, label: String, policy: String) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(policy, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.context_fixed_snapshot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContextMetricGrid(items: List<ContextMetricUi>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(item.value, style = MaterialTheme.typography.titleMedium)
                                if (item.unit.isNotBlank()) {
                                    Text(
                                        item.unit,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun contextInjectionPolicyLabel(value: String): String = when (value.lowercase()) {
    "round" -> stringResource(R.string.context_injection_per_round)
    "request", "realtime" -> stringResource(R.string.context_injection_per_request)
    "off", "disabled", "none" -> stringResource(R.string.context_injection_disabled)
    else -> value.ifBlank { "—" }
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

@Composable
private fun UserBubble(
    entry: ChatEntry,
    onCopied: () -> Unit,
    onLoadAttachment: (String) -> Unit,
    onPreviewAttachment: (String, String, String) -> Unit,
    onDownloadAttachment: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copyText = entry.text.ifBlank { entry.attachments.joinToString("\n") { it.name } }
    val copyMessage = {
        if (copyText.isNotBlank()) {
            clipboard.setText(AnnotatedString(copyText))
            onCopied()
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(.86f).combinedClickable(onClick = {}, onLongClick = copyMessage),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.text.isNotBlank()) Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.5.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    softWrap = true,
                )
                entry.attachments.forEach { attachment ->
                    ChatAttachmentContent(
                        attachment = attachment,
                        foregroundColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onLoadAttachment = onLoadAttachment,
                        onPreviewAttachment = onPreviewAttachment,
                        onDownloadAttachment = onDownloadAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidanceBubble(
    entry: ChatEntry,
    onCopied: () -> Unit,
    onLoadAttachment: (String) -> Unit,
    onPreviewAttachment: (String, String, String) -> Unit,
    onDownloadAttachment: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copyText = entry.text.ifBlank { entry.attachments.joinToString("\n") { it.name } }
    val copyMessage = {
        if (copyText.isNotBlank()) {
            clipboard.setText(AnnotatedString(copyText))
            onCopied()
        }
    }
    val statusLabel = when (entry.guidanceStatus) {
        GuidanceStatus.SUBMITTING -> stringResource(R.string.guidance_submitting)
        GuidanceStatus.ACCEPTED -> stringResource(R.string.guidance_accepted)
        GuidanceStatus.QUEUED -> stringResource(R.string.guidance_queued)
        GuidanceStatus.COMPLETED -> stringResource(R.string.guidance_completed)
        GuidanceStatus.ERROR -> stringResource(R.string.guidance_failed)
        null -> ""
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(.86f).combinedClickable(onClick = {}, onLongClick = copyMessage),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.guidance_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.guidanceStatus == GuidanceStatus.SUBMITTING) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.8.dp)
                        Spacer(Modifier.size(6.dp))
                    }
                    if (statusLabel.isNotBlank()) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (entry.text.isNotBlank()) {
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                entry.attachments.forEach { attachment ->
                    ChatAttachmentContent(
                        attachment = attachment,
                        foregroundColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onLoadAttachment = onLoadAttachment,
                        onPreviewAttachment = onPreviewAttachment,
                        onDownloadAttachment = onDownloadAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatAttachmentContent(
    attachment: ChatAttachmentUi,
    foregroundColor: Color,
    onLoadAttachment: (String) -> Unit,
    onPreviewAttachment: (String, String, String) -> Unit,
    onDownloadAttachment: (String) -> Unit,
) {
    val mediaKind = remember(attachment.mediaKind, attachment.mimeType, attachment.name) {
        attachmentMediaKind(attachment)
    }
    if (mediaKind != null) {
        ChatMediaCard(
            media = ChatMediaUi(
                assetId = "upload:${attachment.path}",
                type = mediaKind,
                name = attachment.name,
                path = attachment.path,
                mimeType = attachment.mimeType,
                size = attachment.size,
                localUri = attachment.localUri,
                loading = attachment.loading,
                error = attachment.error,
            ),
            onLoad = { onLoadAttachment(attachment.path) },
            onDownload = { onDownloadAttachment(attachment.path) },
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                onPreviewAttachment("upload", attachment.path, attachment.name)
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, foregroundColor.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                    Text(
                        attachment.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.attachment_tap_to_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.attachment_tap_to_preview),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun attachmentMediaKind(attachment: ChatAttachmentUi): String? {
    val declaredKind = attachment.mediaKind.trim().lowercase(Locale.ROOT)
    if (declaredKind in CHAT_ATTACHMENT_MEDIA_KINDS) return declaredKind

    val mimeType = attachment.mimeType.trim().lowercase(Locale.ROOT)
    when {
        mimeType.startsWith("image/") -> return "image"
        mimeType.startsWith("audio/") -> return "audio"
        mimeType.startsWith("video/") -> return "video"
    }

    return when (attachment.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        in CHAT_ATTACHMENT_IMAGE_EXTENSIONS -> "image"
        in CHAT_ATTACHMENT_AUDIO_EXTENSIONS -> "audio"
        in CHAT_ATTACHMENT_VIDEO_EXTENSIONS -> "video"
        else -> null
    }
}

private val CHAT_ATTACHMENT_MEDIA_KINDS = setOf("image", "audio", "video")
private val CHAT_ATTACHMENT_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
private val CHAT_ATTACHMENT_AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus", "amr")
private val CHAT_ATTACHMENT_VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v", "avi", "3gp")

@Composable
private fun AssistantMetaSegment(item: ChatRenderItem.AssistantMeta, streaming: Boolean, onCopied: () -> Unit) {
    val entry = item.entry
    var reasoningExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copyReply = remember(entry.text, onCopied) {
        {
            if (entry.text.isNotBlank()) {
                clipboard.setText(AnnotatedString(entry.text))
                onCopied()
            }
        }
    }
    val maxReasoningContentHeight =
        (LocalConfiguration.current.screenHeightDp.dp * 0.5f - 52.dp).coerceAtLeast(96.dp)
    AssistantSegmentSurface(item.first, item.last, copyReply) {
        Column(
            Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                top = if (item.first) 14.dp else 2.dp,
                bottom = if (item.last) 14.dp else 2.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (entry.reasoning.isNotBlank()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().animateContentSize()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { reasoningExpanded = !reasoningExpanded }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.reasoning_process), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 7.dp).weight(1f))
                            if (streaming) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (reasoningExpanded) 90f else 0f },
                            )
                        }
                        if (reasoningExpanded) {
                            Text(
                                entry.reasoning,
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(max = maxReasoningContentHeight)
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            entry.tools.forEach { ToolCallCard(it) }
            if (item.dividerAfter) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AssistantMarkdownSegment(item: ChatRenderItem.AssistantMarkdown, onCopied: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val copyReply = remember(item.fullText, onCopied) {
        {
            if (item.fullText.isNotBlank()) {
                clipboard.setText(AnnotatedString(item.fullText))
                onCopied()
            }
        }
    }
    AssistantSegmentSurface(item.first, item.last, copyReply) {
        Box(
            Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                top = if (item.first) 14.dp else 2.dp,
                bottom = if (item.last) 14.dp else 2.dp,
            ),
        ) {
            if (item.liveTail) {
                Text(
                    item.block,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    softWrap = true,
                )
            } else {
                SafeMarkdown(item.block, streaming = false, modifier = Modifier.fillMaxWidth(), onCopied = onCopied)
            }
        }
    }
}

@Composable
private fun AssistantMediaSegment(
    item: ChatRenderItem.AssistantMedia,
    onLoadMedia: (String, String) -> Unit,
    onDownloadMedia: (String) -> Unit,
) {
    AssistantSegmentSurface(item.first, item.last, onLongCopy = {}) {
        Column(
            Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                top = if (item.first) 14.dp else 2.dp,
                bottom = if (item.last) 14.dp else 6.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.generated_media), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChatMediaCard(
                media = item.media,
                onLoad = { onLoadMedia(item.media.assetId, item.media.path) },
                onDownload = { onDownloadMedia(item.media.path) },
            )
        }
    }
}

@Composable
private fun AssistantPlaceholderSegment(item: ChatRenderItem.AssistantPlaceholder) {
    AssistantSegmentSurface(item.first, item.last, onLongCopy = {}) {
        Row(
            Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                top = if (item.first) 14.dp else 2.dp,
                bottom = if (item.last) 14.dp else 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.streaming), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AssistantFooterSegment(item: ChatRenderItem.AssistantFooter, onCopied: () -> Unit) {
    val entry = item.entry
    var copied by rememberSaveable(entry.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copyReply = {
        if (entry.text.isNotBlank()) {
            clipboard.setText(AnnotatedString(entry.text))
            copied = true
            onCopied()
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    AssistantSegmentSurface(item.first, item.last, copyReply) {
        Column(
            Modifier.padding(
                start = 14.dp,
                end = 10.dp,
                top = if (item.first) 14.dp else 2.dp,
                bottom = 8.dp,
            ),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                entry.usage?.let { usage ->
                    Text(stringResource(R.string.usage_tokens, usage.totalTokens), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.usage_cache, String.format(Locale.US, "%.1f%%", usage.cacheHitRate * 100.0)),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.usage_elapsed, formatElapsed(usage.elapsedMs)),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = copyReply, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        stringResource(if (copied) R.string.feedback_copied else R.string.copy_reply),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantSegmentSurface(
    first: Boolean,
    last: Boolean,
    onLongCopy: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 28.dp else 0.dp,
        topEnd = if (first) 28.dp else 0.dp,
        bottomStart = if (last) 28.dp else 0.dp,
        bottomEnd = if (last) 28.dp else 0.dp,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(.92f)
            .padding(bottom = if (last) 10.dp else 0.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongCopy),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) { content() }
}

private fun formatElapsed(milliseconds: Long): String = when {
    milliseconds <= 0 -> "--"
    milliseconds < 1_000 -> "${milliseconds}ms"
    else -> String.format(Locale.US, "%.1fs", milliseconds / 1_000.0)
}

@Composable
private fun ToolCallCard(tool: ToolCallUi) {
    var expanded by rememberSaveable(tool.callId) { mutableStateOf(false) }
    var nowMs by remember(tool.callId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tool.status, tool.startedAtMs) {
        while (tool.status == ToolStatus.RUNNING) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedMs = if (tool.status == ToolStatus.RUNNING) {
        (nowMs - tool.startedAtMs).coerceAtLeast(0)
    } else tool.elapsedMs.coerceAtLeast(0)
    val showResult = tool.status != ToolStatus.RUNNING && tool.resultPreview.isNotBlank()
    val maxToolContentHeight =
        (LocalConfiguration.current.screenHeightDp.dp * 0.25f - 52.dp).coerceAtLeast(72.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tool.name.ifBlank { stringResource(R.string.tool_call) },
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (elapsedMs > 0 || tool.status == ToolStatus.RUNNING) {
                    Text(
                        stringResource(R.string.tool_elapsed, formatElapsed(elapsedMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                when (tool.status) {
                    ToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    ToolStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, stringResource(R.string.tool_success), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    ToolStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.tool_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    modifier = Modifier.size(18.dp).graphicsLayer {
                        rotationZ = if (expanded) 90f else 0f
                    },
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = maxToolContentHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (tool.arguments.isNotBlank()) ToolDetailBlock(stringResource(R.string.tool_arguments), tool.arguments)
                    if (showResult) ToolDetailBlock(stringResource(R.string.tool_result), tool.resultPreview)
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
private fun ReasoningEffortPicker(
    value: String,
    options: List<String>,
    enabled: Boolean,
    loading: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        InputChip(
            selected = true,
            onClick = { expanded = true },
            enabled = enabled,
            label = {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(value.ifBlank { stringResource(R.string.reasoning_effort_unavailable) })
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(effort) },
                    onClick = { expanded = false; onValueChange(effort) },
                )
            }
        }
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
    onRetryLastResponse: () -> Unit,
    onCompressConversation: () -> Unit,
    onSaveAndNewConversation: () -> Unit,
    attachments: List<ChatAttachmentUi>,
    attachmentUploading: Boolean,
    guidanceSubmitting: Boolean,
    chatStopping: Boolean,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val inputScrollState = rememberScrollState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onAddAttachment(uri)
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAddAttachment(uri)
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val value = pendingCameraUri
        pendingCameraUri = ""
        if (saved && value.isNotBlank()) onAddAttachment(Uri.parse(value))
    }
    val hasInput = text.isNotBlank() || attachments.isNotEmpty()
    LaunchedEffect(text) {
        inputScrollState.scrollTo(inputScrollState.maxValue)
    }
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (streaming) {
            Text(
                stringResource(R.string.guidance_running_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (attachments.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 164.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                attachments.chunked(2).forEach { rowAttachments ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowAttachments.forEach { attachment ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveAttachment(attachment.path) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        attachment.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        stringResource(R.string.remove_attachment),
                                        Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                        if (rowAttachments.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f)
                    .heightIn(min = 56.dp, max = 280.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { attachmentMenuExpanded = true }, enabled = !attachmentUploading) {
                        if (attachmentUploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AttachFile, stringResource(R.string.add_attachment))
                    }
                    DropdownMenu(expanded = attachmentMenuExpanded, onDismissRequest = { attachmentMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.take_photo)) },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                val uri = createCameraImageUri(context)
                                pendingCameraUri = uri.toString()
                                cameraPicker.launch(uri)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_gallery)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_file)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f)
                        .heightIn(min = 56.dp, max = 280.dp)
                        .verticalScroll(inputScrollState),
                    singleLine = false,
                    minLines = 1,
                    maxLines = Int.MAX_VALUE,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (text.isBlank()) Text(stringResource(R.string.chat_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                if (streaming) {
                    IconButton(
                        onClick = onSend,
                        enabled = hasInput && !attachmentUploading && !guidanceSubmitting && !chatStopping,
                    ) {
                        if (guidanceSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send_guidance), tint = MaterialTheme.colorScheme.tertiary)
                    }
                } else {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.quick_actions)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear_conversation)) }, onClick = { menuExpanded = false; onClearConversation() })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.regenerate_response)) },
                                onClick = { menuExpanded = false; onRetryLastResponse() },
                                enabled = !chatClosed,
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.compress_context)) }, onClick = { menuExpanded = false; onCompressConversation() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_and_new)) }, onClick = { menuExpanded = false; onSaveAndNewConversation() })
                        }
                    }
                }
            }
            IconButton(
                onClick = if (streaming) onStop else onSend,
                enabled = if (streaming) !chatStopping else !chatClosed && !attachmentUploading && hasInput,
                modifier = Modifier.padding(start = 8.dp).background(
                    if (streaming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.medium,
                ),
            ) {
                if (streaming && chatStopping) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    Icon(
                        if (streaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        stringResource(if (streaming) R.string.stop_generation else R.string.send),
                        tint = if (streaming) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun createCameraImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "chat-captures").apply { mkdirs() }
    val file = File(directory, "camera-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
private fun JsonObject?.text(key: String): String = (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject?.decimal(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonObject?.long(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.longOrNull
