# theme-sync

Keeps the pinned theme and the active wallpaper in sync between `desktop`
and `laptop`, using `server` as an always-on hub. Opt-in, off by default.

## Design

- **Hub** (`hub.py`, server only): a stdlib-only Python HTTP server, no
  external dependencies. Holds one JSON state document plus the wallpaper
  blobs needed to apply it. Packaged and run via
  `../../nixos/modules/theme-sync-hub.nix`.
- **Watcher** (`../theme-sync-watch`, desktop + laptop only): a systemd
  user service that polls the hub every 15s, pushes local changes up, and
  pulls hub changes down. Packaged and run via
  `../../nixos/modules/theme-sync-watch.nix`.
- **Toggle**: `boner sync on|off|status`. The flag lives at
  `~/.config/theme-sync/enabled`, one file per machine. Default: absent,
  i.e. **off**.

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

- `theme` / `pinned`: mirrors `~/.cache/theme/current` -- `pinned: false`
  means unpinned/auto (wallpaper-driven pywal colors), `theme` is then
  `null`.
- `wallpaper`: the file's path relative to `~/.wallpapers` when it lives
  there (the common case -- both workstations clone the same collection),
  otherwise `sha256:<hash>`.
- `wallpaper_sha256`: content hash of the wallpaper file, used both as
  the blob's storage key and to detect whether a local copy already
  matches.
- `updated_at`: unix seconds. The hub keeps the document with the larger
  `updated_at` -- last-write-wins, no merging.

## Protocol

Stdlib HTTP, no auth (LAN + tailnet only -- same posture as this host's
other private-network services, e.g. NFS/Samba). Binds to explicit
private addresses, never `0.0.0.0`.

- `GET /state` -> current state document (`{}` if none yet).
- `POST /state` -> body is a state document. Accepted (stored) if newer
  (`updated_at` greater than what's stored) than the current one;
  otherwise rejected. Response: `{"accepted": bool, "state": <the
  document the hub now holds>}`. A client whose push is rejected applies
  the returned `state` instead of clobbering it.
- `HEAD /wallpaper/<sha256>` -> 200 if the hub already has that blob, 404
  otherwise. Used before uploading, to skip redundant transfers.
- `GET /wallpaper/<sha256>` -> the raw file bytes, 404 if missing.
- `PUT /wallpaper/<sha256>` -> body is the raw file bytes. Hub verifies
  the uploaded bytes actually hash to `<sha256>` before storing (400 on
  mismatch).

## Storage

`/var/lib/theme-sync` on the server (systemd `StateDirectory`, dynamic
user): `state.json` plus `wallpapers/<sha256>` blobs.

## Loop prevention

Each watcher instance keeps its own record
(`~/.config/theme-sync/last-state.json`) of the last hub revision
(`updated_at`) it has fully pushed or pulled, tracked separately for the
theme half and the wallpaper half. A poll only pushes when the local
state differs from that record; applying a pulled hub state updates the
same record, so a pulled change is never mistaken for a new local change
and pushed straight back.

## No-session safety

`set-wallpaper` blocks indefinitely waiting for hyprpaper's IPC socket.
The watcher only attempts a wallpaper apply (push or pull) when that
socket actually exists (`theme_sync_hypr_session_active` in
`../lib/theme-sync-common.sh`); with no Hyprland session it still applies
the theme half and retries the wallpaper half on a later poll. It never
blocks forever and never crash-loops -- failures (hub down, blob missing,
apply failed) are logged and retried next poll, not treated as fatal.

`theme_sync_hypr_session_active` also exports `HYPRLAND_INSTANCE_SIGNATURE`
for the session it finds: the watcher runs as a systemd `--user` service
under `default.target`, not launched from inside Hyprland, so it never
inherits that variable the way an interactive shell does. Without it,
`set-wallpaper`/`theme-set`/`theme-auto`'s own `hyprctl`/hyprpaper calls
can't find the real socket even when a session exists.

## Hub reachability

The watcher and `boner sync status` both try the hub's LAN address
first, then its tailnet (Tailscale) MagicDNS name, and use whichever
answers first (`theme_sync_find_hub` in `../lib/theme-sync-common.sh`,
candidates in `THEME_SYNC_HUB_URLS`). This lets laptop reach the hub
over the tailnet when it's away from home and off the LAN. The hub
itself binds both the server's LAN address and its tailnet address
(`hosts.server.ip` / `hosts.server.tailnetIp` in `hosts.nix`), set via
`THEME_SYNC_HOSTS` in `theme-sync-hub.nix`.

## Enabling

1. `boner rebuild` on `server` (brings up the hub).
2. `boner rebuild` on `desktop` and `laptop` (brings up the watcher,
   disabled by default).
3. On each workstation you want synced: `boner sync on`.

`boner sync status` shows whether sync is on, the last push/pull times
recorded on that machine, and the hub's current state (or "hub
unreachable").
