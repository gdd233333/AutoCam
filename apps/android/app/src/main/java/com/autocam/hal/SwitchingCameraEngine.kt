package com.autocam.hal

import android.content.Context
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
import com.autocam.engine.SetZoom
import com.autocam.engine.StillResult
import com.autocam.engine.VideoClipResult
import com.autocam.engine.VideoOptions
import com.autocam.engine.VideoSession
import com.autocam.engine.ViewfinderFrame
import com.autocam.engine.ZoomBlendProfile
import com.autocam.flags.FeatureFlags
import com.autocam.mock.MockCameraEngine
import kotlinx.coroutines.flow.Flow

class SwitchingCameraEngine(
    context: Context,
    private val flags: FeatureFlags,
) : CameraEngine {
    private val mock = MockCameraEngine()
    private val live by lazy { Camera2Engine(context, flags) }

    fun mockEngine(): MockCameraEngine = mock

    private fun active(): CameraEngine = if (flags.engineMock()) mock else live

    override fun currentSession(): CameraSession? = active().currentSession()

    override fun attachPreviewSurface(surface: Surface?) {
        if (flags.engineMock()) {
            mock.attachPreviewSurface(surface)
        } else {
            live.attachPreviewSurface(surface)
        }
    }

    override suspend fun openSession(req: OpenSessionRequest): CameraSession {
        if (flags.engineMock()) {
            runCatching { live.closeSession() }
            return mock.openSession(req)
        }
        runCatching { mock.closeSession() }
        return live.openSession(req)
    }

    override suspend fun closeSession() {
        runCatching { mock.closeSession() }
        runCatching { live.closeSession() }
    }

    override fun setCaptureParams(params: CaptureParams) = active().setCaptureParams(params)
    override fun setZoom(cmd: SetZoom) = active().setZoom(cmd)
    override fun setPhysicalLensHint(lensId: String?) = active().setPhysicalLensHint(lensId)
    override fun guideUser(cmd: GuideUser) = active().guideUser(cmd)
    override fun frames(): Flow<ViewfinderFrame> = active().frames()
    override fun guides(): Flow<CompositionGuide> = active().guides()
    override fun filters(): Flow<FilterRecommendation> = active().filters()
    override fun events(): Flow<EngineEvent> = active().events()
    override suspend fun captureStill(): StillResult = active().captureStill()
    override suspend fun startVideo(opts: VideoOptions): VideoSession = active().startVideo(opts)
    override suspend fun stopVideo(): VideoClipResult = active().stopVideo()
    override fun applyCrop(cmd: ApplyCrop): StillResult = active().applyCrop(cmd)
    override fun loadDeviceProfile(json: String) = active().loadDeviceProfile(json)
    override fun saveZoomCalibration(profile: ZoomBlendProfile) = active().saveZoomCalibration(profile)
}
