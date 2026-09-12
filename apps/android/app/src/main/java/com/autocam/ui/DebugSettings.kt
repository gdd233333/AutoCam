package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autocam.app.R
import com.autocam.core.NativeCore
import com.autocam.flags.AiGuide
import com.autocam.flags.FeatureFlags
import com.autocam.hal.HalDumper
import com.autocam.observability.DebugBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DebugSettings(
    flags: FeatureFlags,
    debugBundle: DebugBundle? = null,
    onBack: () -> Unit,
) {
    val snap by flags.snapshot.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPath by remember { mutableStateOf<String?>(null) }
    var dumpPath by remember { mutableStateOf<String?>(null) }
    var dumpError by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.debug_title))
        Text("JNI add(2,3)=${NativeCore.tryAdd(2, 3) ?: "unloaded"}")
        FlagSwitch("engine.mock", snap.engineMock, "flag_engine_mock") {
            flags.setEngineMock(it)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "camera.id=${snap.debugCameraId}  (${cameraIdHint(snap.debugCameraId)})",
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { flags.cycleDebugCameraId() },
                modifier = Modifier.testTag("flag_debug_camera_id"),
            ) { Text("Cycle cam") }
        }
        FlagSwitch("hal.multi_lens", snap.halMultiLens, "flag_hal_multi_lens") {
            flags.setHalMultiLens(it)
        }
        FlagSwitch("hal.zoom_ratio", snap.halZoomRatio, "flag_hal_zoom_ratio") {
            flags.setHalZoomRatio(it)
        }
        FlagSwitch("zoom.blend", snap.zoomBlend, "flag_zoom_blend") {
            flags.setZoomBlend(it)
        }
        FlagSwitch("zoom.dual_physical", snap.zoomDualPhysical, "flag_zoom_dual_physical") {
            flags.setZoomDualPhysical(it)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ai.guide=${snap.aiGuide}", modifier = Modifier.weight(1f))
            Button(
                onClick = { flags.setAiGuide(AiGuide.fromWire(snap.aiGuide).next()) },
                modifier = Modifier.testTag("flag_ai_guide"),
            ) { Text(stringResource(R.string.debug_cycle_guide)) }
        }
        FlagSwitch("ai.refine_still", snap.aiRefineStill, "flag_ai_refine_still") {
            flags.setAiRefineStill(it)
        }
        FlagSwitch("grade.lut", snap.gradeLut, "flag_grade_lut") {
            flags.setGradeLut(it)
        }
        FlagSwitch("capture.dng", snap.captureDng, "flag_capture_dng") {
            flags.setCaptureDng(it)
        }
        FlagSwitch("capture.full_res", snap.captureFullRes, "flag_capture_full_res") {
            flags.setCaptureFullRes(it)
        }
        FlagSwitch("debug.include_hal_dump", snap.debugIncludeHalDump, "flag_debug_include_hal_dump") {
            flags.setDebugIncludeHalDump(it)
        }
        FlagSwitch("debug.flag_secure", snap.debugFlagSecure, "flag_debug_flag_secure") {
            flags.setDebugFlagSecure(it)
        }
        Button(
            onClick = {
                dumpError = null
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            HalDumper(context.applicationContext).dumpToFile()
                        }
                        dumpPath = file.absolutePath
                    } catch (t: Throwable) {
                        dumpError = t.message ?: t.javaClass.simpleName
                    }
                }
            },
            modifier = Modifier.testTag("debug_hal_dump"),
        ) { Text(stringResource(R.string.debug_hal_dump)) }
        dumpPath?.let { Text("dump=$it") }
        dumpError?.let { Text("dump error=$it") }
        if (debugBundle != null) {
            Button(
                onClick = { exportPath = debugBundle.export().absolutePath },
                modifier = Modifier.testTag("debug_export"),
            ) { Text(stringResource(R.string.debug_export)) }
            exportPath?.let { Text(it) }
        }
        Button(onClick = onBack, modifier = Modifier.testTag("debug_back")) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun cameraIdHint(id: String): String {
    return when (id) {
        "0" -> "main 23mm"
        "2" -> "UW 14mm"
        "3" -> "tele 120mm"
        "4" -> "software SAT: auto 2/0/3 + freeze-fade"
        "1" -> "front"
        else -> "auto (0 or 4)"
    }
}

@Composable
private fun FlagSwitch(
    label: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label=$checked", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
