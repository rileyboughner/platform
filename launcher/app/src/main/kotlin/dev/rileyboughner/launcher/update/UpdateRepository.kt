package dev.rileyboughner.launcher.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the launch-time update check for [dev.rileyboughner.launcher.MainActivity]
 * and [dev.rileyboughner.launcher.TvMainActivity] — see `launcher/UPDATES.md`.
 * Suppresses the prompt once Riley has
 * tapped "Later" for that exact versionCode; the dismissed versionCode is
 * remembered in a plain (non-secret) SharedPreferences file, separate from
 * [dev.rileyboughner.launcher.settings.SettingsRepository]'s encrypted
 * token storage, so a newer release still prompts. Any failure (offline,
 * GitHub down, malformed JSON) is swallowed here — callers get null and
 * the launcher opens exactly as if no check ran.
 */
class UpdateRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val info = UpdateApi.fetchVersionInfo()
            if (isUpdateAvailable(info.versionCode, currentVersionCode, getDismissedVersionCode())) info else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Manual check from Settings: ignores any dismissed version (an
     * explicit tap means "check anyway") and reports reachability instead
     * of swallowing it, so the screen can say "couldn't reach GitHub"
     * rather than wrongly claiming the app is up to date.
     */
    suspend fun checkForUpdateManual(currentVersionCode: Int): ManualUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val info = UpdateApi.fetchVersionInfo()
            if (info.versionCode > currentVersionCode) ManualUpdateCheckResult.Available(info)
            else ManualUpdateCheckResult.UpToDate
        } catch (e: Exception) {
            ManualUpdateCheckResult.Unreachable
        }
    }

    fun dismiss(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    private fun getDismissedVersionCode(): Int = prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1)

    companion object {
        private const val PREFS_FILE_NAME = "launcher_update_prefs"
        private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    }
}

/**
 * Result of an explicit, user-tapped update check (Settings) — unlike
 * [UpdateRepository.checkForUpdate], this distinguishes "up to date" from
 * "couldn't reach GitHub", and never suppresses a version for having been
 * dismissed with Later.
 */
sealed interface ManualUpdateCheckResult {
    data class Available(val info: UpdateInfo) : ManualUpdateCheckResult
    data object UpToDate : ManualUpdateCheckResult
    data object Unreachable : ManualUpdateCheckResult
}

/**
 * Pure comparison, split out of [UpdateRepository.checkForUpdate] so it's
 * exercised directly by a JVM unit test with no Android `Context` involved.
 */
internal fun isUpdateAvailable(remoteVersionCode: Int, currentVersionCode: Int, dismissedVersionCode: Int): Boolean =
    remoteVersionCode > currentVersionCode && remoteVersionCode != dismissedVersionCode
