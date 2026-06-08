package com.vaultgallery.data.repository

import android.net.Uri
import com.vaultgallery.data.AppSettingsDataStore
import com.vaultgallery.data.ImportResult
import com.vaultgallery.data.MediaImporter
import com.vaultgallery.data.database.dao.AlbumDao
import com.vaultgallery.data.database.dao.MediaDao
import com.vaultgallery.data.database.entities.AlbumEntity
import com.vaultgallery.data.database.entities.MediaEntity
import com.vaultgallery.domain.model.AppSettings
import com.vaultgallery.domain.model.RecycleAutoDelete
import com.vaultgallery.domain.model.VaultAlbum
import com.vaultgallery.domain.model.VaultMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val albumDao: AlbumDao,
    private val importer: MediaImporter,
    private val settingsDataStore: AppSettingsDataStore
) {
    fun getAllMedia(): Flow<List<VaultMedia>> =
        mediaDao.getAllMedia().map { list -> list.map { it.toDomain() } }

    fun getMediaByAlbum(albumId: String): Flow<List<VaultMedia>> =
        mediaDao.getMediaByAlbum(albumId).map { list -> list.map { it.toDomain() } }

    fun getFavorites(): Flow<List<VaultMedia>> =
        mediaDao.getFavorites().map { list -> list.map { it.toDomain() } }

    fun getRecycleBin(): Flow<List<VaultMedia>> =
        mediaDao.getRecycleBin().map { list -> list.map { it.toDomain() } }

    fun searchMedia(query: String): Flow<List<VaultMedia>> =
        mediaDao.searchMedia(query).map { list -> list.map { it.toDomain() } }

    suspend fun getMediaById(id: String): VaultMedia? =
        mediaDao.getMediaById(id)?.toDomain()

    suspend fun importMedia(uris: List<Uri>, albumId: String?): List<ImportResult> {
        val results = mutableListOf<ImportResult>()
        for (uri in uris) {
            val result = importer.importFromUri(uri, albumId)
            if (result is ImportResult.Success) {
                mediaDao.insert(result.media.toEntity())
            } else if (result is ImportResult.PartialSuccess) {
                mediaDao.insert(result.media.toEntity())
            }
            results.add(result)
        }
        return results
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) =
        mediaDao.setFavorite(id, isFavorite)

    suspend fun moveToRecycleBin(id: String) =
        mediaDao.moveToRecycleBin(id, System.currentTimeMillis())

    suspend fun restoreFromRecycleBin(id: String) =
        mediaDao.restoreFromRecycleBin(id)

    suspend fun permanentlyDelete(media: VaultMedia) {
        importer.deleteEncryptedFile(media.encryptedFileName)
        media.thumbnailFileName?.let { importer.deleteEncryptedFile(it) }
        mediaDao.deleteById(media.id)
    }

    suspend fun moveToAlbum(mediaId: String, albumId: String?) =
        mediaDao.moveToAlbum(mediaId, albumId)

    suspend fun purgeExpiredRecycleBin(autoDelete: RecycleAutoDelete) {
        if (autoDelete == RecycleAutoDelete.NEVER) return
        val cutoff = System.currentTimeMillis() - (autoDelete.days * 24 * 60 * 60 * 1000L)
        val expired = mediaDao.getExpiredRecycleBinItems(cutoff)
        expired.forEach { entity ->
            importer.deleteEncryptedFile(entity.encryptedFileName)
            entity.thumbnailFileName?.let { importer.deleteEncryptedFile(it) }
            mediaDao.deleteById(entity.id)
        }
    }

    fun openDecryptedStream(encryptedFileName: String) =
        importer.openDecryptedStream(encryptedFileName)

    fun readDecryptedBytes(encryptedFileName: String) =
        importer.readDecryptedBytes(encryptedFileName)

    suspend fun exportToGallery(media: VaultMedia): Boolean =
        importer.exportToGallery(media)

    fun getAllAlbums(): Flow<List<VaultAlbum>> =
        albumDao.getAllAlbums().map { list -> list.map { it.toDomain() } }

    suspend fun createAlbum(name: String): VaultAlbum {
        val album = VaultAlbum(name = name)
        albumDao.insert(album.toEntity())
        return album
    }

    suspend fun renameAlbum(id: String, name: String) = albumDao.rename(id, name)

    suspend fun deleteAlbum(id: String) {
        mediaDao.removeAlbumFromMedia(id)
        albumDao.getAlbumById(id)?.let { albumDao.delete(it) }
    }

    suspend fun setAlbumCover(albumId: String, mediaId: String) =
        albumDao.setCover(albumId, mediaId)

    fun getMediaCountForAlbum(albumId: String): Flow<Int> =
        mediaDao.getMediaCountForAlbum(albumId)

    val settings: Flow<AppSettings> = settingsDataStore.settings

    suspend fun updateSettings(settings: AppSettings) =
        settingsDataStore.updateSettings(settings)

    suspend fun setOnboardingComplete() = settingsDataStore.setOnboardingComplete()
}


private fun MediaEntity.toDomain(): VaultMedia = VaultMedia(
    id = id,
    encryptedFileName = encryptedFileName,
    originalFileName = originalFileName,
    mediaType = mediaType,
    albumId = albumId,
    dateAdded = dateAdded,
    size = size,
    duration = duration,
    isFavorite = isFavorite,
    isInRecycleBin = isInRecycleBin,
    recycleDate = recycleDate,
    tags = parseTags(tags),
    thumbnailFileName = thumbnailFileName,
    width = width,
    height = height
)

private fun VaultMedia.toEntity(): MediaEntity = MediaEntity(
    id = id,
    encryptedFileName = encryptedFileName,
    originalFileName = originalFileName,
    mediaType = mediaType,
    albumId = albumId,
    dateAdded = dateAdded,
    size = size,
    duration = duration,
    isFavorite = isFavorite,
    isInRecycleBin = isInRecycleBin,
    recycleDate = recycleDate,
    tags = encodeTags(tags),
    thumbnailFileName = thumbnailFileName,
    width = width,
    height = height
)

private fun AlbumEntity.toDomain(): VaultAlbum = VaultAlbum(
    id = id,
    name = name,
    coverMediaId = coverMediaId,
    dateCreated = dateCreated
)

private fun VaultAlbum.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    coverMediaId = coverMediaId,
    dateCreated = dateCreated
)

private fun parseTags(json: String): List<String> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { arr.getString(it) }
}

private fun encodeTags(tags: List<String>): String {
    val arr = JSONArray()
    tags.forEach { arr.put(it) }
    return arr.toString()
}
