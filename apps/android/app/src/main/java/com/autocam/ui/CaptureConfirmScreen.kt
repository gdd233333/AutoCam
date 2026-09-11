package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autocam.app.R
import com.autocam.engine.ApplyCrop
import com.autocam.engine.CommandBus
import com.autocam.engine.StillResult
import kotlinx.coroutines.launch

@Composable
fun CaptureConfirmScreen(
    still: StillResult,
    bus: CommandBus,
    onDone: () -> Unit,
    onUndo: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var lutId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.confirm_title))
        Text("uri=${still.uri}")
        Text(
            "crop=(${still.cropBoxNorm.x0}, ${still.cropBoxNorm.y0})-" +
                "(${still.cropBoxNorm.x1}, ${still.cropBoxNorm.y1})",
        )
        Text("rotationDeg=${still.rotationDeg}")
        FilterChips(selected = lutId, onSelect = { lutId = it })
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.testTag("confirm_undo"),
            ) { Text(stringResource(R.string.confirm_undo)) }
            Button(
                onClick = {
                    scope.launch {
                        val selected = lutId
                        bus.applyCrop(
                            ApplyCrop(
                                sourceUri = still.uri,
                                cropBoxNorm = still.cropBoxNorm,
                                rotationDeg = still.rotationDeg,
                                lutId = selected,
                                bakeLut = selected != null,
                            ),
                        )
                        onDone()
                    }
                },
                modifier = Modifier.testTag("confirm_save"),
            ) { Text(stringResource(R.string.confirm_save)) }
        }
    }
}
