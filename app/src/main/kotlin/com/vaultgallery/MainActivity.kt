package com.vaultgallery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.data.security.AutoLockManager
import com.vaultgallery.domain.model.ThemeMode
import com.vaultgallery.ui.navigation.VaultNavHost
import com.vaultgallery.ui.theme.VaultGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var repository: VaultRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            val settings by repository.settings.collectAsState(initial = null)
            
            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            VaultGalleryTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor ?: true
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VaultNavHost(autoLockManager)
                }
            }
        }
    }
}
