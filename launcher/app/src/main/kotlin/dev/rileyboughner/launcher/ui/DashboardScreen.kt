package dev.rileyboughner.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.HomelabLinks
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.dashboard.DashboardResult
import dev.rileyboughner.launcher.dashboard.ServerMetrics
import dev.rileyboughner.launcher.duolingo.DuolingoRepository
import dev.rileyboughner.launcher.duolingo.DuolingoResult
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.settings.SettingsActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Leftmost page of the pager: the homelab dashboard. Leads with live server
 * metrics from the web dashboard's public `/api/all` route, then Jellyfin
 * "continue watching" (posters), then plain text rows linking out to the
 * rest of the homelab services — text-first and minimal per Riley's
 * restyle, posters only inside the Jellyfin section.
 */
@Composable
fun DashboardScreen(
    dashboardRepository: DashboardRepository,
    jellyfinRepository: JellyfinRepository,
    duolingoRepository: DuolingoRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var metricsResult by remember { mutableStateOf<DashboardResult<ServerMetrics>?>(null) }
    var duolingoResult by remember { mutableStateOf<DuolingoResult?>(null) }

    LaunchedEffect(Unit) {
        metricsResult = dashboardRepository.fetchServerMetrics()
    }

    LaunchedEffect(Unit) {
        duolingoResult = duolingoRepository.fetchStreak()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Metrics fetch failing (or still loading) just omits this section —
        // the rest of the dashboard page must never be blocked by it.
        val metrics = (metricsResult as? DashboardResult.Success)?.value
        if (metrics != null) {
            MetricsSection(metrics, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            HairlineDivider()
        }

        DuolingoStreakRow(
            result = duolingoResult,
            onRefresh = {
                coroutineScope.launch { duolingoResult = duolingoRepository.fetchStreak(forceRefresh = true) }
            },
            onOpenSettings = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
        HairlineDivider()

        JellyfinContinueWatchingSection(jellyfinRepository = jellyfinRepository, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        HairlineDivider()

        HomelabLinks.LINKS.forEach { link ->
            AppTextRow(
                label = link.label,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricsSection(metrics: ServerMetrics, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Server",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        metrics.cpuPercent?.let { cpu ->
            MetricRow("CPU", "${cpu.roundToInt()}%")
        }
        if (metrics.memoryUsedBytes != null && metrics.memoryTotalBytes != null) {
            MetricRow("RAM", formatUsage(metrics.memoryUsedBytes, metrics.memoryTotalBytes))
        }
        if (metrics.diskUsedBytes != null && metrics.diskTotalBytes != null) {
            MetricRow("Disk (/mnt/tank)", formatUsage(metrics.diskUsedBytes, metrics.diskTotalBytes))
        }
        metrics.uptimeSeconds?.let { seconds ->
            MetricRow("Uptime", formatUptime(seconds))
        }
        if (metrics.containersRunning != null && metrics.containersTotal != null) {
            MetricRow("Containers", "${metrics.containersRunning} / ${metrics.containersTotal} running")
        }
    }
}

/**
 * Small text-only row, no icon/card, directly under the server metrics
 * block. Tapping it refreshes the streak — except when no username is
 * saved yet, where tapping opens Settings instead, same as the "not
 * configured" rows elsewhere on this page.
 */
@Composable
private fun DuolingoStreakRow(
    result: DuolingoResult?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (result) {
        null -> "Duolingo streak: …"
        is DuolingoResult.NotConfigured -> "Duolingo: not set up"
        is DuolingoResult.Unavailable -> "Duolingo: unavailable"
        is DuolingoResult.Success -> "Duolingo streak: ${result.streakDays} days"
    }
    val onClick = if (result is DuolingoResult.NotConfigured) onOpenSettings else onRefresh

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun MetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GIB = 1024.0 * 1024.0 * 1024.0

/** Picks GB or TB for the pair based on the total, so used/total never show mismatched units. */
private fun formatUsage(usedBytes: Long, totalBytes: Long): String {
    val usedGiB = usedBytes / GIB
    val totalGiB = totalBytes / GIB
    val percent = if (totalBytes > 0) ((usedBytes * 100.0) / totalBytes).roundToInt() else 0
    val pair = if (totalGiB >= 1024) {
        "%.1f / %.1f TB".format(usedGiB / 1024.0, totalGiB / 1024.0)
    } else {
        "%.1f / %.1f GB".format(usedGiB, totalGiB)
    }
    return "$pair · $percent%"
}

private fun formatUptime(seconds: Double): String {
    val totalMinutes = (seconds / 60).toLong()
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    return if (days > 0) "${days}d ${hours}h" else "${hours}h ${totalMinutes % 60}m"
}
