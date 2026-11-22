# TV Launcher — in-app updates

Separate from the phone launcher (`launcher/`): this app is
`dev.rileyboughner.tvlauncher`, versioned independently. It checks
`tv-launcher/version.json` from this repo's `main` branch over HTTPS on
every launch and prompts when the remote `versionCode` is higher. The
prompt offers **Update** (downloads the APK, opens the system installer)
or **Later** (dismisses that exact `versionCode`; a newer one prompts
again).

## `version.json`

Path: `tv-launcher/version.json` (this directory).
Fetched at: `https://raw.githubusercontent.com/rileyboughner/platform/main/tv-launcher/version.json`.

Fields: `versionCode` (int, must match the release APK), `versionName`
(string, shown in the prompt), `apkUrl` (direct GitHub Release asset
URL), `notes` (short release notes, optional).

## Publishing a new release

1. Bump `versionCode` and `versionName` in
   `tv-launcher/app/build.gradle.kts`.
2. Build: clean `./gradlew assembleDebug` from `tv-launcher/` (no
   `LAUNCHER_*` env vars — this project defines none, and no token may
   ever be baked into a public release asset). Verify with
   `aapt2 dump badging` (package `dev.rileyboughner.tvlauncher`,
   expected versionCode/versionName) and confirm no `ghp_` /
   `github_pat_` strings in the APK.
3. Create a GitHub Release on `rileyboughner/platform` with tag
   `tv-<versionName>` (e.g. `tv-0.1.0`), targeting the release commit,
   and upload the APK as `app-release.apk`.
4. Update `tv-launcher/version.json` (`apkUrl`, `versionCode`,
   `versionName`, `notes`), commit and push. Existing installs pick it
   up on next launch.

Release APKs are the debug-signed `assembleDebug` build — the same
debug keystore as every other install, so a release installs cleanly
over an existing install with no uninstall and no settings loss.
