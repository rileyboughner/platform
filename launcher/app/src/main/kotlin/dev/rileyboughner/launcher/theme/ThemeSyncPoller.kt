package dev.rileyboughner.launcher.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the launcher's synced theme/wallpaper current while the hosting
 * activity is actually in the foreground, plus once immediately on every
 * ON_RESUME. Two paths, matching the desktop/laptop watcher:
 *
 * - Push: holds the hub's Server-Sent Events stream (`GET /events`) and
 *   re-polls on every nudge, so a change lands within about a second.
 *   Reconnects with backoff (5s, doubling to 30s) when the stream ends,
 *   times out, or the hub is unreachable.
 * - Fallback: a slow 60s poll that catches anything the stream missed
 *   (dropped connection, missed event).
 *
 * Matching ON_PAUSE/ON_RESUME rather than a plain always-running loop is
 * what stops this from polling the hub (or holding a connection) while
 * the launcher isn't on screen.
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
                        // The push stream and the fallback ticker can
                        // fire at the same time; serialize the actual
                        // applies so two polls never download or
                        // set the same wallpaper concurrently.
                        val pollMutex = Mutex()
                        suspend fun guardedPoll() = pollMutex.withLock { poll() }

                        guardedPoll()
                        coroutineScope {
                            launch {
                                var backoffMs = 5_000L
                                while (isActive) {
                                    try {
                                        val baseUrl = withContext(Dispatchers.IO) { ThemeSyncApi.findHubBaseUrl() }
                                        if (baseUrl == null) {
                                            delay(backoffMs)
                                            backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                                            continue
                                        }
                                        backoffMs = 5_000
                                        ThemeSyncApi.collectEvents(baseUrl) { guardedPoll() }
                                        delay(1_000)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        delay(backoffMs)
                                        backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                                    }
                                }
                            }
                            launch {
                                while (isActive) {
                                    delay(60_000)
                                    guardedPoll()
                                }
                            }
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
