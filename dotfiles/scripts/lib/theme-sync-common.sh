# shellcheck shell=bash
# Shared helpers for the theme/wallpaper sync hub client. Sourced (not
# executed) by theme-sync-watch and by `boner sync status`, so hub
# discovery and local-state reading can't drift between the two.
#
# Deliberately NOT sourced into theme-set/set-wallpaper/theme-auto: those
# scripts stay sync-unaware (see theme-sync/README.md) -- the watcher
# detects their effects by polling the same state files they already
# write, rather than the other way around.

# Callers are expected to set DOTFILES_TARGET and source this the same
# way boner/theme-set do, before sourcing this file:
#   DOTFILES_TARGET="$HOME/.platform/dotfiles"
#   . "$DOTFILES_TARGET/scripts/lib/theme-sync-common.sh"
# shellcheck source=theme-common.sh
. "$DOTFILES_TARGET/scripts/lib/theme-common.sh"

# LAN address first, tailnet (Tailscale) MagicDNS name second --
# theme-sync-watch and `boner sync status` try every URL in this list in
# order and use the first that answers (theme_sync_find_hub below), so a
# workstation off the LAN (laptop away from home) still reaches the hub
# over the tailnet.
THEME_SYNC_HUB_PORT=8765
THEME_SYNC_HUB_URLS="http://192.168.1.2:${THEME_SYNC_HUB_PORT} http://server.taila93a39.ts.net:${THEME_SYNC_HUB_PORT}"

# shellcheck disable=SC2034 # read by theme-sync-watch and boner, not this file
THEME_SYNC_DIR="$HOME/.config/theme-sync"
THEME_SYNC_ENABLED_FILE="$THEME_SYNC_DIR/enabled"
# shellcheck disable=SC2034 # read by theme-sync-watch, not this file
THEME_SYNC_STATE_FILE="$THEME_SYNC_DIR/last-state.json"
THEME_SYNC_WALLPAPERS_DIR="$HOME/.wallpapers"
THEME_SYNC_BLOB_CACHE_DIR="$HOME/.cache/theme-sync/blobs"

# Exit status 0 == sync is enabled on this machine. Default-off: an
# absent flag file (the state on every machine until `boner sync on` is
# run) means disabled, not enabled.
theme_sync_enabled() {
    [ -f "$THEME_SYNC_ENABLED_FILE" ] && [ "$(cat "$THEME_SYNC_ENABLED_FILE")" = "on" ]
}

theme_sync_set_enabled() {
    mkdir -p "$THEME_SYNC_DIR"
    printf '%s\n' "$1" >"$THEME_SYNC_ENABLED_FILE"
}

theme_sync_hostname() {
    hostname -s 2>/dev/null || hostname
}

# Echoes the first hub URL that answers GET /state within 2s, trying each
# URL in THEME_SYNC_HUB_URLS in order (LAN before tailnet). Fails with no
# output if none answer -- callers treat that as "hub unreachable, back
# off quietly and retry next poll".
theme_sync_find_hub() {
    for url in $THEME_SYNC_HUB_URLS; do
        if curl -sf --max-time 2 "$url/state" >/dev/null 2>&1; then
            printf '%s\n' "$url"
            return 0
        fi
    done
    return 1
}

# Resolves the wallpaper ~/.wallpaper currently points at and sets
# THEME_SYNC_LOCAL_WP_{TARGET,SHA256,ID} (cleared first). Returns 1 with
# all three left empty if the symlink is absent/broken. Globals rather
# than a printed line: wallpaper filenames can contain spaces, which a
# single-line "sha256 identifier" result would mis-split.
#
# ID is the path relative to ~/.wallpapers when the target lives there
# (the common case -- both workstations clone the same collection),
# otherwise "sha256:<hash>" per the hub's wallpaper-identifier spec.
theme_sync_local_wallpaper() {
    THEME_SYNC_LOCAL_WP_TARGET=""
    THEME_SYNC_LOCAL_WP_SHA256=""
    THEME_SYNC_LOCAL_WP_ID=""

    [ -L "$HOME/.wallpaper" ] || return 1
    local target
    target=$(readlink -f "$HOME/.wallpaper") || return 1
    [ -f "$target" ] || return 1

    # shellcheck disable=SC2034 # read by theme-sync-watch, not this file
    THEME_SYNC_LOCAL_WP_TARGET="$target"
    THEME_SYNC_LOCAL_WP_SHA256=$(sha256sum "$target" | cut -d' ' -f1)

    case "$target" in
        "$THEME_SYNC_WALLPAPERS_DIR"/*)
            # shellcheck disable=SC2034 # read by theme-sync-watch, not this file
            THEME_SYNC_LOCAL_WP_ID="${target#"$THEME_SYNC_WALLPAPERS_DIR"/}"
            ;;
        *)
            # shellcheck disable=SC2034 # read by theme-sync-watch, not this file
            THEME_SYNC_LOCAL_WP_ID="sha256:$THEME_SYNC_LOCAL_WP_SHA256"
            ;;
    esac
}

# Prints the local filesystem path a given hub wallpaper identifier would
# live at on this machine (not necessarily existing yet).
theme_sync_wallpaper_path_for_id() {
    case "$1" in
        sha256:*) printf '%s\n' "$THEME_SYNC_BLOB_CACHE_DIR/${1#sha256:}" ;;
        *) printf '%s\n' "$THEME_SYNC_WALLPAPERS_DIR/$1" ;;
    esac
}

# True if the file at path $1 exists and already hashes to sha256 $2.
theme_sync_wallpaper_matches() {
    [ -f "$1" ] || return 1
    [ "$(sha256sum "$1" | cut -d' ' -f1)" = "$2" ]
}

# True if a Hyprland session is actually running on this machine right
# now -- set-wallpaper blocks indefinitely waiting for hyprpaper's IPC
# socket, so wallpaper pulls must only be attempted when it exists.
#
# Also exports HYPRLAND_INSTANCE_SIGNATURE for the found session: this
# watcher process is started by systemd --user (default.target), not by
# Hyprland itself, so it never inherits that variable the way an
# interactively-run `theme-set`/`set-wallpaper` does. Without it,
# set-wallpaper's own socket wait (and hyprctl/theme_reload_all's calls)
# resolve a socket path with an empty signature and silently can't find
# the real one.
theme_sync_hypr_session_active() {
    local sock sig
    for sock in /run/user/"$(id -u)"/hypr/*/.hyprpaper.sock; do
        [ -S "$sock" ] || continue
        sig=$(basename "$(dirname "$sock")")
        export HYPRLAND_INSTANCE_SIGNATURE="$sig"
        return 0
    done
    return 1
}
