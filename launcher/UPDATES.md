# In-app updates

Riley wants to be *prompted* for updates, not auto-updated and not using
Obtainium. `MainActivity` (phone/tablet only — see below) fetches
`launcher/version.json` from this public repo's `main` branch over HTTPS on
every launch and compares `versionCode` against `BuildConfig.VERSION_CODE`.
If the remote `versionCode` is higher, it shows an "Update available"
prompt with two buttons: **Update** (downloads the APK and opens the system
installer) and **Later** (dismisses; that exact `versionCode` won't prompt
again, but a newer one will).

There is no server-side component. The version file and the APK both live
on GitHub (the version file in this repo, the APK as a GitHub Release
asset).

## `version.json`

Path: `launcher/version.json` (this directory, next to this file).
Fetched at: `https://raw.githubusercontent.com/rileyboughner/platform/main/launcher/version.json`.

Fields:

| field         | type   | meaning                                                             |
|---------------|--------|----------------------------------------------------------------------|
| `versionCode` | int    | Must match the `versionCode` baked into the release APK's manifest.  |
| `versionName` | string | Human-readable version shown in the update prompt.                   |
| `apkUrl`      | string | Direct URL to the GitHub Release asset for that build.               |
| `notes`       | string | Short release notes shown in the update prompt. Optional, may be "". |

The committed file always matches the latest published release. Releases
are cut from this repo's `main` branch; see "Publishing a new release"
below.

## versionCode scheme

Plain incrementing integer, starting at 1 (the first skeleton build).
Bump by exactly 1 for every release that should be installable as an
update, in `launcher/app/build.gradle.kts` (`defaultConfig.versionCode`).
Never reuse or decrease a versionCode, even if a release is later deleted
or superseded — the comparison in `UpdateRepository` is a strict `>`.

`versionName` stays human-readable (e.g. `0.2.0`) and does not need to be
strictly semver, but should read sensibly in the update prompt.

## Release build convention

Release APKs are the debug-signed `assembleDebug` build from the server
build machine — the same debug keystore as every ADB install, so a
release installs cleanly over an existing install with no uninstall and
no settings loss. Do not ship a release-signed APK: a different signing
key would force an uninstall/reinstall on every device.

Release builds MUST be built with the `LAUNCHER_HOME_ASSISTANT_TOKEN`,
`LAUNCHER_JELLYFIN_API_KEY`, and `LAUNCHER_TODOIST_TOKEN` environment
variables unset. Release assets are public (this repo is public), so no
token may be baked into them. Tokens are entered once in the launcher's
Settings screen instead; Settings stores them encrypted, and the stored
values survive updates. Before publishing, verify the APK contains no
token (count-only scan of the extracted dex against the token file) and
that `aapt dump badging` shows the expected `versionCode`/`versionName`.

## Publishing a new release

1. Bump `versionCode` and `versionName` in `launcher/app/build.gradle.kts`.
2. Build the APK to ship: clean `./gradlew assembleDebug` with all
   `LAUNCHER_*` token variables unset, then verify it per "Release build
   convention" above.
3. Create a GitHub Release on `rileyboughner/platform` (tag matching the
   new `versionName`, e.g. `v0.2.0`, targeting the release commit) and
   upload the APK as a release asset named `app-release.apk`.
4. Copy that asset's download URL
   (`https://github.com/rileyboughner/platform/releases/download/<tag>/app-release.apk`)
   into `launcher/version.json`'s `apkUrl`, and update `versionCode`,
   `versionName`, and `notes` to match.
5. Commit and push `launcher/version.json` to `main`. Existing installs
   pick it up on their next launch (allow a few minutes for
   `raw.githubusercontent.com`'s CDN cache to refresh).

## Where this runs

The check, the prompt, and the download/install flow live entirely in
`MainActivity` (phone/tablet). `TvMainActivity` never fetches
`version.json`, never shows the prompt, and never downloads anything — see
its kdoc. This is intentional: the TV build is handled separately.

## Install permission flow

Android requires `REQUEST_INSTALL_PACKAGES` and, per-app, "Allow from this
source" before an `ACTION_VIEW` install intent will work. If that's not
yet granted, the app sends Riley to
`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this app with a short
explanation instead of silently failing. One install tap is still required
after that — there's no way around that on a non-rooted, non-device-owner
phone, and this app doesn't try to work around it.
