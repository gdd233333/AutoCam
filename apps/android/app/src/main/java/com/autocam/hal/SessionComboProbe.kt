package com.autocam.hal

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class SessionComboProbe(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun probe(node: CameraNodeDump): List<SessionComboDump> {
        if (node.facing != "back") return emptyList()
        val ch = manager.getCameraCharacteristics(node.cameraId)
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
        val tex = map.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        val yuv = map.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
        val jpeg = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

        val preview = StreamSizePicker.closest(tex, 1920, 1080) ?: return emptyList()
        val analysis = StreamSizePicker.atMostPixels(yuv, 1920 * 1080, 16.0 / 9.0)
            ?: StreamSizePicker.closest(yuv, 1280, 720)
        val still = StreamSizePicker.nearestPixels(jpeg, 12_500_000, 4.0 / 3.0)
            ?: StreamSizePicker.closest(jpeg, 4096, 3072)
        val video = StreamSizePicker.closest(tex, 1920, 1080) ?: preview
        val jpegMax = jpeg.maxByOrNull { it.width.toLong() * it.height }

        val results = mutableListOf<SessionComboDump>()
        if (analysis != null && still != null) {
            results += probeProfile(
                cameraId = node.cameraId,
                profile = "A_STILL",
                outputs = listOf(
                    OutputSpec("PRIVATE", preview, null, "preview"),
                    OutputSpec("YUV_420_888", analysis, null, "analysis"),
                    OutputSpec("JPEG", still, null, "still"),
                ),
            )
        }
        if (jpegMax != null && analysis != null) {
            results += probeProfile(
                cameraId = node.cameraId,
                profile = "A_STILL_JPEG_MAX",
                outputs = listOf(
                    OutputSpec("PRIVATE", preview, null, "preview"),
                    OutputSpec("YUV_420_888", analysis, null, "analysis"),
                    OutputSpec("JPEG", jpegMax, null, "still_max"),
                ),
            )
        }
        results += probeProfile(
            cameraId = node.cameraId,
            profile = "B_VIDEO",
            outputs = listOfNotNull(
                OutputSpec("PRIVATE", preview, null, "preview"),
                OutputSpec("PRIVATE", video, null, "record"),
                analysis?.let { OutputSpec("YUV_420_888", it, null, "analysis") },
            ),
        )
        results += probeProfile(
            cameraId = node.cameraId,
            profile = "B_VIDEO_NO_ANALYSIS",
            outputs = listOf(
                OutputSpec("PRIVATE", preview, null, "preview"),
                OutputSpec("PRIVATE", video, null, "record"),
            ),
        )
        if (still != null) {
            results += probeProfile(
                cameraId = node.cameraId,
                profile = "C_DEGRADED_STILL",
                outputs = listOf(
                    OutputSpec("PRIVATE", preview, null, "preview"),
                    OutputSpec("JPEG", still, null, "still"),
                ),
            )
        }
        results += probeProfile(
            cameraId = node.cameraId,
            profile = "C_DEGRADED_VIDEO",
            outputs = listOf(
                OutputSpec("PRIVATE", preview, null, "preview"),
                OutputSpec("PRIVATE", video, null, "record"),
            ),
        )

        val physical = node.physicalCameraIds
        if (physical.size >= 2 && analysis != null) {
            results += probeProfile(
                cameraId = node.cameraId,
                profile = "DUAL_PHYSICAL_YUV",
                outputs = listOf(
                    OutputSpec("PRIVATE", preview, null, "preview"),
                    OutputSpec("YUV_420_888", analysis, physical[0], "physical_a"),
                    OutputSpec("YUV_420_888", analysis, physical[1], "physical_b"),
                ),
            )
        }
        return results
    }

    private fun probeProfile(
        cameraId: String,
        profile: String,
        outputs: List<OutputSpec>,
    ): SessionComboDump {
        val dumpOutputs = outputs.map {
            SessionOutputDump(it.format, it.size.width, it.size.height, it.physicalId, it.role)
        }
        val resources = mutableListOf<AutoCloseable>()
        return try {
            val configs = outputs.map { spec ->
                val surface = createSurface(spec, resources)
                OutputConfiguration(surface).also { oc ->
                    if (spec.physicalId != null && Build.VERSION.SDK_INT >= 28) {
                        oc.setPhysicalCameraId(spec.physicalId)
                    }
                }
            }
            val executor = Executor { it.run() }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                configs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) = Unit
                    override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                },
            )
            val setup = probeViaDeviceSetup(cameraId, sessionConfig)
            if (setup != null) {
                SessionComboDump(cameraId, profile, dumpOutputs, setup, "CameraDeviceSetup")
            } else {
                val opened = probeViaOpenDevice(cameraId, sessionConfig)
                SessionComboDump(cameraId, profile, dumpOutputs, opened.supported, opened.api, opened.error)
            }
        } catch (t: Throwable) {
            SessionComboDump(cameraId, profile, dumpOutputs, null, "exception", t.message)
        } finally {
            resources.forEach { runCatching { it.close() } }
        }
    }

    private fun createSurface(spec: OutputSpec, resources: MutableList<AutoCloseable>): Surface {
        return when (spec.format) {
            "JPEG" -> {
                val reader = ImageReader.newInstance(spec.size.width, spec.size.height, ImageFormat.JPEG, 1)
                resources += AutoCloseable { reader.close() }
                reader.surface
            }
            "YUV_420_888" -> {
                val reader = ImageReader.newInstance(
                    spec.size.width,
                    spec.size.height,
                    ImageFormat.YUV_420_888,
                    2,
                )
                resources += AutoCloseable { reader.close() }
                reader.surface
            }
            else -> {
                val texture = SurfaceTexture(spec.role.hashCode() and 0x7fffffff)
                texture.setDefaultBufferSize(spec.size.width, spec.size.height)
                val surface = Surface(texture)
                resources += AutoCloseable {
                    surface.release()
                    texture.release()
                }
                surface
            }
        }
    }

    private fun probeViaDeviceSetup(cameraId: String, config: SessionConfiguration): Boolean? {
        if (Build.VERSION.SDK_INT < 35) return null
        return try {
            val setup = CameraManager::class.java
                .getMethod("getCameraDeviceSetup", String::class.java)
                .invoke(manager, cameraId) ?: return null
            setup.javaClass
                .getMethod("isSessionConfigurationSupported", SessionConfiguration::class.java)
                .invoke(setup, config) as Boolean
        } catch (_: Throwable) {
            null
        }
    }

    private fun probeViaOpenDevice(cameraId: String, config: SessionConfiguration): OpenProbe {
        val thread = HandlerThread("hal-dump-$cameraId").also { it.start() }
        val handler = Handler(thread.looper)
        return try {
            val device = openCamera(cameraId, handler)
            try {
                val supported = if (Build.VERSION.SDK_INT >= 29) {
                    device.isSessionConfigurationSupported(config)
                } else {
                    null
                }
                OpenProbe(supported, "CameraDevice.isSessionConfigurationSupported", null)
            } finally {
                runCatching { device.close() }
            }
        } catch (t: Throwable) {
            OpenProbe(null, "CameraDevice.open", t.message)
        } finally {
            thread.quitSafely()
        }
    }

    private fun openCamera(cameraId: String, handler: Handler): CameraDevice {
        val latch = CountDownLatch(1)
        var opened: CameraDevice? = null
        var error: String? = null
        manager.openCamera(
            cameraId,
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
        if (!latch.await(8, TimeUnit.SECONDS)) {
            throw IllegalStateException("openCamera timeout id=$cameraId")
        }
        return opened ?: throw IllegalStateException(error ?: "openCamera failed id=$cameraId")
    }

    private data class OutputSpec(
        val format: String,
        val size: Size,
        val physicalId: String?,
        val role: String,
    )

    private data class OpenProbe(
        val supported: Boolean?,
        val api: String,
        val error: String?,
    )
}
