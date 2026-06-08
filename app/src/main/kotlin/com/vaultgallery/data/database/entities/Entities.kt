package com.vaultgallery.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vaultgallery.domain.model.MediaType

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val encryptedFileName: String,
    val originalFileName: String,
    val mediaType: MediaType,
    val albumId: String?,
    val dateAdded: Long,
    val size: Long,
    val duration: Long,
    val isFavorite: Boolean,
    val isInRecycleBin: Boolean,
    val recycleDate: Long?,
    val tags: String, // JSON array as string
    val thumbnailFileName: String?,
    val width: Int,
    val height: Int
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverMediaId: String?,
    val dateCreated: Long
)
