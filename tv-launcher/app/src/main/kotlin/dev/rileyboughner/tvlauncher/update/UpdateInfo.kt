package dev.rileyboughner.tvlauncher.update

/** Parsed contents of `tv-launcher/version.json` — see `tv-launcher/UPDATES.md` for the schema and publish process. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)
