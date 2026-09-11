package com.autocam.engine

import kotlinx.coroutines.flow.Flow

interface CameraEngine {
    suspend fun openSession(req: OpenSessionRequest): CameraSession
    suspend fun closeSession()
    fun setCaptureParams(params: CaptureParams)
    fun setZoom(cmd: SetZoom)
    fun setPhysicalLensHint(lensId: String?)
    fun guideUser(cmd: GuideUser)
    fun frames(): Flow<ViewfinderFrame>
    fun guides(): Flow<CompositionGuide>
    fun filters(): Flow<FilterRecommendation>
    fun events(): Flow<EngineEvent>
    suspend fun captureStill(): StillResult
    suspend fun startVideo(opts: VideoOptions): VideoSession
    suspend fun stopVideo(): VideoClipResult
    fun applyCrop(cmd: ApplyCrop): StillResult
    fun loadDeviceProfile(json: String)
    fun saveZoomCalibration(profile: ZoomBlendProfile)
}
