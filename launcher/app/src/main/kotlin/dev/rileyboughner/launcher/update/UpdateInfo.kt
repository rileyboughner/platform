package dev.rileyboughner.launcher.update

/** Parsed contents of `launcher/version.json` — see `launcher/UPDATES.md` for the schema and publish process. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)
