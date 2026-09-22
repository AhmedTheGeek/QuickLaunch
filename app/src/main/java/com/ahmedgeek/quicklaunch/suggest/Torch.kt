package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.ahmedgeek.quicklaunch.Bg
import com.ahmedgeek.quicklaunch.QuickLaunchApp

/** The rear flash as a torch. setTorchMode needs no permission; the callback keeps [on] current. */
class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)

    /** Looked up on first use: listing cameras is a binder round trip or two. */
    private val cameraId: String? by lazy {
        try {
            cameras?.cameraIdList?.firstOrNull { id ->
                val c = cameras.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.w(QuickLaunchApp.TAG, "no torch", e)
            null
        }
    }

    @Volatile
    var on = false
        private set
    private var listening = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    /** Main thread. False on devices without a rear flash. */
    fun available(): Boolean {
        if (cameraId == null) return false
        if (!listening) {
            listening = true
            cameras.registerTorchCallback(callback, Bg.main)
        }
        return true
    }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            cameras.setTorchMode(id, !on)
            true
        } catch (e: CameraAccessException) {
            Log.w(QuickLaunchApp.TAG, "torch busy", e)
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
