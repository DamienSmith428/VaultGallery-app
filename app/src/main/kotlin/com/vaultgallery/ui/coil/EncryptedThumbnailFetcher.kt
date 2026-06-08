package com.vaultgallery.ui.coil

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.vaultgallery.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom model to represent an encrypted thumbnail in the vault.
 * Using a specific class ensures Coil uses our [EncryptedThumbnailFetcher].
 */
data class EncryptedThumbnailModel(val fileName: String)

class EncryptedThumbnailFetcher(
    private val data: EncryptedThumbnailModel,
    private val repository: VaultRepository,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            val fileName = data.fileName
            val bytes = repository.readDecryptedBytes(fileName)
            if (bytes == null) {
                Log.e("VaultGallery_Fetch", "Failed to decrypt bytes for $fileName (returned null)")
                return@withContext null
            }
            
            if (bytes.isEmpty()) {
                Log.e("VaultGallery_Fetch", "Decrypted bytes are empty for $fileName")
                return@withContext null
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                Log.e("VaultGallery_Fetch", "Failed to decode bitmap for $fileName")
                return@withContext null
            }
            
            DrawableResult(
                drawable = BitmapDrawable(options.context.resources, bitmap),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            Log.e("ThumbnailFetcher", "Exception in fetcher for ${data.fileName}", e)
            null
        }
    }

    class Factory(private val repository: VaultRepository) : Fetcher.Factory<EncryptedThumbnailModel> {
        override fun create(data: EncryptedThumbnailModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return EncryptedThumbnailFetcher(data, repository, options)
        }
    }
}
