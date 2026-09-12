package com.autocam.hal

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest

object CaptureParamMapper {
    fun aeMode(wire: String): Int {
        return when (wire) {
            "off" -> CaptureRequest.CONTROL_AE_MODE_OFF
            "on_auto_flash" -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            else -> CaptureRequest.CONTROL_AE_MODE_ON
        }
    }

    fun afMode(wire: String): Int {
        return when (wire) {
            "off" -> CaptureRequest.CONTROL_AF_MODE_OFF
            "auto" -> CaptureRequest.CONTROL_AF_MODE_AUTO
            "continuous_video" -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            else -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }
    }

    fun awbMode(wire: String): Int {
        return when (wire) {
            "off" -> CaptureRequest.CONTROL_AWB_MODE_OFF
            "incandescent" -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
            "daylight" -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
            "cloudy" -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            else -> CaptureRequest.CONTROL_AWB_MODE_AUTO
        }
    }

    fun oisMode(wire: String): Int {
        return if (wire == "ois") {
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
        } else {
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
        }
    }

    fun eisMode(wire: String): Int {
        return if (wire == "eis") {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        } else {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        }
    }

    fun aeModeName(value: Int): String {
        return when (value) {
            CameraMetadata.CONTROL_AE_MODE_OFF -> "off"
            CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH -> "on_auto_flash"
            else -> "on"
        }
    }

    fun afModeName(value: Int): String {
        return when (value) {
            CameraMetadata.CONTROL_AF_MODE_OFF -> "off"
            CameraMetadata.CONTROL_AF_MODE_AUTO -> "auto"
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "continuous_video"
            else -> "continuous_picture"
        }
    }

    fun awbModeName(value: Int): String {
        return when (value) {
            CameraMetadata.CONTROL_AWB_MODE_OFF -> "off"
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "incandescent"
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "daylight"
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "cloudy"
            else -> "auto"
        }
    }
}
