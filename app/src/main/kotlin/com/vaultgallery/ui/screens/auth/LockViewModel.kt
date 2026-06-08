package com.vaultgallery.ui.screens.auth

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.data.security.BiometricHelper
import com.vaultgallery.data.security.PinManager
import com.vaultgallery.domain.model.AuthMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockState(
    val pin: String = "",
    val error: String? = null,
    val authMethod: AuthMethod = AuthMethod.PIN,
    val biometricAvailable: Boolean = false,
    val isLoading: Boolean = true,
    val unlocked: Boolean = false
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val biometricHelper: BiometricHelper,
    private val repository: VaultRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.settings.first()
            _state.value = _state.value.copy(
                authMethod = settings.authMethod,
                biometricAvailable = biometricHelper.isBiometricAvailable(),
                isLoading = false
            )
        }
    }

    fun updatePin(pin: String) {
        if (pin.length <= 8) {
            _state.value = _state.value.copy(pin = pin, error = null)
        }
    }

    fun submitPin(): Boolean {
        val correct = pinManager.verifyPin(_state.value.pin)
        return if (correct) {
            _state.value = _state.value.copy(unlocked = true)
            true
        } else {
            _state.value = _state.value.copy(pin = "", error = "Incorrect PIN")
            false
        }
    }

    fun triggerBiometric(activity: FragmentActivity) {
        biometricHelper.showBiometricPrompt(
            activity = activity,
            onSuccess = { _state.value = _state.value.copy(unlocked = true) },
            onFailed = { _state.value = _state.value.copy(error = "Biometric authentication failed") },
            onError = { msg ->
                // User pressed "Use PIN" — no error shown
                if (!msg.contains("cancel", ignoreCase = true)) {
                    _state.value = _state.value.copy(error = msg)
                }
            }
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
