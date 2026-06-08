package com.vaultgallery.domain.model

import java.util.UUID

enum class MediaType { IMAGE, VIDEO }

enum class AuthMethod { PIN, BIOMETRIC, BOTH }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AutoLockTimeout(val seconds: Long, val label: String) {
    IMMEDIATELY(0L, "Immediately"),
    THIRTY_SECONDS(30L, "30 seconds"),
    ONE_MINUTE(60L, "1 minute"),
    FIVE_MINUTES(300L, "5 minutes"),
    FIFTEEN_MINUTES(900L, "15 minutes")
}

enum class RecycleAutoDelete(val days: Int, val label: String) {
    SEVEN(7, "7 days"),
    THIRTY(30, "30 days"),
    SIXTY(60, "60 days"),
    NEVER(-1, "Never")
}

data class VaultMedia(
    val id: String = UUID.randomUUID().toString(),
    val encryptedFileName: String,
    val originalFileName: String,
    val mediaType: MediaType,
    val albumId: String?,
    val dateAdded: Long = System.currentTimeMillis(),
    val size: Long,
    val duration: Long = 0L, // ms, for video
    val isFavorite: Boolean = false,
    val isInRecycleBin: Boolean = false,
    val recycleDate: Long? = null,
    val tags: List<String> = emptyList(),
    val thumbnailFileName: String? = null,
    val width: Int = 0,
    val height: Int = 0
)

data class VaultAlbum(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverMediaId: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val mediaCount: Int = 0
)

data class AppSettings(
    val authMethod: AuthMethod = AuthMethod.PIN,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val recycleAutoDelete: RecycleAutoDelete = RecycleAutoDelete.THIRTY,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hasCompletedOnboarding: Boolean = false,
    val vaultSizeLimitGb: Int = 5,
    val deleteOriginalAfterImport: Boolean = false
)
