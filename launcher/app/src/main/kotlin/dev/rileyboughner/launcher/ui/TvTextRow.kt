package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one visual building block of the TV UI: a large text row with a
 * single, consistent D-pad focus indicator — bold brighter-white text plus
 * a leading "▸" marker — no ripple, no scale/elevation effects. D-pad
 * center/Enter triggers [onClick] for free: Compose's `clickable` modifier
 * already reports focus via [MutableInteractionSource] and handles
 * Enter/Numpad-Enter/DPAD_CENTER key events when focused, so no extra key
 * handling is needed here. [focusRequester], when supplied, lets the host
 * screen request initial focus on first composition so D-pad up/down has
 * somewhere to start moving from.
 */
@Composable
fun TvTextRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (if (isFocused) "▸ " else "   ") + label,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (isFocused) 34.sp else 30.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.55f),
        )
    }
}
