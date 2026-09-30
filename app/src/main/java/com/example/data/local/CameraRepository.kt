package com.example.data.local

import kotlinx.coroutines.flow.Flow

class CameraRepository(private val cameraDao: CameraDao) {

    val allCameras: Flow<List<CameraEntity>> = cameraDao.getAllCameras()

    fun getCamerasForCurrentWifi(bssid: String): Flow<List<CameraEntity>> {
        return if (bssid.isBlank()) {
            cameraDao.getAllCameras()
        } else {
            cameraDao.getCamerasByBssid(bssid)
        }
    }

    suspend fun insertCamera(camera: CameraEntity): Long {
        return cameraDao.insertCamera(camera)
    }

    suspend fun updateCamera(camera: CameraEntity) {
        cameraDao.updateCamera(camera)
    }

    suspend fun deleteCamera(camera: CameraEntity) {
        cameraDao.deleteCamera(camera)
    }

    suspend fun deleteCameraById(id: Long) {
        cameraDao.deleteCameraById(id)
    }

    suspend fun findCameraByIp(ip: String): CameraEntity? {
        return cameraDao.findCameraByIp(ip)
    }
}
