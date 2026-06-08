package com.vaultgallery.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.vaultgallery.data.database.dao.AlbumDao
import com.vaultgallery.data.database.dao.MediaDao
import com.vaultgallery.data.database.entities.AlbumEntity
import com.vaultgallery.data.database.entities.MediaEntity
import com.vaultgallery.domain.model.MediaType
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun fromTagList(tags: List<String>): String {
        val arr = JSONArray()
        tags.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toTagList(json: String): List<String> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getString(it) }
    }
}

@Database(
    entities = [MediaEntity::class, AlbumEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
}
