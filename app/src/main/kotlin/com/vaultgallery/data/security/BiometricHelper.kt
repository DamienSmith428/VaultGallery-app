package com.vaultgallery.data.security

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isBiometricAvailable(): Boolean {
        val manager = AndroidBiometricManager.from(context)
        return manager.canAuthenticate(
            AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == AndroidBiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Use biometrics to access your vault")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
