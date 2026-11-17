package dev.rileyboughner.launcher.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Minimal "Update available" prompt — no icon, no progress bar, matching
 * the rest of the launcher's monochrome [dev.rileyboughner.launcher.theme.MonoLauncherTheme],
 * which `AlertDialog` already picks up from `MaterialTheme.colorScheme`
 * with no color overrides needed here. Shown only from
 * [dev.rileyboughner.launcher.MainActivity].
 */
@Composable
fun UpdatePromptDialog(
    updateInfo: UpdateInfo,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            val notes = updateInfo.notes.takeIf { it.isNotBlank() }
            Text(if (notes != null) "${updateInfo.versionName}\n\n$notes" else updateInfo.versionName)
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow installs from this app") },
        text = { Text("Android needs permission to install the update. Allow it on the next screen, then come back and tap Update again.") },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
