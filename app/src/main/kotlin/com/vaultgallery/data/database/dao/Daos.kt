package com.vaultgallery.data.database.dao

import androidx.room.*
import com.vaultgallery.data.database.entities.AlbumEntity
import com.vaultgallery.data.database.entities.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE isInRecycleBin = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isInRecycleBin = 0 AND albumId = :albumId ORDER BY dateAdded DESC")
    fun getMediaByAlbum(albumId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isInRecycleBin = 0 AND isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isInRecycleBin = 1 ORDER BY recycleDate DESC")
    fun getRecycleBin(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: String): MediaEntity?

    @Query("""
        SELECT * FROM media WHERE isInRecycleBin = 0 AND (
            originalFileName LIKE '%' || :query || '%' OR
            tags LIKE '%' || :query || '%'
        ) ORDER BY dateAdded DESC
    """)
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE media SET isInRecycleBin = 1, recycleDate = :recycleDate WHERE id = :id")
    suspend fun moveToRecycleBin(id: String, recycleDate: Long)

    @Query("UPDATE media SET isInRecycleBin = 0, recycleDate = NULL WHERE id = :id")
    suspend fun restoreFromRecycleBin(id: String)

    @Query("UPDATE media SET albumId = :albumId WHERE id = :id")
    suspend fun moveToAlbum(id: String, albumId: String?)

    @Query("SELECT COUNT(*) FROM media WHERE isInRecycleBin = 0 AND albumId = :albumId")
    fun getMediaCountForAlbum(albumId: String): Flow<Int>

    @Query("SELECT * FROM media WHERE isInRecycleBin = 1 AND recycleDate < :cutoff")
    suspend fun getExpiredRecycleBinItems(cutoff: Long): List<MediaEntity>

    @Query("UPDATE media SET albumId = NULL WHERE albumId = :albumId")
    suspend fun removeAlbumFromMedia(albumId: String)
}

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY dateCreated DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity)

    @Update
    suspend fun update(album: AlbumEntity)

    @Delete
    suspend fun delete(album: AlbumEntity)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE albums SET coverMediaId = :mediaId WHERE id = :id")
    suspend fun setCover(id: String, mediaId: String?)
}
