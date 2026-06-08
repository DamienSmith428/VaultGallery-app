package com.vaultgallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.ui.coil.EncryptedThumbnailFetcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VaultApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var repository: VaultRepository

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(EncryptedThumbnailFetcher.Factory(repository))
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
    }
}
