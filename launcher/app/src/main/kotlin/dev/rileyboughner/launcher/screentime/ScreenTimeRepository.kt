package dev.rileyboughner.launcher.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

sealed interface ScreenTimeResult {
    data class Success(val duration: Duration) : ScreenTimeResult
    data object NoAccess : ScreenTimeResult
}

/**
 * Today's total foreground screen time, summed across all apps via
 * [UsageStatsManager.queryUsageStats] (INTERVAL_DAILY) +
 * [android.app.usage.UsageStats.getTotalTimeInForeground]. Requires the
 * special "usage access" grant (PACKAGE_USAGE_STATS), which a user must
 * enable by hand in system Settings — there is no runtime permission
 * dialog for it, so [hasUsageAccess] just reports the current grant state
 * for the main page to render a dash + settings link instead of crashing.
 */
object ScreenTimeRepository {

    @Volatile private var cachedResult: ScreenTimeResult? = null
    @Volatile private var cachedAtMillis: Long = 0L
    private const val STALE_AFTER_MILLIS = 15 * 60 * 1000L

    fun cachedOrNull(): ScreenTimeResult? = cachedResult

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun fetchTodayScreenTime(context: Context, forceRefresh: Boolean = false): ScreenTimeResult {
        val isFresh = cachedResult != null && (System.currentTimeMillis() - cachedAtMillis) < STALE_AFTER_MILLIS
        if (isFresh && !forceRefresh) return cachedResult!!

        val result = fetchTodayScreenTimeInternal(context)
        cachedResult = result
        cachedAtMillis = System.currentTimeMillis()
        return result
    }

    private suspend fun fetchTodayScreenTimeInternal(context: Context): ScreenTimeResult =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess(context)) return@withContext ScreenTimeResult.NoAccess
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val zone = ZoneId.systemDefault()
                val startOfDayMillis = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val nowMillis = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startOfDayMillis,
                    nowMillis,
                )
                val totalMillis = stats.sumOf { it.totalTimeInForeground }
                ScreenTimeResult.Success(Duration.ofMillis(totalMillis))
            } catch (e: Exception) {
                ScreenTimeResult.NoAccess
            }
        }
}
