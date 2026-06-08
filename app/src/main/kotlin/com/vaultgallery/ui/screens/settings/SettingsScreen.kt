package com.vaultgallery.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.data.security.BiometricHelper
import com.vaultgallery.data.security.PinManager
import com.vaultgallery.domain.model.AppSettings
import com.vaultgallery.domain.model.AuthMethod
import com.vaultgallery.domain.model.AutoLockTimeout
import com.vaultgallery.domain.model.RecycleAutoDelete
import com.vaultgallery.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val biometricAvailable: Boolean = false,
    val showChangePinDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val newPin: String = "",
    val confirmPin: String = "",
    val pinError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val pinManager: PinManager,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        _state.update { it.copy(biometricAvailable = biometricHelper.isBiometricAvailable()) }
    }

    fun setAutoLock(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            repository.updateSettings(_state.value.settings.copy(autoLockTimeout = timeout))
        }
    }

    fun setRecycleDelete(option: RecycleAutoDelete) {
        viewModelScope.launch {
            repository.updateSettings(_state.value.settings.copy(recycleAutoDelete = option))
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updateSettings(_state.value.settings.copy(themeMode = mode))
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(_state.value.settings.copy(dynamicColor = enabled))
        }
    }

    fun setAuthMethod(method: AuthMethod) {
        viewModelScope.launch {
            repository.updateSettings(_state.value.settings.copy(authMethod = method))
        }
    }

    fun openChangePinDialog() = _state.update { it.copy(showChangePinDialog = true, newPin = "", confirmPin = "", pinError = null) }
    fun dismissChangePinDialog() = _state.update { it.copy(showChangePinDialog = false) }
    fun openAboutDialog() = _state.update { it.copy(showAboutDialog = true) }
    fun dismissAboutDialog() = _state.update { it.copy(showAboutDialog = false) }
    fun setNewPin(pin: String) = _state.update { it.copy(newPin = pin, pinError = null) }
    fun setConfirmPin(pin: String) = _state.update { it.copy(confirmPin = pin, pinError = null) }

    fun confirmChangePin(): Boolean {
        val s = _state.value
        if (s.newPin.length < 4) {
            _state.update { it.copy(pinError = "PIN must be at least 4 digits") }
            return false
        }
        if (s.newPin != s.confirmPin) {
            _state.update { it.copy(pinError = "PINs don't match", confirmPin = "") }
            return false
        }
        pinManager.setPin(s.newPin)
        _state.update { it.copy(showChangePinDialog = false) }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLockVault: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSectionHeader("Security")

            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Authentication Method", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    AuthMethod.entries.forEach { method ->
                        if (method == AuthMethod.BIOMETRIC && !state.biometricAvailable) return@forEach
                        if (method == AuthMethod.BOTH && !state.biometricAvailable) return@forEach
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = state.settings.authMethod == method,
                                onClick = { viewModel.setAuthMethod(method) }
                            )
                            Text(
                                when (method) {
                                    AuthMethod.PIN -> "PIN only"
                                    AuthMethod.BIOMETRIC -> "Biometrics only"
                                    AuthMethod.BOTH -> "PIN + Biometrics"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            SettingsItem(
                icon = Icons.Default.Pin,
                title = "Change PIN",
                subtitle = "Update your vault PIN",
                onClick = viewModel::openChangePinDialog
            )

            SettingsSectionHeader("Auto-Lock")

            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lock vault after", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    AutoLockTimeout.entries.forEach { timeout ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = state.settings.autoLockTimeout == timeout,
                                onClick = { viewModel.setAutoLock(timeout) }
                            )
                            Text(timeout.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SettingsSectionHeader("Recycle Bin")

            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Auto-delete after", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    RecycleAutoDelete.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = state.settings.recycleAutoDelete == option,
                                onClick = { viewModel.setRecycleDelete(option) }
                            )
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SettingsSectionHeader("Appearance")

            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val followSystem = state.settings.themeMode == ThemeMode.SYSTEM
                    
                    ToggleRow(
                        icon = Icons.Default.AutoMode,
                        title = "Follow System",
                        subtitle = "Sync theme with Android settings",
                        checked = followSystem,
                        onCheckedChange = { 
                            if (it) viewModel.setThemeMode(ThemeMode.SYSTEM)
                            else viewModel.setThemeMode(ThemeMode.DARK) // Default to dark when disabling auto
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    ToggleRow(
                        icon = if (state.settings.themeMode == ThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "Dark Mode",
                        subtitle = if (followSystem) "Managed by system" else "Manual override",
                        checked = state.settings.themeMode == ThemeMode.DARK,
                        enabled = !followSystem,
                        onCheckedChange = { 
                            viewModel.setThemeMode(if (it) ThemeMode.DARK else ThemeMode.LIGHT)
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    ToggleRow(
                        icon = Icons.Default.Palette,
                        title = "Dynamic Color",
                        subtitle = "Android 12+ wallpaper colors",
                        checked = state.settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            SettingsSectionHeader("Vault")

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Lock Vault Now",
                subtitle = "Return to lock screen",
                onClick = onLockVault,
                tintError = false
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "About Vault Gallery",
                subtitle = "Security & credits",
                onClick = viewModel::openAboutDialog
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "Vault Gallery v1.0.0\nSecure AES-256 Encrypted Storage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // Change PIN dialog
    if (state.showChangePinDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissChangePinDialog,
            title = { Text("Change PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.newPin,
                        onValueChange = viewModel::setNewPin,
                        label = { Text("New PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        isError = state.pinError != null,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = state.confirmPin,
                        onValueChange = viewModel::setConfirmPin,
                        label = { Text("Confirm PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        isError = state.pinError != null,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (state.pinError != null) {
                        Text(state.pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmChangePin() },
                    enabled = state.newPin.isNotEmpty() && state.confirmPin.isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissChangePinDialog) { Text("Cancel") }
            }
        )
    }

    // About Dialog
    if (state.showAboutDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAboutDialog,
            icon = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("About Vault Gallery") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Vault Gallery is a secure media storage application designed with privacy as the top priority.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    SecurityFeatureItem(
                        title = "AES-256-GCM Encryption",
                        description = "Every file is encrypted with industry-standard AES-256 in Galois/Counter Mode (GCM) for both confidentiality and integrity."
                    )

                    SecurityFeatureItem(
                        title = "Hardware-Backed Security",
                        description = "Encryption keys are stored in the Android Keystore System, protected by hardware-backed security (TEE/StrongBox) where available."
                    )

                    SecurityFeatureItem(
                        title = "Zero-Knowledge Storage",
                        description = "Decryption happens entirely in memory. Your plaintext photos and videos are never written to disk while inside the vault."
                    )

                    SecurityFeatureItem(
                        title = "Biometric Protection",
                        description = "Seamless integration with Android Biometrics for secure and convenient access to your encrypted data."
                    )

                    SecurityFeatureItem(
                        title = "Storage Quotas & Monitoring",
                        description = "Enforce custom storage limits for your vault and track your space consumption in real-time. Stay informed on how much room you have left for your private media."
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Created & Developed by", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "DamienSmith428",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAboutDialog) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SecurityFeatureItem(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    tintError: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon, null,
                tint = if (tintError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon, null, 
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), 
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
