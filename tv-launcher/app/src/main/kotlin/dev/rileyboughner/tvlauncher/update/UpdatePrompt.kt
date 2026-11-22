package dev.rileyboughner.tvlauncher.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import dev.rileyboughner.tvlauncher.BuildConfig
import kotlinx.coroutines.launch

/**
 * Checks for an update once per launch (silently, on a background
 * dispatcher — see [UpdateRepository]) and renders the prompt/permission
 * dialogs on top of the home screen when one applies. The prompt dialog
 * carries the D-pad focus handling the TV needs: the confirm button takes
 * focus when the dialog appears so D-pad navigation starts on a visible,
 * actionable button.
 */
@Composable
fun UpdatePrompt(updateRepository: UpdateRepository) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var showUnknownSourcesPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        updateInfo = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE)
    }

    updateInfo?.let { info ->
        UpdatePromptDialog(
            updateInfo = info,
            downloading = downloading,
            onUpdate = {
                downloading = true
                scope.launch {
                    try {
                        if (UpdateInstaller.downloadAndInstall(context, info.apkUrl)) {
                            updateInfo = null
                        } else {
                            showUnknownSourcesPrompt = true
                        }
                    } catch (e: Exception) {
                        // Download/install failed (offline mid-download, bad
                        // asset, etc.) — drop the prompt silently rather than
                        // nag or crash; it reappears next launch since this
                        // version was never marked dismissed.
                        updateInfo = null
                    } finally {
                        downloading = false
                    }
                }
            },
            onLater = {
                updateRepository.dismiss(info.versionCode)
                updateInfo = null
            },
        )
    }

    if (showUnknownSourcesPrompt) {
        AllowUnknownSourcesDialog(
            onOpenSettings = {
                showUnknownSourcesPrompt = false
                context.startActivity(UpdateInstaller.unknownSourcesSettingsIntent(context))
            },
            onDismiss = { showUnknownSourcesPrompt = false },
        )
    }
}

/** Minimal "Update available" prompt — no icon, no progress bar, matching the launcher's monochrome theme. */
@Composable
private fun UpdatePromptDialog(
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
private fun AllowUnknownSourcesDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
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
