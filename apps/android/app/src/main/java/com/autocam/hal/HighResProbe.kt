package com.autocam.hal

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

class HighResProbe(
    private val manager: CameraManager,
    private val sessions: SessionComboProbe,
) {
    fun probe(cameraIds: List<String>): List<HighResDump> {
        return cameraIds.map { id -> probeOne(id) }
    }

    private fun probeOne(id: String): HighResDump {
        val ch = manager.getCameraCharacteristics(id)
        val streamCfg = readIntArray(ch, "xiaomi.scaler.availableStreamConfigurations")
            ?: readIntArray(ch, "com.xiaomi.scaler.availableStreamConfigurations")
        val jpeg50 = parseVendorJpeg(streamCfg).filter { it[0].toLong() * it[1] >= 40_000_000 }
        val qcfa = readByte(ch, "com.xiaomi.miCam.sensorInfo.qcfaSupported")
            ?: readByte(ch, "xiaomi.miCam.sensorInfo.qcfaSupported")
        val remosaicKey = hasRequestKey(ch, "com.xiaomi.control.qcfa.isSuperRemosaic") ||
            hasRequestKey(ch, "xiaomi.control.qcfa.isSuperRemosaic")
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val preview = map?.getOutputSizes(SurfaceTexture::class.java)
            ?.let { StreamSizePicker.closest(it, 1920, 1080) }
            ?: Size(1920, 1080)
        val combos = mutableListOf<SessionComboDump>()
        for (size in listOf(Size(8192, 6144), Size(8160, 6144))) {
            combos += sessions.probeNamed(
                cameraId = id,
                profile = "JPEG_50MP_${size.width}x${size.height}",
                outputs = listOf(
                    SessionComboProbe.OutputSpec("PRIVATE", preview, null, "preview"),
                    SessionComboProbe.OutputSpec("JPEG", size, null, "still_50mp"),
                ),
            )
            combos += sessions.probeNamed(
                cameraId = id,
                profile = "JPEG_50MP_ONLY_${size.width}x${size.height}",
                outputs = listOf(
                    SessionComboProbe.OutputSpec("JPEG", size, null, "still_50mp"),
                ),
            )
        }
        return HighResDump(
            cameraId = id,
            qcfaSupported = qcfa?.toString(),
            vendorJpeg50mp = jpeg50,
            vendorStreamTagReadable = streamCfg != null,
            remosaicRequestKeyPresent = remosaicKey,
            combos = combos,
        )
    }

    private fun parseVendorJpeg(cfg: IntArray?): List<List<Int>> {
        if (cfg == null || cfg.size < 4) return emptyList()
        val out = linkedSetOf<List<Int>>()
        var i = 0
        while (i + 3 < cfg.size) {
            val format = cfg[i]
            val w = cfg[i + 1]
            val h = cfg[i + 2]
            if (format == ImageFormat.JPEG || format == 32) {
                out += listOf(w, h)
            }
            i += 4
        }
        return out.toList()
    }

    private fun hasRequestKey(ch: CameraCharacteristics, name: String): Boolean {
        return ch.availableCaptureRequestKeys.any { it.name == name }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readIntArray(ch: CameraCharacteristics, name: String): IntArray? {
        return try {
            ch.get(CameraCharacteristics.Key(name, IntArray::class.java))
        } catch (_: Throwable) {
            null
        }
    }

    private fun readByte(ch: CameraCharacteristics, name: String): Byte? {
        return try {
            ch.get(CameraCharacteristics.Key(name, Byte::class.javaObjectType))
        } catch (_: Throwable) {
            try {
                ch.get(CameraCharacteristics.Key(name, ByteArray::class.java))?.firstOrNull()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
