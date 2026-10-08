package com.example.security

import android.graphics.Bitmap
import android.util.LruCache
import com.example.util.SecureMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SecureVaultSession {

    private var activeVmk: ByteArray? = null

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Purely in-memory thumbnail cache (max 40 MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = minOf(maxMemory / 8, 40 * 1024) // 40MB max

    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    @Synchronized
    fun unlock(vmk: ByteArray) {
        // Clear any previous key
        lock()
        activeVmk = vmk.clone()
        _isUnlocked.value = true
    }

    @Synchronized
    fun getActiveVmk(): ByteArray? {
        return activeVmk?.clone()
    }

    @Synchronized
    fun isSessionActive(): Boolean {
        return activeVmk != null && _isUnlocked.value
    }

    @Synchronized
    fun lock() {
        activeVmk?.let {
            SecureMemory.zeroize(it)
        }
        activeVmk = null
        thumbnailCache.evictAll()
        _isUnlocked.value = false
    }

    fun getCachedThumbnail(id: String): Bitmap? {
        return thumbnailCache.get(id)
    }

    fun putCachedThumbnail(id: String, bitmap: Bitmap) {
        thumbnailCache.put(id, bitmap)
    }

    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
    }
}
