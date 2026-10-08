package com.example.data.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom

class VaultStorageManager(private val context: Context) {

    private val vaultDir: File get() = File(context.filesDir, "vault")
    private val objectsDir: File get() = File(vaultDir, "objects")
    private val thumbnailsDir: File get() = File(vaultDir, "thumbnails")

    init {
        objectsDir.mkdirs()
        thumbnailsDir.mkdirs()
    }

    fun getObjectFile(relativeName: String): File = File(objectsDir, relativeName)
    fun getThumbnailFile(relativeName: String): File = File(thumbnailsDir, relativeName)

    /**
     * Writes encrypted data atomically using a temporary file.
     */
    fun writeEncryptedFile(targetFile: File, data: ByteArray) {
        val parent = targetFile.parentFile ?: objectsDir
        if (!parent.exists()) parent.mkdirs()

        val tempFile = File(parent, "${targetFile.name}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across file system boundaries
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * Reads the encrypted file contents into a byte array.
     */
    fun readEncryptedFile(file: File): ByteArray {
        if (!file.exists()) {
            throw IllegalArgumentException("Target encrypted file not found: ${file.name}")
        }
        return file.readBytes()
    }

    /**
     * Best-effort secure overwrite before file deletion.
     */
    fun secureDelete(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            if (length > 0 && length < 50L * 1024 * 1024) { // Only overwrite files under 50MB
                RandomAccessFile(file, "rws").use { raf ->
                    val zeroBytes = ByteArray(minOf(length.toInt(), 4096))
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBytes.size.toLong(), length - written).toInt()
                        raf.write(zeroBytes, 0, toWrite)
                        written += toWrite
                    }
                }
            }
        } catch (_: Exception) {
            // Best effort
        } finally {
            file.delete()
        }
    }

    /**
     * Completely wipes all vault objects and thumbnails.
     */
    fun purgeAllVaultFiles() {
        objectsDir.listFiles()?.forEach { secureDelete(it) }
        thumbnailsDir.listFiles()?.forEach { secureDelete(it) }
    }

    /**
     * Calculates total storage used by encrypted vault items.
     */
    fun calculateTotalVaultBytes(): Long {
        var total = 0L
        objectsDir.listFiles()?.forEach { total += it.length() }
        thumbnailsDir.listFiles()?.forEach { total += it.length() }
        return total
    }
}
