package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encrypted_photos")
data class EncryptedPhotoEntity(
    @PrimaryKey
    val id: String,
    val relativeObjectPath: String,
    val relativeThumbnailPath: String,
    val wrappedPhotoKey: ByteArray,
    val photoKeyNonce: ByteArray,
    val encryptedMetadata: ByteArray,
    val metadataNonce: ByteArray,
    val algorithmVersion: Int = 1,
    val keyVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPhotoEntity
        if (id != other.id) return false
        if (relativeObjectPath != other.relativeObjectPath) return false
        if (relativeThumbnailPath != other.relativeThumbnailPath) return false
        if (!wrappedPhotoKey.contentEquals(other.wrappedPhotoKey)) return false
        if (!photoKeyNonce.contentEquals(other.photoKeyNonce)) return false
        if (!encryptedMetadata.contentEquals(other.encryptedMetadata)) return false
        if (!metadataNonce.contentEquals(other.metadataNonce)) return false
        if (algorithmVersion != other.algorithmVersion) return false
        if (keyVersion != other.keyVersion) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (isFavorite != other.isFavorite) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + relativeObjectPath.hashCode()
        result = 31 * result + relativeThumbnailPath.hashCode()
        result = 31 * result + wrappedPhotoKey.contentHashCode()
        result = 31 * result + photoKeyNonce.contentHashCode()
        result = 31 * result + encryptedMetadata.contentHashCode()
        result = 31 * result + metadataNonce.contentHashCode()
        result = 31 * result + algorithmVersion
        result = 31 * result + keyVersion
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isFavorite.hashCode()
        return result
    }
}
