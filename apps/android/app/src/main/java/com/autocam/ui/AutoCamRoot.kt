package com.autocam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.autocam.engine.CameraEngine
import com.autocam.engine.CommandBus
import com.autocam.engine.StillResult

private sealed interface Route {
    data object Viewfinder : Route
    data class Confirm(val still: StillResult) : Route
    data object Calibration : Route
    data object Debug : Route
}

@Composable
fun AutoCamRoot(
    engine: CameraEngine,
    bus: CommandBus,
    mock: Boolean,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var granted by remember { mutableStateOf(mock) }
            if (!granted) {
                PermissionGate(mock = mock, onGranted = { granted = true })
                return@Surface
            }
            var route by remember { mutableStateOf<Route>(Route.Viewfinder) }
            when (val current = route) {
                Route.Viewfinder -> ViewfinderScreen(
                    engine = engine,
                    bus = bus,
                    mock = mock,
                    onCaptured = { route = Route.Confirm(it) },
                    onOpenCalibration = { route = Route.Calibration },
                    onOpenDebug = { route = Route.Debug },
                )
                is Route.Confirm -> CaptureConfirmScreen(
                    still = current.still,
                    bus = bus,
                    onDone = { route = Route.Viewfinder },
                    onUndo = { route = Route.Viewfinder },
                )
                Route.Calibration -> CalibrationScreen(
                    bus = bus,
                    onBack = { route = Route.Viewfinder },
                )
                Route.Debug -> DebugSettings(
                    mock = mock,
                    onBack = { route = Route.Viewfinder },
                )
            }
        }
    }
}
