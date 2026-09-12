package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autocam.engine.CaptureParams

@Composable
fun CaptureParamsBar(
    params: CaptureParams,
    onChange: (CaptureParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = params.aeMode != "off",
                onClick = {
                    if (params.aeMode == "off") {
                        onChange(params.copy(aeMode = "on", iso = null, exposureNs = null))
                    } else {
                        onChange(
                            params.copy(
                                aeMode = "off",
                                iso = params.iso ?: 100,
                                exposureNs = params.exposureNs ?: 10_000_000L,
                            ),
                        )
                    }
                },
                label = { Text("AE ${if (params.aeMode == "off") "off" else "on"}") },
                modifier = Modifier.testTag("param_ae"),
            )
            FilterChip(
                selected = params.afMode.startsWith("continuous"),
                onClick = {
                    onChange(
                        params.copy(
                            afMode = if (params.afMode == "continuous_picture") "auto" else "continuous_picture",
                        ),
                    )
                },
                label = { Text("AF ${params.afMode}") },
                modifier = Modifier.testTag("param_af"),
            )
            FilterChip(
                selected = params.awbMode == "auto",
                onClick = {
                    onChange(params.copy(awbMode = if (params.awbMode == "auto") "daylight" else "auto"))
                },
                label = { Text("AWB ${params.awbMode}") },
                modifier = Modifier.testTag("param_awb"),
            )
        }
        Text("EV ${"%.1f".format(params.evBias)}", color = Color.White, fontSize = 12.sp)
        Slider(
            value = params.evBias.toFloat().coerceIn(-2f, 2f),
            onValueChange = { onChange(params.copy(evBias = it.toDouble())) },
            valueRange = -2f..2f,
            modifier = Modifier.testTag("param_ev"),
        )
        if (params.aeMode == "off") {
            val iso = (params.iso ?: 100).toFloat()
            Text("ISO ${iso.toInt()}", color = Color.White, fontSize = 12.sp)
            Slider(
                value = iso,
                onValueChange = { onChange(params.copy(iso = it.toInt())) },
                valueRange = 50f..3200f,
                modifier = Modifier.testTag("param_iso"),
            )
        }
    }
}
