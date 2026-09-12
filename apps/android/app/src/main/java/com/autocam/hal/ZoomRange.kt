package com.autocam.hal

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Range

/**
 * Third-party [CaptureRequest.CONTROL_ZOOM_RATIO] must stay inside the public
 * [CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE]. Xiaomi SAT logical id 4
 * advertises vendor `xiaomi.smoothTransition.xiaomiSatMaxZoom=120` (system-camera
 * 100x+), but sending 120 as CONTROL_ZOOM_RATIO is outside the public 1–10 range
 * and can kill the HAL.
 */
object ZoomRange {
    const val SAT_MAX_TAG: String = "xiaomi.smoothTransition.xiaomiSatMaxZoom"
    const val VIDEO_SAT_RANGE_TAG: String = "com.xiaomi.camera.videosat.zoomRange"

    fun publicRange(ch: CameraCharacteristics): Range<Float>? {
        if (Build.VERSION.SDK_INT < 30) return null
        return ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
    }

    fun vendorSatMax(ch: CameraCharacteristics): Float? {
        readFloat(ch, SAT_MAX_TAG)?.let { return it }
        val video = readFloatArray(ch, VIDEO_SAT_RANGE_TAG)
        if (video != null && video.size >= 2) return video[1]
        return null
    }

    fun clampForRequest(requested: Float, publicMin: Float, publicMax: Float): Float {
        if (publicMin > publicMax) return requested
        return requested.coerceIn(publicMin, publicMax)
    }

    fun clampForRequest(requested: Float, publicRange: Range<Float>?): Float {
        if (publicRange == null) return requested.coerceAtLeast(1f)
        return clampForRequest(requested, publicRange.lower, publicRange.upper)
    }

    private fun readFloat(ch: CameraCharacteristics, name: String): Float? {
        return try {
            ch.get(CameraCharacteristics.Key(name, Float::class.javaObjectType))
        } catch (_: Throwable) {
            readFloatArray(ch, name)?.firstOrNull()
        }
    }

    private fun readFloatArray(ch: CameraCharacteristics, name: String): FloatArray? {
        return try {
            ch.get(CameraCharacteristics.Key(name, FloatArray::class.java))
        } catch (_: Throwable) {
            null
        }
    }
}
