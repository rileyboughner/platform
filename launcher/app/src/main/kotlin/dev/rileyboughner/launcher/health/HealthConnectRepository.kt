package dev.rileyboughner.launcher.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** The one Health Connect permission this app ever requests — read-only, sleep only. */
val READ_SLEEP_PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

sealed interface SleepResult {
    /**
     * [deep]/[rem]/[light] are null when the session carried no stage
     * breakdown at all (some sources only log a plain start/end session) —
     * in that case [totalAsleep] falls back to the raw session duration.
     * When present, they're true per-stage totals, including a genuine
     * zero if a stage type just didn't occur in this session.
     */
    data class Success(
        val totalAsleep: Duration,
        val deep: Duration?,
        val rem: Duration?,
        val light: Duration?,
    ) : SleepResult
    data object NoPermission : SleepResult
    data object NoData : SleepResult
    data object Unavailable : SleepResult
}

/**
 * Thin wrapper over the Jetpack Health Connect client, scoped to sleep
 * sessions from the last 36 hours. Every path (provider missing,
 * permission not granted, no recent session, any client exception) turns
 * into a [SleepResult] value — this must never throw into the UI.
 *
 * Deliberately exposes no concept of a sleep *score*: Health Connect's
 * data model only carries [SleepSessionRecord] sessions and stages, never
 * a score. Fitbit computes its sleep score inside its own app/cloud and
 * does not publish it to Health Connect, so there is no field anywhere in
 * this client to read one from — don't add a "score" display without a
 * real source for it.
 */
object HealthConnectRepository {

    @Volatile private var cachedResult: SleepResult? = null
    @Volatile private var cachedAtMillis: Long = 0L
    private const val STALE_AFTER_MILLIS = 15 * 60 * 1000L
    private val LOOKBACK = Duration.ofHours(36)

    /** Raw `getSdkStatus()` return value — see [sdkStatusName] to render it. */
    fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    /** Maps a [sdkStatus] int to its constant name, so a failure is diagnosable from the UI instead of silent. */
    fun sdkStatusName(status: Int): String = when (status) {
        HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE"
        HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"
        else -> "UNKNOWN"
    }

    fun isAvailable(context: Context): Boolean = sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * Unconditionally attempts to bind/register with the Health Connect
     * controller, regardless of what [sdkStatus] reports. getSdkStatus() is
     * a static capability check that never itself talks to the controller —
     * only getOrCreate() does — and can be pessimistic (e.g. against a
     * stale client library or a newly-updated provider). Gating every path
     * on it, as this repository previously did everywhere, meant the app
     * could go its entire lifetime without ever calling getOrCreate() and
     * so would never appear in Health Connect's own app list. This call
     * always goes out, off the main thread, with every exception swallowed
     * into the returned diagnostic text, so Settings/the main page can
     * surface whatever actually happened instead of guessing from the
     * status check alone.
     */
    suspend fun attemptRegistration(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            "registered OK (${granted.size} permission(s) granted)"
        } catch (e: Exception) {
            "registration failed: ${e::class.simpleName}: ${e.message}"
        }
    }

    /** Synchronous, no-I/O peek at the last fetched result, to seed Compose state before refresh completes. */
    fun cachedSleepOrNull(): SleepResult? = cachedResult

    suspend fun hasSleepPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext false
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet()).containsAll(READ_SLEEP_PERMISSIONS)
    }

    suspend fun fetchLatestSleep(context: Context, forceRefresh: Boolean = false): SleepResult {
        val isFresh = cachedResult != null && (System.currentTimeMillis() - cachedAtMillis) < STALE_AFTER_MILLIS
        if (isFresh && !forceRefresh) return cachedResult!!

        val result = fetchLatestSleepInternal(context)
        cachedResult = result
        cachedAtMillis = System.currentTimeMillis()
        return result
    }

    private suspend fun fetchLatestSleepInternal(context: Context): SleepResult = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext SleepResult.Unavailable
        if (!hasSleepPermission(context)) return@withContext SleepResult.NoPermission

        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(LOOKBACK), now),
            )
            val latest = client.readRecords(request).records.maxByOrNull { it.endTime }
                ?: return@withContext SleepResult.NoData

            if (latest.stages.isEmpty()) {
                return@withContext SleepResult.Success(
                    totalAsleep = Duration.between(latest.startTime, latest.endTime),
                    deep = null,
                    rem = null,
                    light = null,
                )
            }

            var deep = Duration.ZERO
            var rem = Duration.ZERO
            var light = Duration.ZERO
            var asleep = Duration.ZERO
            for (stage in latest.stages) {
                val stageDuration = Duration.between(stage.startTime, stage.endTime)
                when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> {
                        deep += stageDuration
                        asleep += stageDuration
                    }
                    SleepSessionRecord.STAGE_TYPE_REM -> {
                        rem += stageDuration
                        asleep += stageDuration
                    }
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> {
                        light += stageDuration
                        asleep += stageDuration
                    }
                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> asleep += stageDuration
                    // Excluded from "duration asleep": awake/out-of-bed/unknown time
                    // logged inside the same session window.
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_UNKNOWN -> Unit
                }
            }

            SleepResult.Success(totalAsleep = asleep, deep = deep, rem = rem, light = light)
        } catch (e: Exception) {
            SleepResult.Unavailable
        }
    }
}
