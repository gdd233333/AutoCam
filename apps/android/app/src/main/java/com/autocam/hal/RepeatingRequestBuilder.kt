package com.autocam.hal

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import com.autocam.engine.CaptureParams
import kotlin.math.roundToInt

class RepeatingRequestBuilder(
    private val characteristics: CameraCharacteristics,
    private val useZoomRatio: Boolean,
) {
    fun apply(builder: CaptureRequest.Builder, params: CaptureParams, zoomRatio: Float) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureParamMapper.aeMode(params.aeMode))
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureParamMapper.afMode(params.afMode))
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureParamMapper.awbMode(params.awbMode))
        if (params.aeMode == "off") {
            params.iso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            params.exposureNs?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        } else {
            applyEv(builder, params.evBias)
        }
        if (params.afMode == "off") {
            params.focusDistance?.let { builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, it.toFloat()) }
        }
        applyFps(builder, params.fps)
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureParamMapper.oisMode(params.stabilization))
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureParamMapper.eisMode(params.stabilization))
        applyZoom(builder, zoomRatio)
        builder.set(CaptureRequest.JPEG_QUALITY, params.jpegQuality.toByte())
    }

    private fun applyEv(builder: CaptureRequest.Builder, evBias: Double) {
        val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return
        val step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return
        val stepF = step.numerator.toDouble() / step.denominator.toDouble()
        if (stepF == 0.0) return
        val index = (evBias / stepF).roundToInt().coerceIn(range.lower, range.upper)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, index)
    }

    private fun applyFps(builder: CaptureRequest.Builder, fps: Int) {
        val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return
        val exact = ranges.firstOrNull { it.lower == fps && it.upper == fps }
        val containing = ranges.firstOrNull { it.lower <= fps && it.upper >= fps }
        val picked = exact ?: containing ?: ranges.maxByOrNull { it.upper } ?: return
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, picked)
    }

    private fun applyZoom(builder: CaptureRequest.Builder, zoomRatio: Float) {
        if (useZoomRatio && Build.VERSION.SDK_INT >= 30) {
            val range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            val z = if (range != null) zoomRatio.coerceIn(range.lower, range.upper) else zoomRatio
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, z)
            return
        }
        val active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val z = zoomRatio.coerceIn(1f, maxZoom)
        val cx = active.centerX()
        val cy = active.centerY()
        val w = (active.width() / z).toInt().coerceAtLeast(2)
        val h = (active.height() / z).toInt().coerceAtLeast(2)
        builder.set(
            CaptureRequest.SCALER_CROP_REGION,
            android.graphics.Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
        )
    }
}
