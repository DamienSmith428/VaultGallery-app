package com.vaultgallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vaultgallery.domain.model.AppSettings
import com.vaultgallery.domain.model.AuthMethod
import com.vaultgallery.domain.model.AutoLockTimeout
import com.vaultgallery.domain.model.RecycleAutoDelete
import com.vaultgallery.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_settings")

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AUTH_METHOD = stringPreferencesKey("auth_method")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val RECYCLE_AUTO_DELETE = stringPreferencesKey("recycle_auto_delete")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val VAULT_SIZE_LIMIT = intPreferencesKey("vault_size_limit")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            authMethod = prefs[Keys.AUTH_METHOD]?.let { AuthMethod.valueOf(it) } ?: AuthMethod.PIN,
            autoLockTimeout = prefs[Keys.AUTO_LOCK_TIMEOUT]?.let { AutoLockTimeout.valueOf(it) } ?: AutoLockTimeout.ONE_MINUTE,
            recycleAutoDelete = prefs[Keys.RECYCLE_AUTO_DELETE]?.let { RecycleAutoDelete.valueOf(it) } ?: RecycleAutoDelete.THIRTY,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            vaultSizeLimitGb = prefs[Keys.VAULT_SIZE_LIMIT] ?: 5
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_METHOD] = settings.authMethod.name
            prefs[Keys.AUTO_LOCK_TIMEOUT] = settings.autoLockTimeout.name
            prefs[Keys.RECYCLE_AUTO_DELETE] = settings.recycleAutoDelete.name
            prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.ONBOARDING_COMPLETE] = settings.hasCompletedOnboarding
            prefs[Keys.VAULT_SIZE_LIMIT] = settings.vaultSizeLimitGb
        }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }
}
