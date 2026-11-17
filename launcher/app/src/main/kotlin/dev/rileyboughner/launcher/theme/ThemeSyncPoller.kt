package dev.rileyboughner.launcher.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs [poll] every ~15s (same cadence family as the desktop/laptop
 * watcher) while the hosting activity is actually in the foreground, plus
 * once immediately on every ON_RESUME -- matching ON_PAUSE/ON_RESUME
 * rather than a plain always-running loop is what stops this from polling
 * the hub (or holding a wakelock-free background coroutine) while the
 * launcher isn't on screen.
 *
 * Shared by the phone/tablet entry point and the TV entry point; each side
 * passes its own poll lambda, since the phone honors the Settings sync
 * toggle and applies the system wallpaper ([ThemeSyncRepository.poll])
 * while the TV is always-on and draws the wallpaper itself
 * ([ThemeSyncRepository.pollTv]). Both repository functions are no-ops
 * (return null) when the hub is unreachable, so this never needs its own
 * reachability check.
 */
@Composable
internal fun ThemeSyncPoller(poll: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        var pollJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pollJob = coroutineScope.launch {
                        while (isActive) {
                            poll()
                            delay(15_000)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> pollJob?.cancel()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pollJob?.cancel()
        }
    }
}
