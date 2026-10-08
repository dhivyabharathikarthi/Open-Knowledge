package com.example.data.crypto

import com.example.domain.model.PhotoMetadata
import com.example.util.SecureMemory
import java.util.UUID

data class EncryptedPhotoBundle(
    val photoId: UUID,
    val wrappedPhotoKey: ByteArray, // Per-photo key wrapped with VMK
    val photoKeyNonce: ByteArray,   // Nonce used to wrap per-photo key
    val encryptedPhotoData: ByteArray, // EncryptedObject serialized binary bytes
    val encryptedThumbnailData: ByteArray, // EncryptedObject serialized binary bytes
    val encryptedMetadata: ByteArray, // Encrypted JSON
    val metadataNonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPhotoBundle
        if (photoId != other.photoId) return false
        if (!wrappedPhotoKey.contentEquals(other.wrappedPhotoKey)) return false
        if (!photoKeyNonce.contentEquals(other.photoKeyNonce)) return false
        if (!encryptedPhotoData.contentEquals(other.encryptedPhotoData)) return false
        if (!encryptedThumbnailData.contentEquals(other.encryptedThumbnailData)) return false
        if (!encryptedMetadata.contentEquals(other.encryptedMetadata)) return false
        if (!metadataNonce.contentEquals(other.metadataNonce)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = photoId.hashCode()
        result = 31 * result + wrappedPhotoKey.contentHashCode()
        result = 31 * result + photoKeyNonce.contentHashCode()
        result = 31 * result + encryptedPhotoData.contentHashCode()
        result = 31 * result + encryptedThumbnailData.contentHashCode()
        result = 31 * result + encryptedMetadata.contentHashCode()
        result = 31 * result + metadataNonce.contentHashCode()
        return result
    }
}

object PhotoCipher {

    /**
     * Encrypts a photo, its thumbnail, and its metadata using a unique per-photo key (PKEK).
     * The PKEK is wrapped with the Vault Master Key (VMK).
     */
    fun encryptPhoto(
        photoId: UUID,
        photoBytes: ByteArray,
        thumbnailBytes: ByteArray,
        metadata: PhotoMetadata,
        vmk: ByteArray
    ): EncryptedPhotoBundle {
        val pkek = CryptoEngine.generateRandomKey()
        try {
            // 1. Wrap per-photo key using VMK (AES-256-GCM)
            val (keyNonce, wrappedKey) = CryptoEngine.encrypt(pkek, vmk)

            // 2. Encrypt photo bytes with per-photo key
            val (photoNonce, photoCiphertext) = CryptoEngine.encrypt(photoBytes, pkek)
            val photoObject = EncryptedObject(
                objectId = photoId,
                nonce = photoNonce,
                ciphertext = photoCiphertext
            )
            val serializedPhoto = EncryptedObject.serialize(photoObject)

            // 3. Encrypt thumbnail bytes with per-photo key
            val (thumbNonce, thumbCiphertext) = CryptoEngine.encrypt(thumbnailBytes, pkek)
            val thumbObject = EncryptedObject(
                objectId = photoId,
                nonce = thumbNonce,
                ciphertext = thumbCiphertext
            )
            val serializedThumb = EncryptedObject.serialize(thumbObject)

            // 4. Encrypt metadata JSON
            val metaJsonBytes = metadata.toJson().toByteArray(Charsets.UTF_8)
            val (metaNonce, encryptedMeta) = try {
                CryptoEngine.encrypt(metaJsonBytes, pkek)
            } finally {
                SecureMemory.zeroize(metaJsonBytes)
            }

            return EncryptedPhotoBundle(
                photoId = photoId,
                wrappedPhotoKey = wrappedKey,
                photoKeyNonce = keyNonce,
                encryptedPhotoData = serializedPhoto,
                encryptedThumbnailData = serializedThumb,
                encryptedMetadata = encryptedMeta,
                metadataNonce = metaNonce
            )
        } finally {
            SecureMemory.zeroize(pkek)
        }
    }

    /**
     * Unwraps the per-photo key using the VMK.
     */
    fun unwrapPhotoKey(
        wrappedKey: ByteArray,
        keyNonce: ByteArray,
        vmk: ByteArray
    ): ByteArray {
        return CryptoEngine.decrypt(wrappedKey, vmk, keyNonce)
    }

    /**
     * Decrypts photo or thumbnail bytes from an EncryptedObject container.
     */
    fun decryptPayload(
        encryptedSerializedData: ByteArray,
        wrappedKey: ByteArray,
        keyNonce: ByteArray,
        vmk: ByteArray
    ): ByteArray {
        val pkek = unwrapPhotoKey(wrappedKey, keyNonce, vmk)
        try {
            val encObj = EncryptedObject.deserialize(encryptedSerializedData)
            return CryptoEngine.decrypt(encObj.ciphertext, pkek, encObj.nonce)
        } finally {
            SecureMemory.zeroize(pkek)
        }
    }

    /**
     * Decrypts photo metadata JSON.
     */
    fun decryptMetadata(
        encryptedMetadata: ByteArray,
        metadataNonce: ByteArray,
        wrappedKey: ByteArray,
        keyNonce: ByteArray,
        vmk: ByteArray
    ): PhotoMetadata {
        val pkek = unwrapPhotoKey(wrappedKey, keyNonce, vmk)
        val jsonBytes = try {
            CryptoEngine.decrypt(encryptedMetadata, pkek, metadataNonce)
        } finally {
            SecureMemory.zeroize(pkek)
        }
        val jsonStr = String(jsonBytes, Charsets.UTF_8)
        SecureMemory.zeroize(jsonBytes)
        return PhotoMetadata.fromJson(jsonStr)
    }
}
