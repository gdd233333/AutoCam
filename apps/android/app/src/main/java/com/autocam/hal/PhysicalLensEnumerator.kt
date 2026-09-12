package com.autocam.hal

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
class PhysicalLensEnumerator(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun publicIds(): List<String> = manager.cameraIdList.toList()

    fun enumerate(): List<CameraNodeDump> {
        return publicIds().map { id -> dumpCamera(id) }
    }

    fun dumpCamera(id: String): CameraNodeDump {
        val ch = manager.getCameraCharacteristics(id)
        val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "back"
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            else -> "unknown"
        }
        val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.map { capabilityName(it) }
            ?.sorted()
            ?: emptyList()
        val physicalIds = if (Build.VERSION.SDK_INT >= 28) {
            ch.physicalCameraIds.toList().sorted()
        } else {
            emptyList()
        }
        val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.map { it.toDouble() }
            ?: emptyList()
        val apertures = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.map { it.toDouble() }
            ?: emptyList()
        val zoomRange = if (Build.VERSION.SDK_INT >= 30) {
            ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                MinMax(it.lower.toDouble(), it.upper.toDouble())
            }
        } else {
            null
        }
        val pixel = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val active = ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpeg = map?.getOutputSizes(ImageFormat.JPEG)?.map { listOf(it.width, it.height) } ?: emptyList()
        val yuv = map?.getOutputSizes(ImageFormat.YUV_420_888)?.map { listOf(it.width, it.height) } ?: emptyList()
        val priv = map?.getOutputSizes(ImageFormat.PRIVATE)?.map { listOf(it.width, it.height) } ?: emptyList()
        val tex = map?.getOutputSizes(SurfaceTexture::class.java)?.map { listOf(it.width, it.height) } ?: emptyList()
        val pixelMax = if (Build.VERSION.SDK_INT >= 31) {
            ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)
        } else {
            null
        }
        val jpegMaxRes = if (Build.VERSION.SDK_INT >= 31) {
            ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.map { listOf(it.width, it.height) }
                ?: emptyList()
        } else {
            emptyList()
        }
        val hasLogical = caps.contains("LOGICAL_MULTI_CAMERA")
        val hasZoomRatio = zoomRange != null
        return CameraNodeDump(
            cameraId = id,
            facing = facing,
            hardwareLevel = level,
            capabilities = caps,
            physicalCameraIds = physicalIds,
            focalLengthsMm = focals,
            apertures = apertures,
            zoomRatioRange = zoomRange,
            sensorPixelArray = pixel?.let { listOf(it.width, it.height) },
            sensorActiveArray = active?.let { listOf(it.width(), it.height()) },
            hasControlZoomRatio = hasZoomRatio,
            hasLogicalMultiCamera = hasLogical,
            jpegSizes = jpeg,
            yuv420888Sizes = yuv,
            privateSizes = priv,
            surfaceTextureSizes = tex,
            vendorTags = vendorTags(ch),
            pixelArrayMaxRes = pixelMax?.let { listOf(it.width, it.height) },
            jpegMaxResSizes = jpegMaxRes,
            hasUltraHighResCapability = caps.contains("ULTRA_HIGH_RESOLUTION_SENSOR"),
        )
    }

    private fun vendorTags(ch: CameraCharacteristics): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (key in ch.keys) {
            val name = key.name ?: continue
            if (!name.contains("xiaomi", ignoreCase = true) && !name.startsWith("com.xiaomi")) {
                continue
            }
            out[name] = stringify(ch.get(key))
        }
        for (name in VENDOR_INT_ARRAY_KEYS) {
            if (out.containsKey(name)) continue
            val value = readVendorIntArray(ch, name) ?: continue
            out[name] = stringify(value)
        }
        for (name in VENDOR_BYTE_KEYS) {
            if (out.containsKey(name)) continue
            val value = readVendorByte(ch, name)
            if (value != null) out[name] = value.toString()
        }
        return out
    }

    companion object {
        val VENDOR_INT_ARRAY_KEYS = listOf(
            "xiaomi.scaler.availableStreamConfigurations",
            "com.xiaomi.scaler.availableStreamConfigurations",
        )
        val VENDOR_BYTE_KEYS = listOf(
            "com.xiaomi.miCam.sensorInfo.qcfaSupported",
            "com.xiaomi.control.qcfa.isSuperRemosaic",
            "xiaomi.control.qcfa.isSuperRemosaic",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun readVendorIntArray(ch: CameraCharacteristics, name: String): IntArray? {
        return try {
            val key = CameraCharacteristics.Key(name, IntArray::class.java)
            ch.get(key)
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readVendorByte(ch: CameraCharacteristics, name: String): Byte? {
        return try {
            val key = CameraCharacteristics.Key(name, Byte::class.javaObjectType)
            ch.get(key)
        } catch (_: Throwable) {
            try {
                val key = CameraCharacteristics.Key(name, ByteArray::class.java)
                ch.get(key)?.firstOrNull()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun stringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
            is IntArray -> value.joinToString(prefix = "[", postfix = "]")
            is LongArray -> value.joinToString(prefix = "[", postfix = "]")
            is DoubleArray -> value.joinToString(prefix = "[", postfix = "]")
            is ByteArray -> value.take(32).joinToString(prefix = "[", postfix = "]")
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
            else -> value.toString()
        }
    }

    private fun capabilityName(value: Int): String {
        return when (value) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "OFFLINE_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> "REMOSAIC_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT -> "DYNAMIC_RANGE_TEN_BIT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE -> "STREAM_USE_CASE"
            else -> "CAP_$value"
        }
    }
}
