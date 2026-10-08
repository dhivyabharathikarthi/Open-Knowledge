package com.example.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.crypto.PhotoCipher
import com.example.data.database.EncryptedPhotoEntity
import com.example.data.database.PhotoDao
import com.example.data.storage.VaultStorageManager
import com.example.domain.model.PhotoMetadata
import com.example.security.SecureVaultSession
import com.example.util.SecureMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val storageManager: VaultStorageManager,
    private val photoDao: PhotoDao
) {

    val allPhotos: Flow<List<EncryptedPhotoEntity>> = photoDao.getAllPhotos()

    suspend fun getPhotoCount(): Int = withContext(Dispatchers.IO) {
        photoDao.getPhotoCount()
    }

    suspend fun getPhotoById(id: String): EncryptedPhotoEntity? = withContext(Dispatchers.IO) {
        photoDao.getPhotoById(id)
    }

    suspend fun loadThumbnailBitmap(entity: EncryptedPhotoEntity, vmk: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Check in-memory cache
        val cached = SecureVaultSession.getCachedThumbnail(entity.id)
        if (cached != null) return@withContext cached

        // 2. Decrypt on-demand from encrypted file
        var decryptedBytes: ByteArray? = null
        try {
            val thumbFile = storageManager.getThumbnailFile(entity.relativeThumbnailPath)
            if (!thumbFile.exists()) return@withContext null

            val encThumbData = storageManager.readEncryptedFile(thumbFile)
            decryptedBytes = PhotoCipher.decryptPayload(
                encryptedSerializedData = encThumbData,
                wrappedKey = entity.wrappedPhotoKey,
                keyNonce = entity.photoKeyNonce,
                vmk = vmk
            )

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
            if (bitmap != null) {
                SecureVaultSession.putCachedThumbnail(entity.id, bitmap)
            }
            bitmap
        } catch (_: Throwable) {
            null
        } finally {
            SecureMemory.zeroize(decryptedBytes)
        }
    }

    suspend fun loadFullPhotoBitmap(entity: EncryptedPhotoEntity, vmk: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        var decryptedBytes: ByteArray? = null
        try {
            val objFile = storageManager.getObjectFile(entity.relativeObjectPath)
            if (!objFile.exists()) return@withContext null

            val encData = storageManager.readEncryptedFile(objFile)
            decryptedBytes = PhotoCipher.decryptPayload(
                encryptedSerializedData = encData,
                wrappedKey = entity.wrappedPhotoKey,
                keyNonce = entity.photoKeyNonce,
                vmk = vmk
            )

            // Probe bounds to prevent OOM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, bounds)

            var sampleSize = 1
            val maxDimension = 3840
            while ((bounds.outWidth / sampleSize) > maxDimension || (bounds.outHeight / sampleSize) > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, decodeOptions)
        } catch (_: Throwable) {
            null
        } finally {
            SecureMemory.zeroize(decryptedBytes)
        }
    }

    suspend fun loadMetadata(entity: EncryptedPhotoEntity, vmk: ByteArray): PhotoMetadata? = withContext(Dispatchers.IO) {
        try {
            PhotoCipher.decryptMetadata(
                encryptedMetadata = entity.encryptedMetadata,
                metadataNonce = entity.metadataNonce,
                wrappedKey = entity.wrappedPhotoKey,
                keyNonce = entity.photoKeyNonce,
                vmk = vmk
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun toggleFavorite(id: String, currentFavorite: Boolean): Unit = withContext(Dispatchers.IO) {
        photoDao.updateFavorite(id, !currentFavorite, System.currentTimeMillis())
    }

    suspend fun deletePhoto(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = photoDao.getPhotoById(id) ?: return@withContext false
        val objFile = storageManager.getObjectFile(entity.relativeObjectPath)
        val thumbFile = storageManager.getThumbnailFile(entity.relativeThumbnailPath)

        storageManager.secureDelete(objFile)
        storageManager.secureDelete(thumbFile)
        photoDao.deletePhoto(id) > 0
    }

    suspend fun deleteAllPhotos(): Boolean = withContext(Dispatchers.IO) {
        storageManager.purgeAllVaultFiles()
        SecureVaultSession.clearThumbnailCache()
        photoDao.deleteAllPhotos() >= 0
    }

    suspend fun getStorageUsageBytes(): Long = withContext(Dispatchers.IO) {
        storageManager.calculateTotalVaultBytes()
    }
}
