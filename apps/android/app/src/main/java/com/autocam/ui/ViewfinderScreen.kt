package com.autocam.ui

import android.view.TextureView
import androidx.compose.foundation.background
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
import com.autocam.engine.CommandBus
import com.autocam.engine.EngineJson
import com.autocam.engine.OpenSessionRequest
import com.autocam.engine.SetZoom
import com.autocam.engine.StillResult
import com.autocam.mock.MockCameraEngine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
fun ViewfinderScreen(
    engine: CameraEngine,
    bus: CommandBus,
    mock: Boolean,
    aiGuide: String = "off",
    onCaptured: (StillResult) -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val frame by engine.frames().collectAsState(initial = null)
    val guide by engine.guides().collectAsState(initial = null)
    var zoom by remember { mutableFloatStateOf(1f) }
    val zoomMin = 0.61f
    val zoomMax = 10f

    LaunchedEffect(engine) {
        if (engine is MockCameraEngine && engine.currentSession() == null) {
            engine.openSession(
                OpenSessionRequest(
                    facing = "back",
                    profileId = "xiaomi.15s_pro.hyperos2",
                    previewMaxFps = 30,
                    previewMaxWidth = 1920,
                    sessionProfile = "still",
                    aiGuide = aiGuide,
                ),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> TextureView(context) },
            modifier = Modifier
                .fillMaxSize()
                .testTag(ViewfinderTags.PREVIEW),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101418)),
        )
        GuideOverlay(guide = guide, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val lens = frame?.activeLensId ?: "—"
            Text(
                text = stringResource(
                    R.string.viewfinder_status,
                    zoom,
                    lens,
                    if (mock) "mock" else "live",
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
            Row {
                Button(
                    onClick = {
                        scope.launch {
                            val raw = bus.captureStill() ?: return@launch
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
