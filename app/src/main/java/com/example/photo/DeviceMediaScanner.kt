package com.example.photo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceMediaItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val size: Long = 0L,
    val sourcePath: String = ""
)

object DeviceMediaScanner {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    /**
     * Scans MediaStore for all device photos across Gallery, DCIM, Pictures, Screenshots.
     */
    suspend fun queryGalleryImages(context: Context): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<DeviceMediaItem>()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "Photo_$id.jpg" else "Photo_$id.jpg"
                    val dateAdded = if (dateColumn != -1) cursor.getLong(dateColumn) * 1000L else System.currentTimeMillis()
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L

                    mediaList.add(
                        DeviceMediaItem(
                            uri = contentUri,
                            name = name,
                            dateAdded = dateAdded,
                            size = size
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Permission or querying fallback handled gracefully
        }

        // Also check physical public directories (Downloads, Pictures, DCIM)
        try {
            val dirsToCheck = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            )

            for (dir in dirsToCheck) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    scanDirectoryRecursive(dir, mediaList, maxDepth = 2)
                }
            }
        } catch (_: Throwable) {}

        // Remove duplicates by URI string
        return@withContext mediaList.distinctBy { it.uri.toString() }
    }

    /**
     * Scans Downloads folder and Browser Download directories specifically for downloaded photos.
     */
    suspend fun queryDownloadImages(context: Context): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val downloadList = mutableListOf<DeviceMediaItem>()
        try {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    "_data"
                )
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1
                
                val dataColumn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex("_data")
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                    val dateAdded = if (dateColumn != -1) cursor.getLong(dateColumn) * 1000L else System.currentTimeMillis()
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L

                    var isDownload = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePathColumn != -1) {
                        val path = cursor.getString(relativePathColumn) ?: ""
                        if (path.contains("Download", ignoreCase = true)) {
                            isDownload = true
                        }
                    }
                    if (!isDownload && dataColumn != -1) {
                        val path = cursor.getString(dataColumn) ?: ""
                        if (path.contains("/Download/", ignoreCase = true) || path.contains("/Downloads/", ignoreCase = true)) {
                            isDownload = true
                        }
                    }
                    if (!isDownload && name.contains("download", ignoreCase = true)) {
                        isDownload = true
                    }

                    if (isDownload) {
                        downloadList.add(
                            DeviceMediaItem(
                                uri = contentUri,
                                name = if (name.isNotEmpty()) name else "Download_$id.jpg",
                                dateAdded = dateAdded,
                                size = size
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        // Fallback directory scanning as backup
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                scanDirectoryRecursive(downloadsDir, downloadList, maxDepth = 2)
            }

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (picturesDir != null && picturesDir.exists()) {
                scanDirectoryRecursive(picturesDir, downloadList, maxDepth = 2)
            }

            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDownloads != null && appDownloads.exists()) {
                scanDirectoryRecursive(appDownloads, downloadList, maxDepth = 2)
            }
        } catch (_: Throwable) {}

        return@withContext downloadList.distinctBy { it.uri.toString() }
    }

    private fun scanDirectoryRecursive(
        dir: File,
        outList: MutableList<DeviceMediaItem>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursive(file, outList, maxDepth, currentDepth + 1)
            } else if (file.isFile && file.length() > 0) {
                val ext = file.extension.lowercase()
                if (IMAGE_EXTENSIONS.contains(ext)) {
                    outList.add(
                        DeviceMediaItem(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            dateAdded = file.lastModified(),
                            size = file.length(),
                            sourcePath = file.absolutePath
                        )
                    )
                }
            }
        }
    }

    /**
     * Loads thumbnail safely for a device media item.
     */
    suspend fun loadDeviceThumbnail(context: Context, item: DeviceMediaItem): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                try {
                    return@withContext context.contentResolver.loadThumbnail(
                        item.uri,
                        Size(256, 256),
                        null
                    )
                } catch (_: Throwable) {}
            }

            // Fallback decode stream with downsampling
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)
                
                var inSampleSize = 1
                val maxDim = 256
                while ((options.outWidth / inSampleSize) > maxDim || (options.outHeight / inSampleSize) > maxDim) {
                    inSampleSize *= 2
                }

                context.contentResolver.openInputStream(item.uri)?.use { secondInput ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize.coerceAtLeast(1)
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    return@withContext BitmapFactory.decodeStream(secondInput, null, decodeOptions)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
