package com.kesepain.kemoapp.ui.screens.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.BusySwitch
import com.kesepain.kemoapp.ui.components.records
import com.kesepain.kemoapp.ui.components.stringItems
import kotlinx.serialization.json.JsonElement

@Composable
fun AgentConfigScreen(
    value: JsonElement?,
    models: JsonElement?,
    onRefresh: () -> Unit,
    onModelsRefresh: () -> Unit,
    busy: Boolean = false,
    onSave: (UserConfigDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(UserConfigDraft.from(value)) }
    val savedProviderType = UserConfigDraft.from(value).providerType
    val modelRecords = models.records("models", "items", "data")
    val modelNames = (
        modelRecords.map { it.text("id", "name", "model") } +
            models.stringItems("models", "items", "data") +
            draft.model
        ).filter(String::isNotBlank).distinct()
    LaunchedEffect(value) {
        val loaded = UserConfigDraft.from(value)
        draft = loaded
        if (loaded.providerType.equals("kemo", ignoreCase = true)) onModelsRefresh()
    }
    LaunchedEffect(Unit) { onRefresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.agent_configuration), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.agent_configuration_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onSave(draft) }) { Text(stringResource(R.string.save)) }
            }
        }
        item {
            ConfigCard(stringResource(R.string.provider_configuration)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("chat", "kemo").forEach { type ->
                        FilterChip(selected = draft.providerType == type, onClick = { draft = draft.copy(providerType = type) }, label = { Text(type) })
                    }
                }
                if (draft.providerType.equals("kemo", ignoreCase = true)) {
                    KemoModelSelector(
                        selected = draft.model,
                        models = modelNames,
                        discoveryEnabled = savedProviderType.equals("kemo", ignoreCase = true),
                        onRefresh = onModelsRefresh,
                    ) { draft = draft.copy(model = it) }
                } else {
                    ConfigTextField(stringResource(R.string.model), draft.model) { draft = draft.copy(model = it) }
                }
                ConfigTextField(stringResource(R.string.base_url), draft.baseUrl) { draft = draft.copy(baseUrl = it) }
                OutlinedTextField(
                    draft.apiKey,
                    { draft = draft.copy(apiKey = it) },
                    label = { Text(stringResource(R.string.api_key)) },
                    placeholder = { Text(stringResource(R.string.api_key_unchanged)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ConfigTextField(stringResource(R.string.reasoning_effort), draft.reasoningEffort) { draft = draft.copy(reasoningEffort = it) }
                ConfigToggleRow(stringResource(R.string.streaming_output), draft.stream, busy = busy) { draft = draft.copy(stream = it) }
                ConfigToggleRow(stringResource(R.string.support_image_input), draft.imageInput, busy = busy) { draft = draft.copy(imageInput = it) }
                ConfigToggleRow(stringResource(R.string.support_audio_input), draft.audioInput, busy = busy) { draft = draft.copy(audioInput = it) }
                ConfigToggleRow(stringResource(R.string.support_video_input), draft.videoInput, busy = busy) { draft = draft.copy(videoInput = it) }
                ConfigToggleRow(stringResource(R.string.support_file_input), draft.fileInput, busy = busy) { draft = draft.copy(fileInput = it) }
            }
        }
        item {
            ConfigCard(stringResource(R.string.subagent_models)) {
                ConfigTextField(stringResource(R.string.agent_model_default), draft.agentDefault) { draft = draft.copy(agentDefault = it) }
                ConfigTextField(stringResource(R.string.agent_model_cheap), draft.agentCheap) { draft = draft.copy(agentCheap = it) }
                ConfigTextField(stringResource(R.string.agent_model_reasoning), draft.agentReasoning) { draft = draft.copy(agentReasoning = it) }
            }
        }
        item {
            ConfigCard(stringResource(R.string.multimodal_models)) {
                Text(stringResource(R.string.vision_routing), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto", "main", "dedicated").forEach { mode ->
                        FilterChip(selected = draft.visionRouting == mode, onClick = { draft = draft.copy(visionRouting = mode) }, label = { Text(mode) })
                    }
                }
                UserConfigDraft.MULTIMODAL_KEYS.forEach { key ->
                    ConfigTextField(multimodalLabel(key), draft.multimodal[key].orEmpty()) { updated ->
                        draft = draft.copy(multimodal = draft.multimodal + (key to updated))
                    }
                }
            }
        }
        item {
            ConfigCard(stringResource(R.string.knowledge_permissions)) {
                ConfigToggleRow(stringResource(R.string.use_shared_knowledge), draft.useSharedKnowledge, busy = busy) { draft = draft.copy(useSharedKnowledge = it) }
                ConfigToggleRow(stringResource(R.string.use_global_knowledge), draft.useGlobalKnowledge, busy = busy) { draft = draft.copy(useGlobalKnowledge = it) }
                ConfigTextField(stringResource(R.string.skills_shared_whitelist), draft.skillsSharedWhitelist) { draft = draft.copy(skillsSharedWhitelist = it) }
                ConfigTextField(stringResource(R.string.expand_shared_whitelist), draft.expandSharedWhitelist) { draft = draft.copy(expandSharedWhitelist = it) }
                ConfigTextField(stringResource(R.string.expand_global_whitelist), draft.expandGlobalWhitelist) { draft = draft.copy(expandGlobalWhitelist = it) }
                ConfigTextField(stringResource(R.string.perception_global_whitelist), draft.perceptionGlobalWhitelist) { draft = draft.copy(perceptionGlobalWhitelist = it) }
                ConfigTextField(stringResource(R.string.plugins_whitelist), draft.pluginsWhitelist) { draft = draft.copy(pluginsWhitelist = it) }
            }
        }
        item {
            ConfigCard(stringResource(R.string.injection_configuration)) {
                ConfigToggleRow(stringResource(R.string.expand_prompt_injection), draft.expandPromptInjection, busy = busy) { draft = draft.copy(expandPromptInjection = it) }
                ConfigToggleRow(stringResource(R.string.expand_realtime_injection), draft.expandRealtimeInjection, busy = busy) { draft = draft.copy(expandRealtimeInjection = it) }
                ConfigToggleRow(stringResource(R.string.perception_prompt_injection), draft.perceptionPromptInjection, busy = busy) { draft = draft.copy(perceptionPromptInjection = it) }
                ConfigToggleRow(stringResource(R.string.perception_realtime_injection), draft.perceptionRealtimeInjection, busy = busy) { draft = draft.copy(perceptionRealtimeInjection = it) }
            }
        }
        item {
            ConfigCard(stringResource(R.string.task_configuration)) {
                ConfigToggleRow(stringResource(R.string.task_auto_accept), draft.taskPlanAutoAccept, busy = busy) { draft = draft.copy(taskPlanAutoAccept = it) }
            }
        }
    }
}

@Composable
private fun KemoModelSelector(
    selected: String,
    models: List<String>,
    discoveryEnabled: Boolean,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(stringResource(R.string.kemo_model_selection), style = MaterialTheme.typography.titleSmall)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { stringResource(R.string.choose_model) })
        }
    }
    AnimatedVisibility(visible = expanded && models.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                models.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            expanded = false
                            onSelected(model)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = model == selected,
                            onClick = {
                                expanded = false
                                onSelected(model)
                            },
                        )
                        Text(model, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (discoveryEnabled) R.string.kemo_models_secure_hint else R.string.kemo_models_save_first),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onRefresh, enabled = discoveryEnabled) { Text(stringResource(R.string.refresh)) }
    }
}

@Composable
private fun ConfigCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ConfigTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun ConfigToggleRow(label: String, checked: Boolean, busy: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        BusySwitch(checked, onCheckedChange, busy = busy)
    }
}

@Composable
private fun multimodalLabel(key: String): String = stringResource(when (key) {
    "vision" -> R.string.multimodal_vision
    "image_generation" -> R.string.multimodal_image_generation
    "image_edit" -> R.string.multimodal_image_edit
    "audio_transcription" -> R.string.multimodal_audio_transcription
    "speech_generation" -> R.string.multimodal_speech_generation
    "speech_to_speech" -> R.string.multimodal_speech_to_speech
    "video_understanding" -> R.string.multimodal_video_understanding
    "video_generation" -> R.string.multimodal_video_generation
    else -> R.string.multimodal_models
})

