package com.vaultgallery.data.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaultgallery.data.AppSettingsDataStore
import com.vaultgallery.domain.model.AutoLockTimeout
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLockManager @Inject constructor(
    private val settingsDataStore: AppSettingsDataStore
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAt: Long = 0L
    private var isTemporaryExiting: Boolean = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setTemporaryExiting(exiting: Boolean) {
        isTemporaryExiting = exiting
    }

    fun unlock() {
        _isLocked.value = false
        backgroundedAt = 0L
    }

    fun lockNow() {
        _isLocked.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!isTemporaryExiting) {
            backgroundedAt = System.currentTimeMillis()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isTemporaryExiting) {
            isTemporaryExiting = false
            return
        }
        if (backgroundedAt == 0L) return
        scope.launch {
            val settings = settingsDataStore.settings.first()
            val timeout = settings.autoLockTimeout
            if (timeout == AutoLockTimeout.IMMEDIATELY) {
                lockNow()
                return@launch
            }
            val elapsed = System.currentTimeMillis() - backgroundedAt
            if (elapsed >= timeout.seconds * 1000L) {
                lockNow()
            }
        }
    }
}
