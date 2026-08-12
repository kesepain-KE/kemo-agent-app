package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerField(
    label: String,
    selected: String,
    models: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    pickerTitle: String? = null,
    allowCustomValue: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                selected.ifBlank { placeholder ?: stringResource(R.string.choose_model) },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
    }

    if (showPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var query by remember { mutableStateOf("") }
        val available = remember(models) { models.filter(String::isNotBlank).distinct() }
        val filtered = remember(available, query) {
            if (query.isBlank()) available else available.filter { it.contains(query.trim(), ignoreCase = true) }
        }
        val listState = rememberLazyListState()
        val progress by remember(filtered.size) {
            derivedStateOf {
                if (filtered.size <= 1) 0f
                else (listState.firstVisibleItemIndex.toFloat() / (filtered.size - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 680.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pickerTitle ?: stringResource(R.string.model_picker_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_search_hint)) },
                    singleLine = true,
                )
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val typed = query.trim()
                        if (allowCustomValue && typed.isNotBlank() && available.none { it.equals(typed, ignoreCase = true) }) {
                            item(key = "typed:$typed") {
                                Card(
                                    onClick = { onSelected(typed); showPicker = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                ) {
                                    Text(stringResource(R.string.use_typed_model, typed), Modifier.padding(16.dp))
                                }
                            }
                        }
                        items(filtered, key = { it }) { model ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onSelected(model); showPicker = false },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (model == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = model == selected, onClick = { onSelected(model); showPicker = false })
                                    Text(model, Modifier.padding(start = 6.dp).weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (filtered.isEmpty() && query.isBlank()) {
                            item { Text(stringResource(R.string.no_models), Modifier.padding(16.dp)) }
                        } else if (filtered.isEmpty()) {
                            item { Text(stringResource(R.string.no_matching_models), Modifier.padding(16.dp)) }
                        }
                    }
                    if (filtered.size > 1) {
                        val progressDescription = stringResource(R.string.model_scroll_progress, (progress * 100).toInt())
                        val density = LocalDensity.current
                        val thumbHeight = 56.dp
                        val travelPx = with(density) { (maxHeight - thumbHeight).coerceAtLeast(0.dp).toPx() }
                        val offsetDp = with(density) { (travelPx * progress).toDp() }
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .width(4.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .offset(y = offsetDp)
                                .width(4.dp).height(thumbHeight)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics {
                                    contentDescription = progressDescription
                                },
                        )
                    }
                }
            }
        }
    }
}
