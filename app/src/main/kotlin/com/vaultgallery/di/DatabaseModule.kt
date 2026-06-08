package com.vaultgallery.di

import android.content.Context
import androidx.room.Room
import com.vaultgallery.data.database.VaultDatabase
import com.vaultgallery.data.database.dao.AlbumDao
import com.vaultgallery.data.database.dao.MediaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_gallery.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMediaDao(db: VaultDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideAlbumDao(db: VaultDatabase): AlbumDao = db.albumDao()
}
