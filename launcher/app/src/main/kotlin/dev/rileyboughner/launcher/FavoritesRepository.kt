package dev.rileyboughner.launcher

/**
 * Single hardcoded source of truth for the home-page favorites list, until
 * a real "customize favorites" UI exists. To change the defaults, edit
 * [DEFAULT_FAVORITE_PACKAGES] — order here is the order shown on the Apps
 * page. Entries for apps that aren't installed are silently skipped.
 */
object FavoritesRepository {

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
        return DEFAULT_FAVORITE_PACKAGES.mapNotNull { byPackage[it] }
    }
}
