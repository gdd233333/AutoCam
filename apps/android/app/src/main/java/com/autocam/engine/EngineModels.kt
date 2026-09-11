package com.autocam.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BoxNorm(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
)

@Serializable
data class RangeF(
    val min: Double,
    val max: Double,
)

@Serializable
data class OpenSessionRequest(
    val facing: String,
    val profileId: String? = null,
    val previewMaxFps: Int? = null,
    val previewMaxWidth: Int? = null,
    val sessionProfile: String,
    val aiGuide: String,
)

@Serializable
data class SessionLens(
    val lensId: String,
    val physicalCameraId: String? = null,
    val role: String,
    val focalMm: Double,
    val equiv35mm: Double,
    val fNumber: Double? = null,
    val opticalZoom: Double,
    val hasOis: Boolean,
    val hasAf: Boolean,
)

@Serializable
data class CameraSession(
    val sessionId: String,
    val logicalCameraId: String,
    val facing: String,
    val sessionProfile: String,
    val zoomRatioRange: RangeF,
    val supportsLogicalMultiCamera: Boolean,
    val supportsControlZoomRatio: Boolean,
    val lenses: List<SessionLens>,
    val aeModes: List<String>,
    val afModes: List<String>,
    val awbModes: List<String>,
    val isoRange: RangeF,
    val exposureNsRange: RangeF,
    val fpsRanges: List<List<Int>>,
    val stillSizes: List<List<Int>>,
    val videoSizes: List<List<Int>>,
    val stabilization: List<String>,
)

@Serializable
data class CaptureParams(
    val aeMode: String,
    val afMode: String,
    val awbMode: String,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDistance: Double? = null,
    val wbCctK: Int? = null,
    val evBias: Double,
    val fps: Int,
    val stabilization: String,
    val zoomRatio: Double,
    val physicalLensHint: String? = null,
    val stillSize: List<Int>? = null,
    val videoSize: List<Int>? = null,
    val jpegQuality: Int,
)

@Serializable
data class SetZoom(
    val zoomRatio: Double,
    val rate: String,
    val source: String,
)

@Serializable
data class GuideUser(
    val panNx: Double,
    val panNy: Double,
    val zoomRatio: Double,
    val followSuggested: Boolean,
)

@Serializable
data class ApplyCrop(
    val sourceUri: String,
    val cropBoxNorm: BoxNorm,
    val rotationDeg: Double,
    val lutId: String? = null,
    val bakeLut: Boolean,
)

@Serializable
data class VideoOptions(
    val size: List<Int>,
    val fps: Int,
    val audio: Boolean,
    val stabilize: Boolean,
)

@Serializable
data class VideoSession(
    val sessionId: String,
    val uriPending: String,
    val size: List<Int>,
    val fps: Int,
)

@Serializable
data class VideoClipResult(
    val uri: String,
    val durationMs: Long,
    val size: List<Int>,
)

@Serializable
data class ViewfinderFrame(
    val timestampNs: Long,
    val previewKind: String,
    val previewHandle: String,
    val width: Int,
    val height: Int,
    val rotationDeg: Int,
    val zoomRatio: Double,
    val activePhysicalCamera: String? = null,
    val activeLensId: String,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focalMm: Double? = null,
)

@Serializable
data class OverlayPrimitive(
    val type: String,
    val opacity: Double? = null,
    val box: BoxNorm? = null,
    val nx: Double? = null,
    val ny: Double? = null,
    val dx: Double? = null,
    val dy: Double? = null,
    val key: String? = null,
    val angleDeg: Double? = null,
)

@Serializable
data class CompositionGuide(
    val timestampNs: Long,
    val compositionClass: String,
    val confidence: Double,
    val subjectBox: BoxNorm,
    val subjectCenterNx: Double,
    val subjectCenterNy: Double,
    val targetNx: Double,
    val targetNy: Double,
    val suggestedZoom: Double,
    val overlay: List<OverlayPrimitive>,
)

@Serializable
data class StillResult(
    val uri: String,
    val width: Int,
    val height: Int,
    val appliedLutId: String?,
    val cropBoxNorm: BoxNorm,
    val rotationDeg: Double,
    val activeLensId: String,
    val timestampNs: Long,
)

@Serializable
data class FilterCandidate(
    val lutId: String,
    val score: Double,
    val tags: List<String>,
)

@Serializable
data class FilterRecommendation(
    val timestampNs: Long,
    val candidates: List<FilterCandidate>,
)

@Serializable
data class EngineEvent(
    val timestampNs: Long,
    val code: String,
    val level: String,
    val messageKey: String? = null,
    val data: JsonObject? = null,
)

@Serializable
data class ZoomBlendBreakpoint(
    val fromLensId: String,
    val toLensId: String,
    val upSwitch: Double,
    val downSwitch: Double,
    val hideSwitchCropNorm: Double,
)

@Serializable
data class ZoomBlendProfile(
    val hysteresisFrames: Int,
    val fadeFrames: Int,
    val algorithm: String,
    val breakpoints: List<ZoomBlendBreakpoint>,
    val gainMatch: Map<String, Double> = emptyMap(),
    val cctOffsetK: Map<String, Int> = emptyMap(),
)
