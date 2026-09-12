package com.autocam.hal

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.autocam.engine.ApplyCrop
import com.autocam.engine.CameraEngine
import com.autocam.engine.CameraSession
import com.autocam.engine.CaptureParams
import com.autocam.engine.CompositionGuide
import com.autocam.engine.EngineEvent
import com.autocam.engine.FilterRecommendation
import com.autocam.engine.GuideUser
import com.autocam.engine.OpenSessionRequest
import com.autocam.engine.RangeF
import com.autocam.engine.SessionLens
import com.autocam.engine.SetZoom
import com.autocam.engine.StillResult
import com.autocam.engine.VideoClipResult
import com.autocam.engine.VideoOptions
import com.autocam.engine.VideoSession
import com.autocam.engine.ViewfinderFrame
import com.autocam.engine.ZoomBlendProfile
import com.autocam.flags.FeatureFlags
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class Camera2Engine(
    context: Context,
    private val flags: FeatureFlags,
) : CameraEngine {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraThread = HandlerThread("autocam-camera2").also { it.start() }
    private val handler = Handler(cameraThread.looper)
    private val executor = Executor { handler.post(it) }

    private val lock = Any()
    private var session: CameraSession? = null
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var characteristics: CameraCharacteristics? = null
    private var requestBuilder: RepeatingRequestBuilder? = null
    private var previewSurface: Surface? = null
    private var yuvReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var previewSize: Size = Size(1920, 1080)
    private var params = DEFAULT_PARAMS
    private var zoomRatio = 1f
    private var cameraId: String = "0"
    private val yuvFrames = AtomicInteger(0)
    private val previewFrames = AtomicInteger(0)
    private var sessionSeq = 0

    private val frameFlow = MutableSharedFlow<ViewfinderFrame>(replay = 1, extraBufferCapacity = 256)
    private val guideFlow = MutableSharedFlow<CompositionGuide>(replay = 1, extraBufferCapacity = 256)
    private val filterFlow = MutableSharedFlow<FilterRecommendation>(replay = 1, extraBufferCapacity = 256)
    private val eventFlow = MutableSharedFlow<EngineEvent>(replay = 1, extraBufferCapacity = 256)

    override fun currentSession(): CameraSession? = synchronized(lock) { session }

    override fun attachPreviewSurface(surface: Surface?) {
        synchronized(lock) {
            previewSurface = surface
            val dev = device
            if (surface != null && dev != null && captureSession == null && session != null) {
                handler.post { runCatching { startSessionLocked(dev) } }
            }
        }
    }

    override suspend fun openSession(req: OpenSessionRequest): CameraSession {
        closeSession()
        val id = pickCameraId(req.facing)
        val ch = manager.getCameraCharacteristics(id)
        val opened = openDevice(id)
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("no stream map for $id")
        val tex = map.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        val yuv = map.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
        val jpeg = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val preview = StreamSizePicker.closest(tex, 1920, 1080) ?: Size(1920, 1080)
        val analysis = StreamSizePicker.closest(yuv, 1920, 1080) ?: Size(1280, 720)
        val still = StreamSizePicker.nearestPixels(jpeg, 12_500_000, 4.0 / 3.0)
            ?: StreamSizePicker.closest(jpeg, 4080, 3072)
            ?: Size(1920, 1440)
        val zoomRange = if (Build.VERSION.SDK_INT >= 30) {
            ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
        val built = buildSession(req, id, ch, still)
        synchronized(lock) {
            cameraId = id
            device = opened
            characteristics = ch
            previewSize = preview
            requestBuilder = RepeatingRequestBuilder(ch, flags.halZoomRatio() && zoomRange != null)
            params = params.copy(zoomRatio = 1.0, stillSize = listOf(still.width, still.height))
            zoomRatio = 1f
            session = built
            yuvReader = ImageReader.newInstance(analysis.width, analysis.height, ImageFormat.YUV_420_888, 2).also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    r.acquireLatestImage()?.close()
                    yuvFrames.incrementAndGet()
                }, handler)
            }
            jpegReader = ImageReader.newInstance(still.width, still.height, ImageFormat.JPEG, 1)
        }
        emit("session_open", "info", "event.session_open")
        emit("stream_profile", "info", "event.stream_profile")
        val surface = synchronized(lock) { previewSurface }
        if (surface != null) {
            handler.post { runCatching { startSessionLocked(opened) } }
        }
        return built
    }

    override suspend fun closeSession() {
        synchronized(lock) {
            runCatching { captureSession?.close() }
            runCatching { device?.close() }
            runCatching { yuvReader?.close() }
            runCatching { jpegReader?.close() }
            captureSession = null
            device = null
            yuvReader = null
            jpegReader = null
            characteristics = null
            requestBuilder = null
            session = null
        }
        emit("session_close", "info", "event.session_close")
    }

    override fun setCaptureParams(params: CaptureParams) {
        if (params.aeMode != "off" && (params.iso != null || params.exposureNs != null)) {
            emit("params_rejected", "warn", "event.params_rejected")
            return
        }
        synchronized(lock) {
            this.params = params
            zoomRatio = params.zoomRatio.toFloat()
        }
        updateRepeating()
    }

    override fun setZoom(cmd: SetZoom) {
        synchronized(lock) { zoomRatio = cmd.zoomRatio.toFloat() }
        emit("zoom_cmd_ms", "debug", "event.zoom_cmd_ms")
        updateRepeating()
    }

    override fun setPhysicalLensHint(lensId: String?) {
        emit("lens_hint_ignored", "info", "event.lens_hint_ignored")
    }

    override fun guideUser(cmd: GuideUser) {
        if (cmd.followSuggested) setZoom(SetZoom(cmd.zoomRatio, "immediate", "guide"))
    }

    override fun frames(): Flow<ViewfinderFrame> = frameFlow.asSharedFlow()
    override fun guides(): Flow<CompositionGuide> = guideFlow.asSharedFlow()
    override fun filters(): Flow<FilterRecommendation> = filterFlow.asSharedFlow()
    override fun events(): Flow<EngineEvent> = eventFlow.asSharedFlow()

    override suspend fun captureStill(): StillResult {
        throw IllegalStateException("still capture is PR-09")
    }

    override suspend fun startVideo(opts: VideoOptions): VideoSession {
        throw IllegalStateException("video is PR-10")
    }

    override suspend fun stopVideo(): VideoClipResult {
        throw IllegalStateException("video is PR-10")
    }

    override fun applyCrop(cmd: ApplyCrop): StillResult {
        throw IllegalStateException("applyCrop is PR-15")
    }

    override fun loadDeviceProfile(json: String) = Unit

    override fun saveZoomCalibration(profile: ZoomBlendProfile) = Unit

    private fun pickCameraId(facing: String): String {
        val override = flags.debugCameraId()
        if (override.isNotBlank() && override != "auto") {
            return override
        }
        if (facing == "front") {
            return manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: "1"
        }
        if (flags.halMultiLens()) {
            runCatching {
                val ch = manager.getCameraCharacteristics("4")
                val logical = Build.VERSION.SDK_INT >= 28 && ch.physicalCameraIds.size >= 2
                if (logical) return "4"
            }
        }
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: "0"
    }

    private fun buildSession(
        req: OpenSessionRequest,
        id: String,
        ch: CameraCharacteristics,
        still: Size,
    ): CameraSession {
        sessionSeq += 1
        val zoomRange = if (Build.VERSION.SDK_INT >= 30) {
            ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
        val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(1f)
        val apertures = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val physical = if (Build.VERSION.SDK_INT >= 28) ch.physicalCameraIds.toList().sorted() else emptyList()
        val lenses = if (physical.size >= 2) {
            physical.mapIndexed { index, pid ->
                val pch = runCatching { manager.getCameraCharacteristics(pid) }.getOrNull()
                val f = pch?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                    ?: focals.first()
                val a = pch?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull()
                val role = when (pid) {
                    "2" -> "ultrawide"
                    "3" -> "tele"
                    else -> "main"
                }
                val mainF = 6.68f
                SessionLens(
                    lensId = "physical_$role",
                    physicalCameraId = pid,
                    role = role,
                    focalMm = f.toDouble(),
                    equiv35mm = when (role) {
                        "ultrawide" -> 14.0
                        "tele" -> 120.0
                        else -> 23.0
                    },
                    fNumber = a?.toDouble(),
                    opticalZoom = (f / mainF).toDouble(),
                    hasOis = (pch?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                        ?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true),
                    hasAf = true,
                )
            }
        } else {
            listOf(
                SessionLens(
                    lensId = "physical_main",
                    physicalCameraId = id,
                    role = "main",
                    focalMm = focals.first().toDouble(),
                    equiv35mm = 23.0,
                    fNumber = apertures?.firstOrNull()?.toDouble(),
                    opticalZoom = 1.0,
                    hasOis = true,
                    hasAf = true,
                ),
            )
        }
        val ae = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.map { CaptureParamMapper.aeModeName(it) }
            ?: listOf("on")
        val af = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map { CaptureParamMapper.afModeName(it) }
            ?: listOf("continuous_picture")
        val awb = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.map { CaptureParamMapper.awbModeName(it) }
            ?: listOf("auto")
        val iso = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposure = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val fps = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { listOf(it.lower, it.upper) }
            ?: listOf(listOf(30, 30))
        return CameraSession(
            sessionId = "cam2_${sessionSeq.toString().padStart(2, '0')}",
            logicalCameraId = id,
            facing = req.facing,
            sessionProfile = req.sessionProfile,
            zoomRatioRange = RangeF(
                (zoomRange?.lower ?: 1f).toDouble(),
                (zoomRange?.upper ?: 10f).toDouble(),
            ),
            supportsLogicalMultiCamera = physical.size >= 2,
            supportsControlZoomRatio = zoomRange != null,
            lenses = lenses,
            aeModes = ae.distinct(),
            afModes = af.distinct(),
            awbModes = awb.distinct(),
            isoRange = RangeF(iso?.lower?.toDouble() ?: 50.0, iso?.upper?.toDouble() ?: 6400.0),
            exposureNsRange = RangeF(
                exposure?.lower?.toDouble() ?: 1_000_000.0,
                exposure?.upper?.toDouble() ?: 1_000_000_000.0,
            ),
            fpsRanges = fps,
            stillSizes = listOf(listOf(still.width, still.height)),
            videoSizes = listOf(listOf(1920, 1080)),
            stabilization = listOf("off", "eis", "ois"),
        )
    }

    private fun startSessionLocked(dev: CameraDevice) {
        val preview = previewSurface ?: return
        val yuv = yuvReader ?: return
        val jpeg = jpegReader ?: return
        val outputs = listOf(
            OutputConfiguration(preview),
            OutputConfiguration(yuv.surface),
            OutputConfiguration(jpeg.surface),
        )
        val config = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(lock) { captureSession = session }
                    updateRepeating()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    emit("session_error", "error", "event.session_error")
                }
            },
        )
        dev.createCaptureSession(config)
    }

    private fun updateRepeating() {
        val sess: CameraCaptureSession
        val dev: CameraDevice
        val preview: Surface
        val builderHelper: RepeatingRequestBuilder
        val p: CaptureParams
        val z: Float
        synchronized(lock) {
            sess = captureSession ?: return
            dev = device ?: return
            preview = previewSurface ?: return
            builderHelper = requestBuilder ?: return
            p = params
            z = zoomRatio
        }
        val builder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(preview)
        yuvReader?.surface?.let { builder.addTarget(it) }
        builderHelper.apply(builder, p, z)
        sess.setRepeatingRequest(
            builder.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    emitFrame(result)
                }
            },
            handler,
        )
    }

    private fun emitFrame(result: TotalCaptureResult) {
        val n = previewFrames.incrementAndGet()
        val z = if (Build.VERSION.SDK_INT >= 30) {
            result.get(CaptureResult.CONTROL_ZOOM_RATIO) ?: zoomRatio
        } else {
            zoomRatio
        }
        val physical = if (Build.VERSION.SDK_INT >= 29) {
            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        } else {
            null
        }
        val lensId = when (physical) {
            "2" -> "physical_ultrawide"
            "3" -> "physical_tele"
            else -> "physical_main"
        }
        val frame = ViewfinderFrame(
            timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: System.nanoTime(),
            previewKind = "private",
            previewHandle = "opaque:preview:$n",
            width = previewSize.width,
            height = previewSize.height,
            rotationDeg = 90,
            zoomRatio = z.toDouble(),
            activePhysicalCamera = physical,
            activeLensId = lensId,
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH)?.toDouble(),
        )
        frameFlow.tryEmit(frame)
        if (n == 1) emit("zoom_apply_ms", "debug", "event.zoom_apply_ms")
    }

    private suspend fun openDevice(id: String): CameraDevice {
        return suspendCancellableCoroutine { cont ->
            manager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (cont.isActive) cont.resume(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("camera disconnected"))
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (cont.isActive) {
                            cont.resumeWithException(IllegalStateException("camera_error_$error"))
                        }
                    }
                },
                handler,
            )
        }
    }

    private fun emit(code: String, level: String, key: String) {
        eventFlow.tryEmit(
            EngineEvent(
                timestampNs = System.nanoTime(),
                code = code,
                level = level,
                messageKey = key,
            ),
        )
    }

    companion object {
        val DEFAULT_PARAMS = CaptureParams(
            aeMode = "on",
            afMode = "continuous_picture",
            awbMode = "auto",
            iso = null,
            exposureNs = null,
            evBias = 0.0,
            fps = 30,
            stabilization = "ois",
            zoomRatio = 1.0,
            jpegQuality = 95,
        )
    }
}
