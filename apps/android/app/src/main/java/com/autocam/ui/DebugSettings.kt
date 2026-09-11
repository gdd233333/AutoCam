package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autocam.app.R
import com.autocam.mock.MockCameraEngine

@Composable
fun DebugSettings(
    mock: Boolean,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.debug_title))
        Text("engine.mock=$mock")
        Text("ENGINE_MOCK=${MockCameraEngine.ENGINE_MOCK}")
        Text(stringResource(R.string.debug_flags_pending))
        Button(onClick = onBack, modifier = Modifier.testTag("debug_back")) {
            Text(stringResource(R.string.back))
        }
    }
}
