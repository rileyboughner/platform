package dev.rileyboughner.launcher.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Minimal "Update available" prompt — no icon, no progress bar, matching
 * the rest of the launcher's monochrome [dev.rileyboughner.launcher.theme.MonoLauncherTheme],
 * which `AlertDialog` already picks up from `MaterialTheme.colorScheme`
 * with no color overrides needed here. Shown from the shared [UpdatePrompt]
 * composable on phone, tablet, and TV. The confirm button takes focus when
 * the dialog appears so D-pad navigation on the TV starts on a visible,
 * actionable button (harmless on touch devices).
 */
@Composable
fun UpdatePromptDialog(
    updateInfo: UpdateInfo,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val updateFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { updateFocusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            val notes = updateInfo.notes.takeIf { it.isNotBlank() }
            Text(if (notes != null) "${updateInfo.versionName}\n\n$notes" else updateInfo.versionName)
        },
        confirmButton = {
            TextButton(
                onClick = onUpdate,
                enabled = !downloading,
                modifier = Modifier.focusRequester(updateFocusRequester),
            ) {
                Text(if (downloading) "Downloading…" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) {
                Text("Later")
            }
        },
    )
}

/** Shown when Android blocks the install because this app isn't yet allowed to install unknown apps. */
@Composable
fun AllowUnknownSourcesDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val settingsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { settingsFocusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow installs from this app") },
        text = { Text("Android needs permission to install the update. Allow it on the next screen, then come back and tap Update again.") },
        confirmButton = {
            TextButton(onClick = onOpenSettings, modifier = Modifier.focusRequester(settingsFocusRequester)) { Text("Open settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
