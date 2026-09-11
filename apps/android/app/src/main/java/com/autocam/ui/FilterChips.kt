package com.autocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private val DEFAULT_LUTS = listOf("lut.warm_v1", "lut.clean_v1", "lut.vivid_v1")

@Composable
fun FilterChips(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    lutIds: List<String> = DEFAULT_LUTS,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (id in lutIds) {
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(id.removePrefix("lut.")) },
                modifier = Modifier.testTag("filter_chip_$id"),
            )
        }
    }
}
