package com.lumen.launcher.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class TorchController(context: Context) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = manager?.cameraIdList?.firstOrNull { id ->
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    var on: Boolean = false
        private set

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            on = !on
            manager?.setTorchMode(id, on)
            on
        } catch (_: Exception) {
            on = false
            false
        }
    }
}
