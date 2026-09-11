package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autocam.app.R
import com.autocam.engine.CommandBus
import com.autocam.engine.ZoomBlendBreakpoint
import com.autocam.engine.ZoomBlendProfile
import com.autocam.mock.MockCameraEngine
import kotlinx.coroutines.launch

@Composable
fun CalibrationScreen(
    bus: CommandBus,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hysteresis by remember { mutableFloatStateOf(2f) }
    var fade by remember { mutableFloatStateOf(2f) }
    var upSwitch by remember { mutableFloatStateOf(0.95f) }
    var downSwitch by remember { mutableFloatStateOf(0.72f) }
    var teleUp by remember { mutableFloatStateOf(4.80f) }
    var teleDown by remember { mutableFloatStateOf(3.60f) }
    var crop by remember { mutableFloatStateOf(0.02f) }
    var gainTele by remember { mutableFloatStateOf(1.08f) }
    var cctTele by remember { mutableFloatStateOf(-150f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.calibration_title))
        LabeledSlider("hysteresisFrames", hysteresis, 1f..8f) { hysteresis = it }
        LabeledSlider("fadeFrames", fade, 1f..8f) { fade = it }
        LabeledSlider("uw→main upSwitch", upSwitch, 0.7f..1.2f) { upSwitch = it }
        LabeledSlider("main→uw downSwitch", downSwitch, 0.5f..1.0f) { downSwitch = it }
        LabeledSlider("main→tele upSwitch", teleUp, 3f..6f) { teleUp = it }
        LabeledSlider("tele→main downSwitch", teleDown, 2f..5f) { teleDown = it }
        LabeledSlider("hideSwitchCropNorm", crop, 0f..0.1f) { crop = it }
        LabeledSlider("gain tele", gainTele, 0.8f..1.3f) { gainTele = it }
        LabeledSlider("cctOffsetK tele", cctTele, -400f..400f) { cctTele = it }
        Button(
            onClick = {
                scope.launch {
                    bus.saveZoomCalibration(
                        ZoomBlendProfile(
                            hysteresisFrames = hysteresis.toInt().coerceAtLeast(1),
                            fadeFrames = fade.toInt().coerceAtLeast(1),
                            algorithm = "freeze_fade",
                            breakpoints = listOf(
                                ZoomBlendBreakpoint(
                                    MockCameraEngine.LENS_UW,
                                    MockCameraEngine.LENS_MAIN,
                                    upSwitch.toDouble(),
                                    downSwitch.toDouble(),
                                    crop.toDouble(),
                                ),
                                ZoomBlendBreakpoint(
                                    MockCameraEngine.LENS_MAIN,
                                    MockCameraEngine.LENS_TELE,
                                    teleUp.toDouble(),
                                    teleDown.toDouble(),
                                    crop.toDouble(),
                                ),
                            ),
                            gainMatch = mapOf(
                                MockCameraEngine.LENS_UW to 1.0,
                                MockCameraEngine.LENS_MAIN to 1.0,
                                MockCameraEngine.LENS_TELE to gainTele.toDouble(),
                            ),
                            cctOffsetK = mapOf(
                                MockCameraEngine.LENS_UW to 0,
                                MockCameraEngine.LENS_MAIN to 0,
                                MockCameraEngine.LENS_TELE to cctTele.toInt(),
                            ),
                        ),
                    )
                    onBack()
                }
            },
            modifier = Modifier.testTag("calibration_save"),
        ) { Text(stringResource(R.string.calibration_save)) }
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$label: ${"%.2f".format(value)}")
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
