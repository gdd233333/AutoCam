package com.autocam.hal

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Brute-force Camera2 ids that getCameraIdList() hides (Xiaomi maps UW/Tele to "5"/"6").
 */
class ExtraIdProbe(
    private val manager: CameraManager,
    private val enumerator: PhysicalLensEnumerator,
) {
    fun probe(maxId: Int = 15): List<ExtraIdDump> {
        val public = enumerator.publicIds().toSet()
        return (0..maxId).map { it.toString() }.map { id -> probeOne(id, id in public) }
    }

    private fun probeOne(id: String, inPublic: Boolean): ExtraIdDump {
        val node = try {
            enumerator.dumpCamera(id)
        } catch (t: Throwable) {
            return ExtraIdDump(
                cameraId = id,
                inPublicList = inPublic,
                characteristicsOk = false,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
        val open = if (inPublic) {
            null
        } else {
            tryOpen(id)
        }
        return ExtraIdDump(
            cameraId = id,
            inPublicList = inPublic,
            characteristicsOk = true,
            openOk = open?.first,
            error = open?.second,
            node = node,
        )
    }

    private fun tryOpen(id: String): Pair<Boolean, String?> {
        val thread = HandlerThread("extra-id-$id").also { it.start() }
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        var opened: CameraDevice? = null
        var error: String? = null
        return try {
            manager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        opened = camera
                        latch.countDown()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        error = "disconnected"
                        latch.countDown()
                    }

                    override fun onError(camera: CameraDevice, errorCode: Int) {
                        camera.close()
                        error = "camera_error_$errorCode"
                        latch.countDown()
                    }
                },
                handler,
            )
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return false to "open timeout"
            }
            val device = opened
            if (device != null) {
                runCatching { device.close() }
                true to null
            } else {
                false to (error ?: "open failed")
            }
        } catch (t: Throwable) {
            false to (t.message ?: t.javaClass.simpleName)
        } finally {
            thread.quitSafely()
        }
    }
}
