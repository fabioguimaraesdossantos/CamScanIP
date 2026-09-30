package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY data_cadastro DESC")
    fun getAllCameras(): Flow<List<CameraEntity>>

    @Query("SELECT * FROM cameras WHERE bssid_wifi = :bssid ORDER BY data_cadastro DESC")
    fun getCamerasByBssid(bssid: String): Flow<List<CameraEntity>>

    @Query("SELECT * FROM cameras WHERE id = :id LIMIT 1")
    fun getCameraById(id: Long): Flow<CameraEntity?>

    @Query("SELECT * FROM cameras WHERE ip_local = :ip LIMIT 1")
    suspend fun findCameraByIp(ip: String): CameraEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: CameraEntity): Long

    @Update
    suspend fun updateCamera(camera: CameraEntity)

    @Delete
    suspend fun deleteCamera(camera: CameraEntity)

    @Query("DELETE FROM cameras WHERE id = :id")
    suspend fun deleteCameraById(id: Long)
}
