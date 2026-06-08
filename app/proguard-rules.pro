# Vault Gallery ProGuard Rules

# Keep Room entities
-keep class com.vaultgallery.data.database.entities.** { *; }

# Keep domain models
-keep class com.vaultgallery.domain.model.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Biometric
-keep class androidx.biometric.** { *; }

# Security Crypto
-keep class androidx.security.crypto.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# General Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
