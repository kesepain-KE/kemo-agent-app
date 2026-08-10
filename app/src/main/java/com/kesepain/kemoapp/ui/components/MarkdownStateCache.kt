package com.kesepain.kemoapp.ui.components

import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal object MarkdownStateCache {
    private const val MAX_ENTRIES = 192
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = object : LinkedHashMap<String, CachedMarkdownState>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMarkdownState>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(content: String): MarkdownState = cache.getOrPut(content) { CachedMarkdownState(content, scope) }
}

private class CachedMarkdownState(
    private val content: String,
    scope: CoroutineScope,
) : MarkdownState {
    private val _state = MutableStateFlow<State>(State.Loading())
    override val state: StateFlow<State> = _state.asStateFlow()
    private val _links = MutableStateFlow<Map<String, String?>>(emptyMap())
    override val links: StateFlow<Map<String, String?>> = _links.asStateFlow()
    init {
        scope.launch { parse() }
    }

    override suspend fun parse(): State {
        val parsed = parseMarkdownFlow(content).first { it !is State.Loading }
        _state.value = parsed
        return parsed
    }
}
