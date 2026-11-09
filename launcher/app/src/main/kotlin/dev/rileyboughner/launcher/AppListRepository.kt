package dev.rileyboughner.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** One launchable app, as shown in the phone/tablet grid or TV row. */
data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    /** Badge count stub — real unread/notification counts are out of scope for this skeleton. */
    val badgeCount: Int = 0,
)

/**
 * Queries installed, launchable apps via [PackageManager]. This is the only
 * data source for the app grid/row in this skeleton — no caching, no
 * background refresh, no persistence. Good enough for a launcher stub;
 * revisit if the list query becomes a visible cold-start cost.
 */
class AppListRepository(private val context: Context) {

    fun getLaunchableApps(): List<LauncherAppInfo> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = packageManager.queryIntentActivities(mainIntent, 0)

        return resolvedActivities
            .map { resolveInfo ->
                LauncherAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = resolveInfo.loadIcon(packageManager),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
