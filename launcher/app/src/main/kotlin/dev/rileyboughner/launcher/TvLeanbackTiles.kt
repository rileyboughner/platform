package dev.rileyboughner.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** One installed Leanback app tile on the TV home screen (Jellyfin, YouTube, ...). */
data class TvLeanbackTile(
    val packageName: String,
    val label: String,
    val artwork: Drawable,
    val launchIntent: Intent,
)

/**
 * Resolves a single TV home tile for an installed Leanback app, preferring
 * its TV banner artwork over its icon. Returns null — so the caller omits
 * the tile entirely — when the package isn't installed, matching the
 * "silently omit a tile if its app is not installed" requirement.
 *
 * Deliberately resolves via [PackageManager] directly rather than filtering
 * the phone/tablet app grid's [LauncherAppInfo] list (from
 * [AppListRepository.getLaunchableApps]): that list only queries
 * `CATEGORY_LAUNCHER`, which Leanback apps like Jellyfin/YouTube for
 * Android TV don't reliably declare — their launch intent only shows up via
 * [PackageManager.getLeanbackLaunchIntentForPackage].
 */
fun resolveLeanbackTile(context: Context, packageName: String, label: String): TvLeanbackTile? {
    val packageManager = context.packageManager
    return try {
        packageManager.getApplicationInfo(packageName, 0)

        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return null

        val resolvedActivityInfo = packageManager.resolveActivity(launchIntent, 0)?.activityInfo
        val artwork = resolvedActivityInfo?.let { activityInfo -> runCatching { activityInfo.loadBanner(packageManager) }.getOrNull() }
            ?: runCatching { packageManager.getApplicationBanner(packageName) }.getOrNull()
            ?: resolvedActivityInfo?.let { activityInfo -> runCatching { activityInfo.loadIcon(packageManager) }.getOrNull() }
            ?: runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null

        TvLeanbackTile(packageName, label, artwork, launchIntent)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
