package com.example.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.crypto.PhotoCipher
import com.example.data.database.EncryptedPhotoEntity
import com.example.data.database.PhotoDao
import com.example.data.storage.VaultStorageManager
import com.example.domain.model.PhotoMetadata
import com.example.util.SecureMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PhotoImporter(
    private val context: Context,
    private val storageManager: VaultStorageManager,
    private val photoDao: PhotoDao
) {

    companion object {
        private const val MAX_THUMBNAIL_DIMENSION = 320
        private const val THUMBNAIL_QUALITY = 80
        private const val MAX_PHOTO_DIMENSION = 2560
        private const val MASTER_PHOTO_QUALITY = 90
    }

    /**
     * Imports a photo from URI safely, handling high-resolution images, memory limits, and permissions.
     */
    suspend fun importFromUri(uri: Uri, vmk: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val photoId = UUID.randomUUID()
        val idStr = photoId.toString()
        val objectFileName = "$idStr.bin"
        val thumbFileName = "$idStr.thumb"

        val objectFile = storageManager.getObjectFile(objectFileName)
        val thumbFile = storageManager.getThumbnailFile(thumbFileName)

        var photoBytes: ByteArray? = null
        var thumbBytes: ByteArray? = null
        var tempFile: File? = null

        try {
            // 1. Read the URI once into a temporary cache file to avoid multiple stream openings
            val tempCacheDir = context.cacheDir
            tempFile = File(tempCacheDir, "import_${UUID.randomUUID()}.tmp")
            
            var copied = false
            if (uri.scheme == "file" && uri.path != null) {
                try {
                    val localFile = File(uri.path!!)
                    if (localFile.exists() && localFile.length() > 0) {
                        localFile.inputStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copied = tempFile.exists() && tempFile.length() > 0
                    }
                } catch (_: Throwable) {}
            }

            if (!copied) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Unable to open image stream for selected photo"))
            }

            // 2. Probe dimensions from temp file
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth.coerceAtLeast(1)
            val origHeight = boundsOptions.outHeight.coerceAtLeast(1)

            // 3. Extract metadata
            val fileName = queryDisplayName(uri) ?: "photo_${idStr.take(8)}.jpg"
            val mimeType = try { context.contentResolver.getType(uri) ?: "image/jpeg" } catch (_: Throwable) { "image/jpeg" }

            // 4. Decode and process image bytes safely
            val isTooLarge = origWidth > MAX_PHOTO_DIMENSION || origHeight > MAX_PHOTO_DIMENSION
            if (!isTooLarge && tempFile.length() <= 15 * 1024 * 1024L) {
                // Read directly if reasonably sized
                photoBytes = tempFile.readBytes()
            } else {
                // Downsample large camera/downloads images to prevent OutOfMemoryError
                var sampleSize = 1
                while ((origWidth / sampleSize) > MAX_PHOTO_DIMENSION || (origHeight / sampleSize) > MAX_PHOTO_DIMENSION) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decodedBmp = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                if (decodedBmp != null) {
                    val baos = ByteArrayOutputStream()
                    decodedBmp.compress(Bitmap.CompressFormat.JPEG, MASTER_PHOTO_QUALITY, baos)
                    decodedBmp.recycle()
                    photoBytes = baos.toByteArray()
                } else {
                    photoBytes = tempFile.readBytes()
                }
            }

            if (photoBytes == null || photoBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Unable to process image data"))
            }

            // 5. Create optimized thumbnail
            thumbBytes = createThumbnailBytesFromTempFile(tempFile, origWidth, origHeight, photoBytes)

            val (width, height) = extractDimensions(photoBytes)

            // 6. Construct metadata
            val metadata = PhotoMetadata(
                id = idStr,
                originalFileName = fileName,
                mimeType = mimeType,
                sizeBytes = photoBytes.size.toLong(),
                width = if (width > 0) width else origWidth,
                height = if (height > 0) height else origHeight,
                importedAt = System.currentTimeMillis(),
                isFavorite = false
            )

            // 7. Encrypt photo, thumbnail, and metadata
            val bundle = PhotoCipher.encryptPhoto(
                photoId = photoId,
                photoBytes = photoBytes,
                thumbnailBytes = thumbBytes,
                metadata = metadata,
                vmk = vmk
            )

            // 8. Write encrypted files to storage atomically
            storageManager.writeEncryptedFile(objectFile, bundle.encryptedPhotoData)
            storageManager.writeEncryptedFile(thumbFile, bundle.encryptedThumbnailData)

            // 9. Insert metadata into Room database
            val entity = EncryptedPhotoEntity(
                id = idStr,
                relativeObjectPath = objectFileName,
                relativeThumbnailPath = thumbFileName,
                wrappedPhotoKey = bundle.wrappedPhotoKey,
                photoKeyNonce = bundle.photoKeyNonce,
                encryptedMetadata = bundle.encryptedMetadata,
                metadataNonce = bundle.metadataNonce,
                algorithmVersion = 1,
                keyVersion = 1,
                createdAt = metadata.importedAt,
                updatedAt = metadata.importedAt,
                isFavorite = false
            )
            photoDao.insertPhoto(entity)

            Result.success(idStr)
        } catch (t: Throwable) {
            // Rollback on any failure/error
            try {
                storageManager.secureDelete(objectFile)
                storageManager.secureDelete(thumbFile)
            } catch (_: Throwable) {}
            Result.failure(t)
        } finally {
            try {
                tempFile?.delete()
            } catch (_: Throwable) {}
            SecureMemory.zeroize(photoBytes)
            SecureMemory.zeroize(thumbBytes)
        }
    }

    suspend fun importBitmap(bitmap: Bitmap, originalFileName: String, vmk: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val photoId = UUID.randomUUID()
        val idStr = photoId.toString()
        val objectFileName = "$idStr.bin"
        val thumbFileName = "$idStr.thumb"

        val objectFile = storageManager.getObjectFile(objectFileName)
        val thumbFile = storageManager.getThumbnailFile(thumbFileName)

        var photoBytes: ByteArray? = null
        var thumbBytes: ByteArray? = null

        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, MASTER_PHOTO_QUALITY, baos)
            photoBytes = baos.toByteArray()
            if (photoBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Generated image data is empty"))
            }

            val width = bitmap.width
            val height = bitmap.height
            val mimeType = "image/jpeg"

            thumbBytes = createThumbnailBytes(photoBytes)

            val metadata = PhotoMetadata(
                id = idStr,
                originalFileName = originalFileName,
                mimeType = mimeType,
                sizeBytes = photoBytes.size.toLong(),
                width = width,
                height = height,
                importedAt = System.currentTimeMillis(),
                isFavorite = false
            )

            val bundle = PhotoCipher.encryptPhoto(
                photoId = photoId,
                photoBytes = photoBytes,
                thumbnailBytes = thumbBytes,
                metadata = metadata,
                vmk = vmk
            )

            storageManager.writeEncryptedFile(objectFile, bundle.encryptedPhotoData)
            storageManager.writeEncryptedFile(thumbFile, bundle.encryptedThumbnailData)

            val entity = EncryptedPhotoEntity(
                id = idStr,
                relativeObjectPath = objectFileName,
                relativeThumbnailPath = thumbFileName,
                wrappedPhotoKey = bundle.wrappedPhotoKey,
                photoKeyNonce = bundle.photoKeyNonce,
                encryptedMetadata = bundle.encryptedMetadata,
                metadataNonce = bundle.metadataNonce,
                algorithmVersion = 1,
                keyVersion = 1,
                createdAt = metadata.importedAt,
                updatedAt = metadata.importedAt,
                isFavorite = false
            )
            photoDao.insertPhoto(entity)

            Result.success(idStr)
        } catch (t: Throwable) {
            try {
                storageManager.secureDelete(objectFile)
                storageManager.secureDelete(thumbFile)
            } catch (_: Throwable) {}
            Result.failure(t)
        } finally {
            SecureMemory.zeroize(photoBytes)
            SecureMemory.zeroize(thumbBytes)
        }
    }

    /**
     * Helper to create demo sample photos directly in memory.
     */
    fun createSamplePhotoBitmap(title: String, styleIndex: Int): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val gradients = listOf(
            Pair(Color.rgb(24, 76, 120), Color.rgb(15, 23, 42)),      // Deep Ocean
            Pair(Color.rgb(180, 83, 9), Color.rgb(69, 26, 3)),       // Sunset Amber
            Pair(Color.rgb(16, 149, 193), Color.rgb(99, 102, 241)),  // Neon Indigo
            Pair(Color.rgb(13, 148, 136), Color.rgb(15, 23, 42)),    // Emerald Teal
            Pair(Color.rgb(190, 24, 93), Color.rgb(88, 28, 135)),    // Velvet Rose
            Pair(Color.rgb(79, 70, 229), Color.rgb(124, 58, 237)),   // Royal Violet
            Pair(Color.rgb(5, 150, 105), Color.rgb(4, 120, 87)),     // Forest Jade
            Pair(Color.rgb(217, 119, 6), Color.rgb(180, 83, 9))      // Golden Sun
        )

        val (startColor, endColor) = gradients[styleIndex % gradients.size]
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            startColor, endColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw decorative geometrical curves
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 25
        paint.style = Paint.Style.FILL
        canvas.drawCircle(width * 0.8f, height * 0.3f, 350f, paint)
        canvas.drawCircle(width * 0.2f, height * 0.75f, 450f, paint)

        // Decorative card box in center
        paint.alpha = 40
        paint.color = Color.BLACK
        canvas.drawRoundRect(100f, height * 0.38f, width - 100f, height * 0.65f, 40f, 40f, paint)

        // Draw Title text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, width / 2f, height * 0.50f, textPaint)

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("AES-256-GCM Encrypted Vault Object", width / 2f, height * 0.56f, subTextPaint)

        return bitmap
    }

    private fun extractDimensions(bytes: ByteArray): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            Pair(options.outWidth, options.outHeight)
        } catch (_: Throwable) {
            Pair(1080, 1920)
        }
    }

    private fun createThumbnailBytesFromTempFile(tempFile: File, origWidth: Int, origHeight: Int, fallbackBytes: ByteArray): ByteArray {
        return try {
            var inSampleSize = 1
            if (origHeight > MAX_THUMBNAIL_DIMENSION || origWidth > MAX_THUMBNAIL_DIMENSION) {
                val halfHeight = origHeight / 2
                val halfWidth = origWidth / 2
                while ((halfHeight / inSampleSize) >= MAX_THUMBNAIL_DIMENSION &&
                    (halfWidth / inSampleSize) >= MAX_THUMBNAIL_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                ?: return createThumbnailBytes(fallbackBytes)

            val targetWidth: Int
            val targetHeight: Int
            if (decodedBitmap.width > decodedBitmap.height) {
                targetWidth = MAX_THUMBNAIL_DIMENSION
                targetHeight = (decodedBitmap.height * (MAX_THUMBNAIL_DIMENSION.toFloat() / decodedBitmap.width)).toInt().coerceAtLeast(1)
            } else {
                targetHeight = MAX_THUMBNAIL_DIMENSION
                targetWidth = (decodedBitmap.width * (MAX_THUMBNAIL_DIMENSION.toFloat() / decodedBitmap.height)).toInt().coerceAtLeast(1)
            }

            val scaled = Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
            if (scaled != decodedBitmap) {
                decodedBitmap.recycle()
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, baos)
            scaled.recycle()
            baos.toByteArray()
        } catch (_: Throwable) {
            createThumbnailBytes(fallbackBytes)
        }
    }

    private fun createThumbnailBytes(bytes: ByteArray): ByteArray {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val origWidth = options.outWidth.coerceAtLeast(1)
            val origHeight = options.outHeight.coerceAtLeast(1)

            var inSampleSize = 1
            if (origHeight > MAX_THUMBNAIL_DIMENSION || origWidth > MAX_THUMBNAIL_DIMENSION) {
                val halfHeight = origHeight / 2
                val halfWidth = origWidth / 2
                while ((halfHeight / inSampleSize) >= MAX_THUMBNAIL_DIMENSION &&
                    (halfWidth / inSampleSize) >= MAX_THUMBNAIL_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: throw IllegalStateException("Failed to decode image bytes for thumbnail")

            val targetWidth: Int
            val targetHeight: Int
            if (decodedBitmap.width > decodedBitmap.height) {
                targetWidth = MAX_THUMBNAIL_DIMENSION
                targetHeight = (decodedBitmap.height * (MAX_THUMBNAIL_DIMENSION.toFloat() / decodedBitmap.width)).toInt().coerceAtLeast(1)
            } else {
                targetHeight = MAX_THUMBNAIL_DIMENSION
                targetWidth = (decodedBitmap.width * (MAX_THUMBNAIL_DIMENSION.toFloat() / decodedBitmap.height)).toInt().coerceAtLeast(1)
            }

            val scaled = Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
            if (scaled != decodedBitmap) {
                decodedBitmap.recycle()
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, baos)
            scaled.recycle()
            baos.toByteArray()
        } catch (_: Throwable) {
            // Fallback emergency tiny thumbnail if OOM or format failure
            val tinyBmp = Bitmap.createBitmap(MAX_THUMBNAIL_DIMENSION, MAX_THUMBNAIL_DIMENSION, Bitmap.Config.RGB_565)
            val baos = ByteArrayOutputStream()
            tinyBmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            tinyBmp.recycle()
            baos.toByteArray()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
