package com.vaultgallery.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.data.security.BiometricHelper
import com.vaultgallery.data.security.PinManager
import com.vaultgallery.domain.model.AppSettings
import com.vaultgallery.domain.model.AuthMethod
import com.vaultgallery.domain.model.AutoLockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val hasCompletedOnboarding: Boolean = false,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val pin: String = "",
    val confirmPin: String = "",
    val pinError: String? = null,
    val biometricAvailable: Boolean = false,
    val useBiometric: Boolean = false,
    val selectedTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE
)

enum class OnboardingStep {
    WELCOME, SET_PIN, BIOMETRICS, AUTO_LOCK, COMPLETE
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val pinManager: PinManager,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.settings.first()
            _state.value = _state.value.copy(
                hasCompletedOnboarding = settings.hasCompletedOnboarding,
                biometricAvailable = biometricHelper.isBiometricAvailable()
            )
        }
    }

    fun nextStep() {
        val current = _state.value.currentStep
        val next = when (current) {
            OnboardingStep.WELCOME -> OnboardingStep.SET_PIN
            OnboardingStep.SET_PIN -> if (_state.value.biometricAvailable) OnboardingStep.BIOMETRICS else OnboardingStep.AUTO_LOCK
            OnboardingStep.BIOMETRICS -> OnboardingStep.AUTO_LOCK
            OnboardingStep.AUTO_LOCK -> OnboardingStep.COMPLETE
            OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
        }
        _state.value = _state.value.copy(currentStep = next)
    }

    fun updatePin(pin: String) {
        _state.value = _state.value.copy(pin = pin, pinError = null)
    }

    fun updateConfirmPin(pin: String) {
        _state.value = _state.value.copy(confirmPin = pin, pinError = null)
    }

    fun confirmSetPin(): Boolean {
        val s = _state.value
        if (s.pin.length < 4) {
            _state.value = s.copy(pinError = "PIN must be at least 4 digits")
            return false
        }
        if (s.pin != s.confirmPin) {
            _state.value = s.copy(pinError = "PINs don't match", confirmPin = "")
            return false
        }
        pinManager.setPin(s.pin)
        return true
    }

    fun toggleBiometric(enabled: Boolean) {
        _state.value = _state.value.copy(useBiometric = enabled)
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        _state.value = _state.value.copy(selectedTimeout = timeout)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val s = _state.value
            val authMethod = when {
                s.useBiometric && s.biometricAvailable -> AuthMethod.BOTH
                else -> AuthMethod.PIN
            }
            repository.updateSettings(
                AppSettings(
                    authMethod = authMethod,
                    autoLockTimeout = s.selectedTimeout,
                    hasCompletedOnboarding = true
                )
            )
        }
    }
}
