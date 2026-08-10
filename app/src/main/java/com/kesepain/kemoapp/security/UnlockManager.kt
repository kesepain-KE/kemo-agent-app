package com.kesepain.kemoapp.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UnlockManager(private val autoLockMinutes: () -> Int) : DefaultLifecycleObserver {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private var backgroundAt: Long = 0

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }

    fun unlock() { _unlocked.value = true; backgroundAt = 0 }
    fun lock() { _unlocked.value = false }

    override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
    override fun onStart(owner: LifecycleOwner) {
        val timeout = autoLockMinutes().coerceAtLeast(1) * 60_000L
        if (backgroundAt > 0 && System.currentTimeMillis() - backgroundAt >= timeout) lock()
    }
}
