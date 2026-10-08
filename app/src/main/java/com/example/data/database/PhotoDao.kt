package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM encrypted_photos ORDER BY createdAt DESC")
    fun getAllPhotos(): Flow<List<EncryptedPhotoEntity>>

    @Query("SELECT * FROM encrypted_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): EncryptedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: EncryptedPhotoEntity)

    @Query("DELETE FROM encrypted_photos WHERE id = :id")
    suspend fun deletePhoto(id: String): Int

    @Query("DELETE FROM encrypted_photos")
    suspend fun deleteAllPhotos(): Int

    @Query("SELECT COUNT(*) FROM encrypted_photos")
    suspend fun getPhotoCount(): Int

    @Query("UPDATE encrypted_photos SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAt: Long)
}
