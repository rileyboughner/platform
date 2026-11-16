package dev.rileyboughner.launcher

/**
 * Single hardcoded source of truth for the home-page favorites list, until
 * a real "customize favorites" UI exists. To change the defaults, edit
 * [DEFAULT_FAVORITE_PACKAGES] — order here is the order shown on the Apps
 * page. Entries for apps that aren't installed are silently skipped.
 *
 * The lift-log app is resolved differently: its package name isn't known
 * ahead of time, so it's matched by label at runtime (see
 * [resolveLiftLogApp]) and appended after the pinned-by-package favorites,
 * shown as "workout" instead of its own label. Same silent-skip behavior
 * if no match is installed.
 */
object FavoritesRepository {

    private const val LIFT_LOG_DISPLAY_LABEL = "workout"

    val DEFAULT_FAVORITE_PACKAGES: List<String> = listOf(
        // Phone
        "com.google.android.dialer",
        "com.android.dialer",
        // Messages
        "com.google.android.apps.messaging",
        "com.android.mms",
        // Browser
        "com.vivaldi.browser",
        // Camera
        "com.google.android.GoogleCamera",
        "com.android.camera2",
        // A few other common ones
        "com.google.android.gm",
        "com.google.android.apps.photos",
    )

    fun resolveFavorites(allApps: List<LauncherAppInfo>): List<LauncherAppInfo> {
        val byPackage = allApps.associateBy { it.packageName }
        val pinned = DEFAULT_FAVORITE_PACKAGES.mapNotNull { byPackage[it] }
        val liftLog = resolveLiftLogApp(allApps)?.copy(label = LIFT_LOG_DISPLAY_LABEL)
        return if (liftLog != null) pinned + liftLog else pinned
    }

    /** Matches by label containing "lift" (case-insensitive), preferring one containing "lift log". */
    private fun resolveLiftLogApp(allApps: List<LauncherAppInfo>): LauncherAppInfo? {
        val candidates = allApps.filter { it.label.contains("lift", ignoreCase = true) }
        return candidates.firstOrNull { it.label.contains("lift log", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }
}
