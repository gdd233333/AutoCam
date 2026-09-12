package com.autocam.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.autocam.app.R
import com.autocam.engine.CameraEngine
import com.autocam.engine.CaptureParams
import com.autocam.engine.CommandBus
import com.autocam.engine.EngineJson
import com.autocam.engine.OpenSessionRequest
import com.autocam.engine.SetZoom
import com.autocam.engine.StillResult
import com.autocam.hal.Camera2Engine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
fun ViewfinderScreen(
    engine: CameraEngine,
    bus: CommandBus,
    mock: Boolean,
    aiGuide: String = "off",
    cameraId: String = "auto",
    onCaptured: (StillResult) -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val frameFlow = remember(engine, mock) { engine.frames() }
    val guideFlow = remember(engine, mock) { engine.guides() }
    val frame by frameFlow.collectAsState(initial = null)
    val guide by guideFlow.collectAsState(initial = null)
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(10f) }
    var params by remember {
        mutableStateOf(Camera2Engine.DEFAULT_PARAMS)
    }

    LaunchedEffect(engine, mock, aiGuide, cameraId) {
        runCatching { engine.closeSession() }
        val session = engine.openSession(
            OpenSessionRequest(
                facing = if (cameraId == "1") "front" else "back",
                profileId = "xiaomi.15s_pro.hyperos2",
                previewMaxFps = 30,
                previewMaxWidth = 1920,
                sessionProfile = "still",
                aiGuide = aiGuide,
            ),
        )
        zoomMin = session.zoomRatioRange.min.toFloat()
        zoomMax = session.zoomRatioRange.max.toFloat()
        zoom = zoom.coerceIn(zoomMin, zoomMax)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            surface.setDefaultBufferSize(1920, 1080)
                            engine.attachPreviewSurface(Surface(surface))
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            engine.attachPreviewSurface(null)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag(ViewfinderTags.PREVIEW),
        )
        GuideOverlay(guide = guide, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val lens = frame?.activeLensId ?: "—"
            val iso = frame?.iso?.toString() ?: "—"
            val cam = engine.currentSession()?.logicalCameraId ?: cameraId
            Text(
                text = stringResource(
                    R.string.viewfinder_status,
                    zoom,
                    lens,
                    if (mock) "mock" else "id$cam iso$iso",
                ),
                color = Color.White,
            )
            Slider(
                value = zoom.coerceIn(zoomMin, zoomMax),
                onValueChange = { value ->
                    zoom = value
                    scope.launch {
                        bus.setZoom(
                            SetZoom(
                                zoomRatio = value.toDouble(),
                                rate = "immediate",
                                source = "user_slider",
                            ),
                        )
                    }
                },
                valueRange = zoomMin..zoomMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ViewfinderTags.ZOOM_SLIDER),
            )
            CaptureParamsBar(
                params = params,
                onChange = { next ->
                    params = next.copy(zoomRatio = zoom.toDouble())
                    scope.launch { bus.setCaptureParams(params) }
                },
            )
            Row {
                Button(
                    onClick = {
                        scope.launch {
                            val raw = runCatching { bus.captureStill() }.getOrNull() ?: return@launch
                            onCaptured(EngineJson.decodeFromJsonElement(raw))
                        }
                    },
                    modifier = Modifier.testTag(ViewfinderTags.SHUTTER),
                ) { Text(stringResource(R.string.shutter)) }
                Button(onClick = onOpenCalibration) {
                    Text(stringResource(R.string.calibration_title))
                }
                Button(onClick = onOpenDebug) {
                    Text(stringResource(R.string.debug_title))
                }
            }
        }
    }
}
