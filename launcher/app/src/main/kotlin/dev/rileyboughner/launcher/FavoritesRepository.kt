package dev.rileyboughner.launcher

/**
 * Single hardcoded source of truth for the main-page favorites row, until a
 * real "customize favorites" UI exists. [FAVORITES] order is the order
 * shown on the main page. Each entry is resolved by exact package name
 * first (trying each listed package in order), falling back to a
 * case-insensitive label-contains match when the package is unknown or not
 * installed under that name — e.g. the "Muse" agent app, whose package
 * name isn't known ahead of time. An entry with no installed match is
 * silently skipped: a favorites row never shows a dead app.
 */
object FavoritesRepository {

    private class FavoriteSpec(
        val displayLabel: String,
        val packages: List<String> = emptyList(),
        val labelContains: String? = null,
        val preferredLabelContains: String? = null,
    )

    private val FAVORITES: List<FavoriteSpec> = listOf(
        FavoriteSpec("Phone", packages = listOf("com.google.android.dialer", "com.android.dialer")),
        FavoriteSpec("Messages", packages = listOf("com.google.android.apps.messaging", "com.android.mms")),
        FavoriteSpec("Camera", packages = listOf("com.google.android.GoogleCamera", "com.android.camera2")),
        FavoriteSpec("Email", packages = listOf("com.zoho.mail"), labelContains = "zoho"),
        FavoriteSpec("Outlook", packages = listOf("com.microsoft.office.outlook"), labelContains = "outlook"),
        FavoriteSpec("Photos", packages = listOf("app.alextran.immich"), labelContains = "immich"),
        FavoriteSpec("Workout", labelContains = "lift", preferredLabelContains = "lift log"),
        FavoriteSpec("Agent", labelContains = "muse", preferredLabelContains = "muse"),
        FavoriteSpec("Music", packages = listOf("com.Arturo254.opentune"), labelContains = "opentun"),
        FavoriteSpec("Podcasts", packages = listOf("com.spotify.music"), labelContains = "spotify"),
        FavoriteSpec("Obsidian", packages = listOf("md.obsidian")),
        FavoriteSpec("SongBook Pro", packages = listOf("com.songbookpro.songbookpro")),
    )

    fun resolveFavorites(allApps: List<LauncherAppInfo>): List<LauncherAppInfo> {
        val byPackage = allApps.associateBy { it.packageName }
        return FAVORITES.mapNotNull { spec -> resolve(spec, allApps, byPackage) }
    }

    private fun resolve(
        spec: FavoriteSpec,
        allApps: List<LauncherAppInfo>,
        byPackage: Map<String, LauncherAppInfo>,
    ): LauncherAppInfo? {
        val byPkg = spec.packages.firstNotNullOfOrNull { byPackage[it] }
        val match = byPkg ?: resolveByLabel(spec, allApps)
        return match?.copy(label = spec.displayLabel)
    }

    /**
     * Filters by the broad [FavoriteSpec.labelContains] substring, then
     * prefers whichever candidate also contains the narrower
     * [FavoriteSpec.preferredLabelContains] (e.g. "lift" → prefer "lift
     * log"; "muse" → prefer the exact app named "Muse"), falling back to
     * the first broad match if no candidate satisfies the narrower one.
     */
    private fun resolveByLabel(spec: FavoriteSpec, allApps: List<LauncherAppInfo>): LauncherAppInfo? {
        val labelContains = spec.labelContains ?: return null
        val candidates = allApps.filter { it.label.contains(labelContains, ignoreCase = true) }
        val preferred = spec.preferredLabelContains?.let { preference ->
            candidates.firstOrNull { it.label.equals(preference, ignoreCase = true) }
                ?: candidates.firstOrNull { it.label.contains(preference, ignoreCase = true) }
        }
        return preferred ?: candidates.firstOrNull()
    }
}
