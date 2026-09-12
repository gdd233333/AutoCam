package com.autocam.mock

import com.autocam.engine.ApplyCrop
import com.autocam.engine.BoxNorm
import com.autocam.engine.CameraEngine
import com.autocam.engine.CameraSession
import com.autocam.engine.CaptureParams
import com.autocam.engine.CompositionGuide
import com.autocam.engine.EngineEvent
import com.autocam.engine.FilterRecommendation
import com.autocam.engine.GuideUser
import com.autocam.engine.OpenSessionRequest
import com.autocam.engine.OverlayPrimitive
import com.autocam.engine.RangeF
import com.autocam.engine.SessionLens
import com.autocam.engine.SetZoom
import com.autocam.engine.StillResult
import com.autocam.engine.VideoClipResult
import com.autocam.engine.VideoOptions
import com.autocam.engine.VideoSession
import com.autocam.engine.ViewfinderFrame
import com.autocam.engine.ZoomBlendBreakpoint
import com.autocam.engine.ZoomBlendProfile
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * In-process camera. `engine.mock=true` for Ship 0 tests. Fake clock only.
 */
class MockCameraEngine : CameraEngine {
    var timeNs: Long = 0L
        private set
    var stepPerFrame: Double = 0.25

    private var session: CameraSession? = null
    private var sessionSeq = 0
    private var frameSeq = 0
    private var aiGuide: String = "off"
    private var zoomRatio: Double = 1.0
    private var activeLensId: String = LENS_MAIN
    private var pendingLens: String? = null
    private var pendingCount: Int = 0
    private var fadeRemaining: Int = 0
    private var zoomPendingApply: Boolean = false
    private var physicalLensHint: String? = null
    private var recording: VideoSession? = null
    private var recordStartedNs: Long = 0L
    private var lastStill: StillResult? = null

    private var subjectNx: Double = 0.5
    private var subjectNy: Double = 0.5
    private var panNx: Double = 0.0
    private var panNy: Double = 0.0
    private var guiding: Boolean = false
    private var subjectBox: BoxNorm = BoxNorm(0.06, 0.04, 0.94, 0.90)
    private var compositionClass: String = "thirds"
    private var targetNx: Double = 0.333
    private var targetNy: Double = 0.333
    private var suggestedZoom: Double = 1.8

    private var blend = DEFAULT_BLEND
    private var params = DEFAULT_PARAMS

    private val frameLog = mutableListOf<ViewfinderFrame>()
    private val guideLog = mutableListOf<CompositionGuide>()
    private val filterLog = mutableListOf<FilterRecommendation>()
    private val eventLog = mutableListOf<EngineEvent>()

    private val frameFlow = MutableSharedFlow<ViewfinderFrame>(replay = 1, extraBufferCapacity = 256)
    private val guideFlow = MutableSharedFlow<CompositionGuide>(replay = 1, extraBufferCapacity = 256)
    private val filterFlow = MutableSharedFlow<FilterRecommendation>(replay = 1, extraBufferCapacity = 256)
    private val eventFlow = MutableSharedFlow<EngineEvent>(replay = 1, extraBufferCapacity = 256)

    fun frameLog(): List<ViewfinderFrame> = frameLog.toList()
    fun guideLog(): List<CompositionGuide> = guideLog.toList()
    fun filterLog(): List<FilterRecommendation> = filterLog.toList()
    fun eventLog(): List<EngineEvent> = eventLog.toList()
    fun subjectCenter(): Pair<Double, Double> = subjectNx to subjectNy
    override fun currentSession(): CameraSession? = session

    fun setSubject(nx: Double, ny: Double) {
        subjectNx = nx.coerceIn(0.0, 1.0)
        subjectNy = ny.coerceIn(0.0, 1.0)
    }

    fun loadFixture(
        subjectBox: BoxNorm,
        compositionClass: String,
        targetNx: Double,
        targetNy: Double,
        suggestedZoom: Double,
    ) {
        this.subjectBox = subjectBox
        this.compositionClass = compositionClass
        this.targetNx = targetNx
        this.targetNy = targetNy
        this.suggestedZoom = suggestedZoom
        setSubject((subjectBox.x0 + subjectBox.x1) / 2.0, (subjectBox.y0 + subjectBox.y1) / 2.0)
    }

    fun advanceNs(delta: Long) {
        require(delta >= 0) { "advanceNs must be >= 0" }
        val target = timeNs + delta
        while (timeNs + FRAME_NS <= target) {
            tickFrame()
        }
        timeNs = target
    }

    fun tickFrame() {
        timeNs += FRAME_NS
        if (guiding) {
            subjectNx = (subjectNx + panNx * stepPerFrame).coerceIn(0.0, 1.0)
            subjectNy = (subjectNy + panNy * stepPerFrame).coerceIn(0.0, 1.0)
        }
        val desired = desiredLens(zoomRatio, activeLensId)
        if (desired != activeLensId) {
            if (pendingLens == desired) {
                pendingCount += 1
            } else {
                pendingLens = desired
                pendingCount = 1
            }
            if (pendingCount >= blend.hysteresisFrames) {
                val from = activeLensId
                activeLensId = desired
                pendingLens = null
                pendingCount = 0
                fadeRemaining = blend.fadeFrames
                emitEvent("lens_switch", "info", "event.lens_switch") {
                    put("fromLensId", from)
                    put("toLensId", activeLensId)
                }
            }
        } else {
            pendingLens = null
            pendingCount = 0
        }
        if (fadeRemaining > 0) {
            emitEvent("freeze_fade", "info", "event.freeze_fade")
            fadeRemaining -= 1
        }
        pushFrame()
        maybePushGuide()
    }

    override suspend fun openSession(req: OpenSessionRequest): CameraSession {
        sessionSeq += 1
        aiGuide = req.aiGuide
        zoomRatio = 1.0
        activeLensId = LENS_MAIN
        pendingLens = null
        pendingCount = 0
        fadeRemaining = 0
        guiding = false
        panNx = 0.0
        panNy = 0.0
        val opened = MOCK_SESSION.copy(
            sessionId = "sess_${sessionSeq.toString().padStart(2, '0')}",
            facing = req.facing,
            sessionProfile = req.sessionProfile,
        )
        session = opened
        emitEvent("session_open", "info", "event.session_open")
        pushFrame()
        maybePushGuide()
        return opened
    }

    override suspend fun closeSession() {
        session = null
        recording = null
        emitEvent("session_close", "info", "event.session_close")
    }

    override fun setCaptureParams(params: CaptureParams) {
        ensureSession()
        if (params.aeMode != "off" && (params.iso != null || params.exposureNs != null)) {
            emitEvent("params_rejected", "warn", "event.params_rejected")
            return
        }
        this.params = params
        zoomRatio = params.zoomRatio
        pushFrame()
    }

    override fun setZoom(cmd: SetZoom) {
        ensureSession()
        zoomRatio = cmd.zoomRatio
        zoomPendingApply = true
        emitEvent("zoom_cmd_ms", "debug", "event.zoom_cmd_ms")
        pushFrame()
    }

    override fun setPhysicalLensHint(lensId: String?) {
        ensureSession()
        physicalLensHint = lensId
        if (lensId != null && lensId != activeLensId) {
            emitEvent("lens_hint_ignored", "info", "event.lens_hint_ignored")
        }
    }

    override fun guideUser(cmd: GuideUser) {
        ensureSession()
        panNx = cmd.panNx
        panNy = cmd.panNy
        guiding = true
        if (cmd.followSuggested) {
            zoomRatio = cmd.zoomRatio
            zoomPendingApply = true
        }
        pushFrame()
        maybePushGuide()
    }

    override fun frames(): Flow<ViewfinderFrame> = frameFlow.asSharedFlow()
    override fun guides(): Flow<CompositionGuide> = guideFlow.asSharedFlow()
    override fun filters(): Flow<FilterRecommendation> = filterFlow.asSharedFlow()
    override fun events(): Flow<EngineEvent> = eventFlow.asSharedFlow()

    override suspend fun captureStill(): StillResult {
        ensureSession()
        val result = StillResult(
            uri = STILL_URI,
            width = 4096,
            height = 3072,
            appliedLutId = null,
            cropBoxNorm = subjectBox,
            rotationDeg = 0.0,
            activeLensId = activeLensId,
            timestampNs = timeNs,
        )
        lastStill = result
        emitEvent("capture_ms", "debug", "event.capture_ms")
        return result
    }

    override suspend fun startVideo(opts: VideoOptions): VideoSession {
        ensureSession()
        val vs = VideoSession(
            sessionId = "vid_${sessionSeq.toString().padStart(2, '0')}",
            uriPending = VIDEO_URI,
            size = opts.size,
            fps = opts.fps,
        )
        recording = vs
        recordStartedNs = timeNs
        return vs
    }

    override suspend fun stopVideo(): VideoClipResult {
        val vs = recording ?: error("no video session")
        recording = null
        val durationMs = ((timeNs - recordStartedNs).coerceAtLeast(0)) / 1_000_000L
        return VideoClipResult(uri = vs.uriPending, durationMs = durationMs, size = vs.size)
    }

    override fun applyCrop(cmd: ApplyCrop): StillResult {
        ensureSession()
        val baked = if (cmd.bakeLut) cmd.lutId else null
        val result = StillResult(
            uri = cmd.sourceUri,
            width = 4096,
            height = 3072,
            appliedLutId = baked,
            cropBoxNorm = cmd.cropBoxNorm,
            rotationDeg = cmd.rotationDeg,
            activeLensId = activeLensId,
            timestampNs = timeNs,
        )
        lastStill = result
        return result
    }

    override fun loadDeviceProfile(json: String) {
        // PR-12 binds dump-time ids. Mock only needs zoomBlend if present later.
    }

    override fun saveZoomCalibration(profile: ZoomBlendProfile) {
        blend = profile
    }

    private fun ensureSession() {
        if (session == null) {
            throw IllegalStateException("session not open")
        }
    }

    private fun pushFrame() {
        val opened = session ?: return
        val focal = opened.lenses.firstOrNull { it.lensId == activeLensId }?.focalMm
        val frame = ViewfinderFrame(
            timestampNs = timeNs,
            previewKind = "yuv420",
            previewHandle = "opaque:frame:${++frameSeq}",
            width = opened.let { 1920 },
            height = 1080,
            rotationDeg = 90,
            zoomRatio = zoomRatio,
            activePhysicalCamera = null,
            activeLensId = activeLensId,
            iso = 125,
            exposureNs = 10_000_000,
            focalMm = focal,
        )
        frameLog += frame
        frameFlow.tryEmit(frame)
        if (zoomPendingApply && abs(frame.zoomRatio - zoomRatio) <= 0.02) {
            emitEvent("zoom_apply_ms", "debug", "event.zoom_apply_ms")
            zoomPendingApply = false
        }
    }

    private fun maybePushGuide() {
        if (session == null) return
        if (aiGuide == "off" && !guiding) return
        val dx = targetNx - subjectNx
        val dy = targetNy - subjectNy
        val guide = CompositionGuide(
            timestampNs = timeNs,
            compositionClass = compositionClass,
            confidence = 0.82,
            subjectBox = subjectBox,
            subjectCenterNx = subjectNx,
            subjectCenterNy = subjectNy,
            targetNx = targetNx,
            targetNy = targetNy,
            suggestedZoom = suggestedZoom,
            overlay = listOf(
                OverlayPrimitive(type = "grid_thirds", opacity = 0.35),
                OverlayPrimitive(type = "subject_box", box = subjectBox),
                OverlayPrimitive(type = "pan_arrow", dx = dx, dy = dy),
                OverlayPrimitive(type = "target_reticle", nx = targetNx, ny = targetNy),
                OverlayPrimitive(type = "hint_text", key = "guide.move_subject_to_reticle"),
            ),
        )
        guideLog += guide
        guideFlow.tryEmit(guide)
    }

    private fun emitEvent(
        code: String,
        level: String,
        messageKey: String,
        dataBuilder: (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit)? = null,
    ) {
        val data = dataBuilder?.let { buildJsonObject(it) }
        val event = EngineEvent(
            timestampNs = timeNs,
            code = code,
            level = level,
            messageKey = messageKey,
            data = data,
        )
        eventLog += event
        eventFlow.tryEmit(event)
    }

    private fun desiredLens(zoom: Double, current: String): String {
        return when (current) {
            LENS_UW -> if (zoom >= UW_TO_MAIN) LENS_MAIN else LENS_UW
            LENS_TELE -> if (zoom <= TELE_TO_MAIN) LENS_MAIN else LENS_TELE
            else -> when {
                zoom <= MAIN_TO_UW -> LENS_UW
                zoom >= MAIN_TO_TELE -> LENS_TELE
                else -> LENS_MAIN
            }
        }
    }

    companion object {
        const val ENGINE_MOCK: Boolean = true
        const val FRAME_NS: Long = 1_000_000_000L / 30L
        const val LENS_UW: String = "physical_uw"
        const val LENS_MAIN: String = "physical_main"
        const val LENS_TELE: String = "physical_tele"
        const val STILL_URI: String =
            "content://com.autocam.app.fileprovider/stills/20260911_001.jpg"
        const val VIDEO_URI: String =
            "content://com.autocam.app.fileprovider/video/20260911_001.mp4"

        private const val UW_TO_MAIN = 0.95
        private const val MAIN_TO_UW = 0.72
        private const val MAIN_TO_TELE = 4.80
        private const val TELE_TO_MAIN = 3.60

        val DEFAULT_BLEND: ZoomBlendProfile = ZoomBlendProfile(
            hysteresisFrames = 2,
            fadeFrames = 2,
            algorithm = "freeze_fade",
            breakpoints = listOf(
                ZoomBlendBreakpoint(LENS_UW, LENS_MAIN, UW_TO_MAIN, MAIN_TO_UW, 0.02),
                ZoomBlendBreakpoint(LENS_MAIN, LENS_TELE, MAIN_TO_TELE, TELE_TO_MAIN, 0.03),
            ),
            gainMatch = mapOf(LENS_UW to 1.0, LENS_MAIN to 1.0, LENS_TELE to 1.08),
            cctOffsetK = mapOf(LENS_UW to 0, LENS_MAIN to 0, LENS_TELE to -150),
        )

        val DEFAULT_PARAMS: CaptureParams = CaptureParams(
            aeMode = "on",
            afMode = "continuous_picture",
            awbMode = "auto",
            iso = null,
            exposureNs = null,
            evBias = 0.0,
            fps = 30,
            stabilization = "ois",
            zoomRatio = 1.0,
            physicalLensHint = null,
            stillSize = listOf(4096, 3072),
            videoSize = listOf(1920, 1080),
            jpegQuality = 95,
        )

        val MOCK_SESSION: CameraSession = CameraSession(
            sessionId = "sess_01",
            logicalCameraId = "0",
            facing = "back",
            sessionProfile = "still",
            zoomRatioRange = RangeF(0.61, 10.0),
            supportsLogicalMultiCamera = true,
            supportsControlZoomRatio = true,
            lenses = listOf(
                SessionLens(LENS_UW, null, "ultrawide", 2.2, 14.0, null, 0.61, false, true),
                SessionLens(LENS_MAIN, null, "main", 6.8, 23.0, null, 1.0, true, true),
                SessionLens(LENS_TELE, null, "tele", 15.5, 120.0, null, 5.22, true, true),
            ),
            aeModes = listOf("off", "on", "on_auto_flash"),
            afModes = listOf("off", "auto", "continuous_picture", "continuous_video"),
            awbModes = listOf("off", "auto", "incandescent", "daylight", "cloudy"),
            isoRange = RangeF(50.0, 6400.0),
            exposureNsRange = RangeF(25_000.0, 1_000_000_000.0),
            fpsRanges = listOf(listOf(15, 15), listOf(30, 30)),
            stillSizes = listOf(listOf(4096, 3072), listOf(1920, 1440)),
            videoSizes = listOf(listOf(1920, 1080)),
            stabilization = listOf("off", "eis", "ois"),
        )
    }
}
