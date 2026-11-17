package dev.rileyboughner.launcher.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.rileyboughner.launcher.BuildConfig
import kotlinx.coroutines.launch

/**
 * Checks for an update once per launch (silently, on a background
 * dispatcher — see [UpdateRepository]) and renders the prompt/permission
 * dialogs on top of the host screen when one applies. Shared by
 * [dev.rileyboughner.launcher.MainActivity] (phone/tablet) and
 * [dev.rileyboughner.launcher.TvMainActivity] (TV) — the prompt dialog
 * itself carries the D-pad focus handling the TV needs, so the two hosts
 * need no separate prompt code.
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
