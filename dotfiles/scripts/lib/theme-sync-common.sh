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

# This repo's bash watcher only ever runs on desktop/laptop -- phones/tablets
# are the launcher's own (Kotlin) sync client, see
# launcher/.../theme/ThemeSyncRepository.kt -- so the group for every
# workstation is always "workstation", never computed or configurable.
THEME_SYNC_GROUP="workstation"

# Cached locally so a poll that can't reach the hub (or a quick repeat call,
# e.g. `boner sync status` right after a poll) doesn't need a fresh request
# just to know which state endpoint to use. Re-checked at most once per
# THEME_SYNC_MODE_CACHE_TTL seconds; the watcher's event-driven loop (60s
# fallback) is what makes a hub-side mode change actually take effect
# within ~1 cache window.
THEME_SYNC_MODE_CACHE_FILE="$THEME_SYNC_DIR/mode-cache.json"
THEME_SYNC_MODE_CACHE_TTL=60

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

# Echoes the hub's current sync mode ("unified" or "split"), using a short
# local cache (THEME_SYNC_MODE_CACHE_FILE/TTL above) to avoid hitting /mode
# on every call. Falls back to a stale cached value (any age) if the hub is
# unreachable, and to "unified" (the hub's own default) if there's no cache
# either -- never fails outright, since callers need *some* answer to pick
# a state endpoint.
theme_sync_get_mode() {
    local hub_url="$1" now cached_mode="" cached_at=0
    now=$(date +%s)
    if [ -f "$THEME_SYNC_MODE_CACHE_FILE" ]; then
        cached_mode=$(jq -r '.mode // ""' "$THEME_SYNC_MODE_CACHE_FILE" 2>/dev/null)
        cached_at=$(jq -r '.cached_at // 0' "$THEME_SYNC_MODE_CACHE_FILE" 2>/dev/null)
        if [ -n "$cached_mode" ] && [ $(( now - cached_at )) -lt "$THEME_SYNC_MODE_CACHE_TTL" ]; then
            printf '%s\n' "$cached_mode"
            return 0
        fi
    fi

    local fetched
    fetched=$(curl -sf --max-time 3 "$hub_url/mode" 2>/dev/null | jq -r '.mode // ""' 2>/dev/null)
    if [ "$fetched" = "unified" ] || [ "$fetched" = "split" ]; then
        mkdir -p "$THEME_SYNC_DIR"
        jq -n --arg mode "$fetched" --argjson cached_at "$now" '{mode: $mode, cached_at: $cached_at}' \
            >"$THEME_SYNC_MODE_CACHE_FILE"
        printf '%s\n' "$fetched"
        return 0
    fi

    if [ -n "$cached_mode" ]; then
        printf '%s\n' "$cached_mode"
        return 0
    fi
    printf 'unified\n'
}

# Maps a sync mode ($1) to the hub state path to use for this machine's
# group -- "state" (the shared/legacy document) in unified mode, or
# "state/$THEME_SYNC_GROUP" in split mode. Callers build the full URL as
# "$hub_url/$(theme_sync_state_path "$mode")".
theme_sync_state_path() {
    if [ "$1" = "split" ]; then
        printf 'state/%s\n' "$THEME_SYNC_GROUP"
    else
        printf 'state\n'
    fi
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
# now -- set-wallpaper waits on the awww daemon,
# so wallpaper pulls must only be attempted when a session exists.
#
# Also exports HYPRLAND_INSTANCE_SIGNATURE for the found session: this
# watcher process is started by systemd --user (default.target), not by
# Hyprland itself, so it never inherits that variable the way an
# interactively-run `theme-set`/`set-wallpaper` does. Delegates to
# theme_ensure_hypr_session (lib/theme-common.sh), which validates the
# current value and otherwise scans for a live session dir, skipping
# the stale leftovers a restarted compositor leaves behind.
theme_sync_hypr_session_active() {
    theme_ensure_hypr_session
}
