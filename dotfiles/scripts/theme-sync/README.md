# theme-sync

Keeps the pinned theme (and, for desktop/laptop, the active wallpaper) in
sync across devices, using `server` as an always-on hub. Opt-in, off by
default on every device.

Riley's design gives this a global choice between two sync modes:

- **unified** — every synced device shares one theme/wallpaper state.
- **split** — two independent groups: `workstation` (desktop + laptop,
  theme + wallpaper) and `mobile` (phone + tablet, theme only — see
  "Launcher" below). Each group last-write-wins on its own.

The mode is a single hub-wide setting (`boner sync mode unified|split`),
not per-device.

## Design

- **Hub** (`hub.py`, server only): a stdlib-only Python HTTP server, no
  external dependencies. Holds the shared state document, one state
  document per group, and the wallpaper blobs needed to apply any of them.
  Packaged and run via `../../nixos/modules/theme-sync-hub.nix`.
- **Watcher** (`../theme-sync-watch`, desktop + laptop only): a systemd
  user service that polls the hub every 5s, pushes local changes up, and
  pulls hub changes down — against the shared state in unified mode, or
  the `workstation` group's state in split mode. Packaged and run via
  `../../nixos/modules/theme-sync-watch.nix`.
- **Launcher sync client** (phone/tablet only, Kotlin —
  `dev.rileyboughner.launcher.theme.ThemeSyncRepository` in this repo's
  Android app): polls the hub the same way, against the `mobile` group's
  state in split mode. See "Launcher" below.
- **Toggle**: `boner sync on|off|status` on desktop/laptop, a Settings
  switch (default off) on the launcher. The flag lives at
  `~/.config/theme-sync/enabled` (one file per workstation) or in the
  launcher's own settings storage. Default: off, independently per device.

Neither `theme-set`/`set-wallpaper`/`theme-auto` nor the Quickshell theme
picker know sync exists. The watcher detects changes by polling the same
state files those scripts already write (`~/.cache/theme/current`, the
`~/.wallpaper` symlink) rather than the other way around, so there are no
hooks to keep in sync with that code.

## State document

```json
{
  "theme": "graphite",
  "pinned": true,
  "wallpaper": "some/path.jpg",
  "wallpaper_sha256": "...",
  "updated_at": 1700000000,
  "writer": "desktop"
}
```

Every state endpoint below (`/state` and each `/state/<group>`) holds one
of these, independently. `POST` always replaces the whole document --
there is no merging, so a client that only wants to change one field
(e.g. the launcher pushing a theme-only change) must carry forward the
other fields from the last document it saw. See each field:

- `theme` / `pinned`: mirrors `~/.cache/theme/current` -- `pinned: false`
  means unpinned/auto (wallpaper-driven pywal colors), `theme` is then
  `null`. The launcher never writes `pinned: false` -- there's no "auto"
  concept on Android, so it always pushes a named theme with `pinned: true`.
- `wallpaper`: the file's path relative to `~/.wallpapers` when it lives
  there (the common case -- both workstations clone the same collection),
  otherwise `sha256:<hash>`. The launcher never writes this field itself
  (phones/tablets don't push a wallpaper) -- it carries forward whatever
  value was already there when pushing a theme change.
- `wallpaper_sha256`: content hash of the wallpaper file, used both as
  the blob's storage key and to detect whether a local copy already
  matches.
- `updated_at`: unix seconds. The hub keeps the document with the larger
  `updated_at` -- last-write-wins, no merging.

## Groups and mode

Two fixed groups exist on the hub regardless of mode: `workstation`
(desktop + laptop) and `mobile` (phone + tablet). The mode
(`GET`/`POST /mode`, one of `"unified"`/`"split"`, default `"unified"`)
decides which state endpoint clients actually use:

- **unified**: every client reads/writes the single shared `/state`
  document, same as before groups existed.
- **split**: workstations read/write `/state/workstation`; the launcher
  reads/writes `/state/mobile`. The two groups' documents are completely
  independent -- each last-write-wins on its own, and a change in one
  group never touches the other.

Switching modes doesn't migrate or merge any existing document -- the
shared `/state` document and each group's `/state/<group>` document are
stored separately the whole time, so switching back to a mode you used
before picks up exactly where that mode's document left off.

## Protocol

Stdlib HTTP, no auth (LAN + tailnet only -- same posture as this host's
other private-network services, e.g. NFS/Samba). Binds to explicit
private addresses, never `0.0.0.0`.

- `GET /mode` -> `{"mode": "unified"}` or `{"mode": "split"}`.
- `POST /mode` -> body `{"mode": "unified"}` or `{"mode": "split"}`.
  Any other value is rejected with 400. Response: `{"mode": <the mode
  the hub now holds>}`.
- `GET /state` -> the shared (unified/back-compat) state document (`{}`
  if none yet).
- `POST /state` -> body is a state document for the shared document.
  Accepted (stored) if newer (`updated_at` greater than what's stored)
  than the current one; otherwise rejected. Response: `{"accepted":
  bool, "state": <the document the hub now holds>}`. A client whose push
  is rejected applies the returned `state` instead of clobbering it.
- `GET /state/<group>` -> same shape as `GET /state`, but for one
  group's document (`<group>` is `workstation` or `mobile`; anything else
  is 404).
- `POST /state/<group>` -> same semantics as `POST /state`, scoped to
  that group's document.
- `HEAD /wallpaper/<sha256>` -> 200 if the hub already has that blob, 404
  otherwise. Used before uploading, to skip redundant transfers.
- `GET /wallpaper/<sha256>` -> the raw file bytes, 404 if missing.
- `PUT /wallpaper/<sha256>` -> body is the raw file bytes. Hub verifies
  the uploaded bytes actually hash to `<sha256>` before storing (400 on
  mismatch).
- `GET /events` -> a Server-Sent Events stream, held open. The hub sends
  a `data: {"type": "sync"}` nudge immediately on connect, then a
  `data: {"type": "state-changed", "path": ...}` line on every accepted
  state write and `{"type": "mode-changed", ...}` on every mode write,
  plus a `: ping` heartbeat comment every 20s. Events are nudges, not
  the state itself: clients re-fetch their own state document over the
  `GET` endpoints above and apply it with the same compare-and-apply
  logic the polling path uses. Clients keep a slow (60s) fallback poll
  for anything the stream misses.

Wallpaper blobs are shared, content-addressed storage -- any state
document (shared or either group's) can reference any blob by its
sha256, so there's nothing group-specific about blob storage itself.

## Storage

`/var/lib/theme-sync` on the server (systemd `StateDirectory`, dynamic
user): `state.json` (the shared document), `groups.json` (`{"workstation":
{...}, "mobile": {...}}`), `mode.json` (`{"mode": "unified"}`), plus
`wallpapers/<sha256>` blobs.

## Loop prevention

Each watcher instance keeps its own record
(`~/.config/theme-sync/last-state.json`) of the last hub revision
(`updated_at`) it has fully pushed or pulled, tracked separately for the
theme half and the wallpaper half. A poll only pushes when the local
state differs from that record; applying a pulled hub state updates the
same record, so a pulled change is never mistaken for a new local change
and pushed straight back.

The launcher doesn't need this split: it never detects local changes by
polling its own state the way the watcher does (there's no local
wallpaper-change detection on Android), so it only ever pushes on an
explicit Settings save. It keeps one `updated_at` watermark (the hub
revision it last applied or had accepted) and simply skips applying any
hub document that isn't newer than that watermark.

## No-session safety

`set-wallpaper` waits on the swww daemon.
The watcher only attempts a wallpaper apply (push or pull) when a live session
exists (`theme_sync_hypr_session_active` in
`../lib/theme-sync-common.sh`); with no Hyprland session it still applies
the theme half and retries the wallpaper half on a later poll. It never
blocks forever and never crash-loops -- failures (hub down, blob missing,
apply failed) are logged and retried next poll, not treated as fatal.

`theme_sync_hypr_session_active` also exports `HYPRLAND_INSTANCE_SIGNATURE`
for the session it finds: the watcher runs as a systemd `--user` service
under `default.target`, not launched from inside Hyprland, so it never
inherits that variable the way an interactive shell does. Without it,
`set-wallpaper`/`theme-set`/`theme-auto`'s own `hyprctl`/`swww` calls
can't find the real socket even when a session exists.

## Hub reachability

The watcher, `boner sync status`/`mode`, and the launcher's sync client
all try the hub's LAN address first, then its tailnet (Tailscale)
MagicDNS name, and use whichever answers first (`theme_sync_find_hub` in
`../lib/theme-sync-common.sh`, candidates in `THEME_SYNC_HUB_URLS`; the
same two URLs are duplicated as constants in the launcher's
`ThemeSyncApi.kt`, since that client is Kotlin, not bash). This lets
laptop (or a phone off Wi-Fi) reach the hub over the tailnet when away
from home. The hub itself binds both the server's LAN address and its
tailnet address (`hosts.server.ip` / `hosts.server.tailnetIp` in
`hosts.nix`), set via `THEME_SYNC_HOSTS` in `theme-sync-hub.nix`.

A hub-unreachable moment is always handled gracefully, on every client:
the watcher and the launcher both keep whatever they last applied and
retry on the next poll (or, for the launcher, on resume); neither ever
crashes or blocks waiting for the hub.

## Launcher (phone/tablet)

`dev.rileyboughner.launcher.theme` in this repo's Android app:

- `ThemeSyncApi.kt` — raw HTTP client (stdlib `HttpURLConnection`, same
  shape as the rest of the launcher's network code), hub discovery as
  above.
- `ThemeSyncRepository.kt` — polls every ~5s while the launcher is in
  the foreground, plus once on resume (never while backgrounded, never
  on the UI thread). Pulls the `mobile` group's state in split mode, the
  shared state in unified mode; applies a newer wallpaper via
  `WallpaperManager.setBitmap` and the theme as an accent color (see
  below). Only ever *pushes* when Riley picks a theme in Settings — it
  never pushes a wallpaper, and never pushes in the background.
- `ThemePalette.kt` — maps each of the 11 theme keys in
  `dotfiles/config/themes/*.json` to that theme's `colors.color4` (read
  directly from those files, not invented) as the accent color.
  `MonoLauncherTheme` (the launcher's black-and-white theme) threads that
  color into `primary`/`secondary` only — every other color stays fixed
  black/white, so a synced theme shows up as an accent/highlight (e.g. a
  `Switch`, or the active page-indicator dot) rather than a redesign.
  Graphite keeps the launcher's plain white accent (no tint) rather than
  its literal (near-white-gray) `color4`.

Settings (phone/tablet) adds: a "Sync with hub" switch (off by default,
matching the workstation default), a theme picker (writes to the hub on
save), and a sync-scope picker (unified/split, also writes `/mode` on
save). The TV UI never loads any of this code — the sync client is only
reachable through `MainActivity`, and is additionally guarded by the
same `UiModeManager` television check `HomeRouterActivity` uses to keep
TV off the phone/tablet UI in the first place.

## Enabling

1. `boner rebuild` on `server` (brings up the hub).
2. `boner rebuild` on `desktop` and `laptop` (brings up the watcher,
   disabled by default).
3. Decide unified vs. split (optional — defaults to unified):
   `boner sync mode unified` or `boner sync mode split`.
4. On each workstation you want synced: `boner sync on`.
5. On each phone/tablet you want synced: install the APK, open Settings,
   turn on "Sync with hub", optionally pick a theme and/or sync scope,
   and save.

`boner sync status` shows whether sync is on, the group, the mode, the
last push/pull times recorded on that machine, and the hub's current
state for that machine's group (or "hub unreachable").
