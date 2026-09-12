package com.autocam.hal

import kotlinx.serialization.Serializable

@Serializable
data class HalDump(
    val dumpedAtIso: String,
    val build: BuildDump,
    val cameras: List<CameraNodeDump>,
    val sessionCombos: List<SessionComboDump>,
    val xiaomiCameraEngine: XiaomiEngineDump,
)

@Serializable
data class BuildDump(
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val release: String,
    val fingerprint: String,
)

@Serializable
data class CameraNodeDump(
    val cameraId: String,
    val facing: String,
    val hardwareLevel: String,
    val capabilities: List<String>,
    val physicalCameraIds: List<String>,
    val focalLengthsMm: List<Double>,
    val apertures: List<Double>,
    val zoomRatioRange: MinMax?,
    val sensorPixelArray: List<Int>?,
    val sensorActiveArray: List<Int>?,
    val hasControlZoomRatio: Boolean,
    val hasLogicalMultiCamera: Boolean,
    val jpegSizes: List<List<Int>>,
    val yuv420888Sizes: List<List<Int>>,
    val privateSizes: List<List<Int>>,
    val surfaceTextureSizes: List<List<Int>>,
)

@Serializable
data class MinMax(
    val min: Double,
    val max: Double,
)

@Serializable
data class SessionComboDump(
    val logicalCameraId: String,
    val profile: String,
    val outputs: List<SessionOutputDump>,
    val supported: Boolean?,
    val queryApi: String,
    val error: String? = null,
)

@Serializable
data class SessionOutputDump(
    val format: String,
    val width: Int,
    val height: Int,
    val physicalCameraId: String? = null,
    val role: String,
)

@Serializable
data class XiaomiEngineDump(
    val sdkOnClasspath: Boolean,
    val classesTried: List<String>,
    val classesPresent: List<String>,
    val cameraPackages: List<String>,
    val notes: String,
)
