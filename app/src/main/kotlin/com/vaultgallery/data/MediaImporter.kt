package com.vaultgallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.vaultgallery.data.security.VaultCrypto
import com.vaultgallery.domain.model.MediaType
import com.vaultgallery.domain.model.VaultMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class ImportResult {
    data class Success(val media: VaultMedia) : ImportResult()
    data class PartialSuccess(val media: VaultMedia, val originalDeleted: Boolean, val uri: Uri? = null) : ImportResult()
    data class Failure(val uri: Uri, val reason: String) : ImportResult()
}

@Singleton
class MediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: VaultCrypto
) {
    private val vaultDir: File by lazy {
        File(context.filesDir, "vault").also { it.mkdirs() }
    }

    private val thumbDir: File by lazy {
        File(context.filesDir, "thumbs").also { it.mkdirs() }
    }

    private fun resolveMediaUri(cr: ContentResolver, uri: Uri): Uri? {
        if (uri.authority == "com.android.providers.media.documents") {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val id = split[1]
            val baseUri = when (type) {
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> return uri
            }
            return android.content.ContentUris.withAppendedId(baseUri, id.toLong())
        }
        return uri
    }

    suspend fun importFromUri(
        uri: Uri,
        albumId: String?,
        deleteOriginal: Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val mediaUri = resolveMediaUri(cr, uri) ?: uri
            
            val mimeType = cr.getType(mediaUri) ?: "application/octet-stream"
            val mediaType = when {
                mimeType.startsWith("image/") -> MediaType.IMAGE
                mimeType.startsWith("video/") -> MediaType.VIDEO
                else -> return@withContext ImportResult.Failure(mediaUri, "Unsupported media type: $mimeType")
            }

            val originalName = resolveDisplayName(cr, mediaUri) ?: "${UUID.randomUUID()}"
            val fileSize = resolveFileSize(cr, mediaUri)
            val encId = UUID.randomUUID().toString()
            val encFileName = "$encId.enc"
            val encFile = File(vaultDir, encFileName)

            // Encrypt directly from URI input stream
            cr.openInputStream(mediaUri)?.use { inputStream ->
                encFile.outputStream().use { outputStream ->
                    crypto.encrypt(inputStream, outputStream)
                }
            } ?: return@withContext ImportResult.Failure(mediaUri, "Cannot open input stream")

            // Metadata & Thumbnail
            val (width, height, duration) = resolveMediaMetadata(mediaUri, mediaType)
            val thumbBitmap = generateThumbnail(mediaUri, mediaType)
            val thumbFileName = if (thumbBitmap != null) {
                val tName = "${encId}_thumb.enc"
                val tFile = File(thumbDir, tName)
                val bos = ByteArrayOutputStream()
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                val thumbBytes = bos.toByteArray()
                crypto.encrypt(thumbBytes.inputStream(), tFile.outputStream())
                tName
            } else null

            val media = VaultMedia(
                id = encId,
                encryptedFileName = encFileName,
                originalFileName = originalName,
                mediaType = mediaType,
                albumId = albumId,
                size = fileSize,
                dateAdded = System.currentTimeMillis(),
                thumbnailFileName = thumbFileName,
                width = width,
                height = height,
                duration = duration
            )

            // Attempt to delete original if requested
            val deleted = if (deleteOriginal) tryDeleteOriginal(cr, mediaUri) else false

            if (!deleteOriginal || deleted) {
                ImportResult.Success(media)
            } else {
                ImportResult.PartialSuccess(media, false, mediaUri)
            }
        } catch (e: Exception) {
            ImportResult.Failure(uri, e.message ?: "Unknown error")
        }
    }

    private fun resolveDisplayName(cr: ContentResolver, uri: Uri): String? {
        // Try content resolver query first
        try {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        // Fallback: last path segment
        return uri.lastPathSegment
    }

    private fun resolveFileSize(cr: ContentResolver, uri: Uri): Long {
        try {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        } catch (_: Exception) {}
        return 0L
    }

    private fun resolveMediaMetadata(uri: Uri, mediaType: MediaType): Triple<Int, Int, Long> {
        var width = 0
        var height = 0
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            if (mediaType == MediaType.IMAGE && (width == 0 || height == 0)) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                width = options.outWidth
                height = options.outHeight
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return Triple(width, height, duration)
    }

    private fun generateThumbnail(uri: Uri, mediaType: MediaType): Bitmap? {
        return try {
            if (mediaType == MediaType.IMAGE) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                
                val maxDimension = 300
                var inSampleSize = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                        inSampleSize *= 2
                    }
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 300, 300)
                    } else {
                        retriever.getFrameAtTime(-1)
                    }
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { null }
    }

    private fun tryDeleteOriginal(cr: ContentResolver, uri: Uri): Boolean {
        return try {
            if (cr.delete(uri, null, null) > 0) {
                true
            } else {
                // If delete returns 0, it might be because the URI isn't a direct MediaStore row
                // but a virtual or different provider URI.
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens a decrypted InputStream from a vault encrypted file.
     * Caller is responsible for closing the stream.
     */
    fun openDecryptedStream(encryptedFileName: String): java.io.InputStream? {
        val file = if (encryptedFileName.endsWith("_thumb.enc")) File(thumbDir, encryptedFileName) 
                   else File(vaultDir, encryptedFileName)
        if (!file.exists()) return null
        val fis = file.inputStream()
        return crypto.decryptStream(fis)
    }

    /**
     * Reads entire decrypted content into memory (for photos/small items).
     */
    fun readDecryptedBytes(encryptedFileName: String): ByteArray? {
        val file = if (encryptedFileName.endsWith("_thumb.enc")) File(thumbDir, encryptedFileName)
                   else File(vaultDir, encryptedFileName)
        if (!file.exists()) return null
        return crypto.decryptToBytes(file.inputStream())
    }

    /**
     * Permanently deletes an encrypted file from vault storage.
     */
    fun deleteEncryptedFile(encryptedFileName: String): Boolean {
        val file = if (encryptedFileName.endsWith("_thumb.enc")) File(thumbDir, encryptedFileName)
                   else File(vaultDir, encryptedFileName)
        return file.delete()
    }

    fun getEncryptedFile(encryptedFileName: String): File = File(vaultDir, encryptedFileName)

    suspend fun exportToGallery(media: VaultMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = readDecryptedBytes(media.encryptedFileName) ?: return@withContext false
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, media.originalFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (media.mediaType == MediaType.IMAGE) "image/jpeg" else "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (media.mediaType == MediaType.IMAGE) "Pictures/VaultGallery" else "Movies/VaultGallery")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (media.mediaType == MediaType.IMAGE) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, contentValues) ?: return@withContext false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
