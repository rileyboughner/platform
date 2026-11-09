# Shared helpers for the pywal-based theme system. Sourced (not executed)
# by set-wallpaper, theme-set, theme-auto and restore-theme so the
# derived-file generation and reload sequence live in exactly one place
# and can't drift between the wallpaper and theme-switcher code paths.

THEME_REPO_DIR="$HOME/.platform/dotfiles/config/themes"
THEME_USER_DIR="$HOME/.config/themes"
THEME_STATE_FILE="$HOME/.cache/theme/current"
THEME_WAL_JSON="$HOME/.cache/wal/colors.json"
THEME_HYPRTOOLKIT_CONFIG="$HOME/.config/hypr/hyprtoolkit.conf"
THEME_QT6CT_DIR="$HOME/.config/qt6ct"

# Prints the path to <name>.json. Checks the repo checkout first (so a
# plain `git pull` picks up new/updated themes with no stow/rebuild
# step), then falls back to ~/.config/themes/ for machine-local themes
# that aren't tracked in the dotfiles repo.
theme_resolve() {
    name="$1"
    if [ -f "$THEME_REPO_DIR/$name.json" ]; then
        printf '%s\n' "$THEME_REPO_DIR/$name.json"
    elif [ -f "$THEME_USER_DIR/$name.json" ]; then
        printf '%s\n' "$THEME_USER_DIR/$name.json"
    else
        return 1
    fi
}

# Lists available theme names (repo + user dirs, deduplicated, sorted).
# The trailing `true` keeps the group's exit status 0 even when
# ~/.config/themes doesn't exist (the common case) -- callers running
# under `set -e pipefail` (boner) shouldn't fail just because the
# optional user theme dir is absent.
theme_list() {
    {
        [ -d "$THEME_REPO_DIR" ] && find "$THEME_REPO_DIR" -maxdepth 1 -name "*.json" -printf "%f\n"
        [ -d "$THEME_USER_DIR" ] && find "$THEME_USER_DIR" -maxdepth 1 -name "*.json" -printf "%f\n"
        true
    } 2>/dev/null | sed 's/\.json$//' | sort -u
}

# Echoes the name of the currently-pinned theme. Fails (no output) if
# no theme is pinned -- callers treat that as "default to graphite".
theme_active_name() {
    [ -s "$THEME_STATE_FILE" ] || return 1
    cat "$THEME_STATE_FILE"
}

theme_save_state() {
    mkdir -p "$(dirname "$THEME_STATE_FILE")"
    printf '%s\n' "$1" >"$THEME_STATE_FILE"
}

theme_clear_state() {
    rm -f "$THEME_STATE_FILE"
}

# Applies a named theme through pywal itself: `wal --theme <file>` reads
# the JSON via pywal's own theme.file()/theme.parse(), fills in any
# missing checksum/wallpaper/alpha defaults, writes colors.json and
# renders every template under ~/.config/wal/templates -- the exact
# same export path `wal -i` uses. -n skips touching the wallpaper; -q
# keeps it quiet. (Verified against pywal16 3.8.15's own source:
# pywal/theme.py:file(), pywal/__main__.py:parse_args().)
theme_apply() {
    name="$1"
    file=$(theme_resolve "$name") || {
        echo "Unknown theme: $name" >&2
        echo "Available themes:" >&2
        theme_list | sed 's/^/  /' >&2
        return 1
    }
    wal --theme "$file" -n -q
}

# Regenerates the HyprToolkit + qt6ct color files from the current
# ~/.cache/wal/colors.json. Used by both set-wallpaper and the theme
# switcher so there is exactly one extraction, not two hand-written
# copies that can drift apart.
theme_write_derived() {
    background=$(jq -r '.special.background' "$THEME_WAL_JSON")
    foreground=$(jq -r '.special.foreground' "$THEME_WAL_JSON")
    color0=$(jq -r '.colors.color0' "$THEME_WAL_JSON")
    color1=$(jq -r '.colors.color1' "$THEME_WAL_JSON")
    color4=$(jq -r '.colors.color4' "$THEME_WAL_JSON")
    color5=$(jq -r '.colors.color5' "$THEME_WAL_JSON")
    color8=$(jq -r '.colors.color8' "$THEME_WAL_JSON")

    argb() {
        printf 'ff%s' "$(printf '%s' "$1" | tr -d '#' | tr 'a-f' 'A-F')"
    }

    mkdir -p "$(dirname "$THEME_HYPRTOOLKIT_CONFIG")"

    cat >"$THEME_HYPRTOOLKIT_CONFIG" <<EOF
background = 0xff${background#\#}
base = 0xff${color0#\#}
text = 0xff${foreground#\#}
alternate_base = 0xff${color8#\#}
bright_text = 0xff${foreground#\#}
accent = 0xff${color4#\#}
accent_secondary = 0xff${color5#\#}

h1_size = 22
h2_size = 17
h3_size = 14
font_size = 13
small_font_size = 11

icon_theme = "Papirus"
font_family = "Sans Serif"
font_family_monospace = "monospace"

rounding_large = 12
rounding_small = 8
EOF

    qt_bg=$(argb "$background")
    qt_fg=$(argb "$foreground")
    qt_muted=$(argb "$color8")
    qt_accent=$(argb "$color4")
    qt_accent2=$(argb "$color5")
    qt_red=$(argb "$color1")

    mkdir -p "$THEME_QT6CT_DIR/colors"

    # Color order follows QPalette::ColorRole (WindowText..PlaceholderText), as
    # read positionally by qt6ct's color scheme parser
    cat >"$THEME_QT6CT_DIR/colors/pywal.conf" <<EOF
[ColorScheme]
active_colors=#$qt_fg, #$qt_muted, #$qt_accent2, #$qt_accent, #$qt_muted, #$qt_bg, #$qt_fg, #$qt_accent2, #$qt_fg, #$qt_bg, #$qt_bg, #$qt_bg, #$qt_accent, #$qt_bg, #$qt_accent, #$qt_red, #$qt_muted, #$qt_fg, #$qt_bg, #$qt_fg, #$qt_muted
disabled_colors=#$qt_muted, #$qt_muted, #$qt_accent2, #$qt_accent, #$qt_muted, #$qt_bg, #$qt_muted, #$qt_muted, #$qt_muted, #$qt_bg, #$qt_bg, #$qt_bg, #$qt_accent, #$qt_muted, #$qt_accent, #$qt_red, #$qt_muted, #$qt_muted, #$qt_bg, #$qt_muted, #$qt_muted
inactive_colors=#$qt_fg, #$qt_muted, #$qt_accent2, #$qt_accent, #$qt_muted, #$qt_bg, #$qt_fg, #$qt_accent2, #$qt_fg, #$qt_bg, #$qt_bg, #$qt_bg, #$qt_accent, #$qt_bg, #$qt_accent, #$qt_red, #$qt_muted, #$qt_fg, #$qt_bg, #$qt_fg, #$qt_muted
EOF

    cat >"$THEME_QT6CT_DIR/qt6ct.conf" <<EOF
[Appearance]
style=Fusion
custom_palette=true
color_scheme_path=$THEME_QT6CT_DIR/colors/pywal.conf
icon_theme=Papirus
standard_dialogs=default
EOF
}

# Full reload sequence so every consumer (Hyprland, kitty, mako,
# swayosd, Quickshell, Neovim, hyprlauncher, the qt6ct-themed polkit
# dialog) picks up the new colors. This is what set-wallpaper always
# did by hand; theme-set/theme-auto/restore-theme now share it too.
theme_reload_all() {
    hyprctl reload

    makoctl reload

    # kitty has no cross-process broadcast socket by default -- each
    # `kitty` invocation (regular windows + pyprland scratchpads) is a
    # separate process. kitty.conf sets `listen_on
    # unix:/tmp/kitty-{kitty_pid}` specifically so every one of them
    # gets its own remote-control socket here; fan out to all of them.
    for sock in /tmp/kitty-*; do
        [ -S "$sock" ] || continue
        kitty @ --to "unix:$sock" set-colors --all "$HOME/.cache/wal/colors-kitty.conf" >/dev/null 2>&1 || true
    done

    pkill swayosd-server 2>/dev/null || true
    swayosd-server >/dev/null 2>&1 &

    pkill quickshell 2>/dev/null || true
    quickshell >/dev/null 2>&1 &

    pkill -SIGUSR1 nvim

    pkill hyprlauncher 2>/dev/null || true
    hyprlauncher -d >/dev/null 2>&1 &

    systemctl --user restart hyprpolkitagent.service
}
