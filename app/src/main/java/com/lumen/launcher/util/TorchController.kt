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

    fun toggle(): Boolean = set(!on)

    fun set(enabled: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            manager?.setTorchMode(id, enabled)
            on = enabled
            on
        } catch (_: Exception) {
            on = false
            false
        }
    }
}
