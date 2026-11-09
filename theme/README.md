# theme

Design tokens for the platform's shared design language: colors (Rosé
Pine, frozen as the cross-device identity), type scale, spacing, and
radius. One source of truth, built with [Style
Dictionary](https://styledictionary.com) into per-target outputs so
Nix/home-manager, the Android launcher, and any web surface consume the
same values instead of three hand-copied palettes drifting apart.

This is unrelated to pywal: pywal stays wallpaper-driven, local-only
flair on the Linux desktop/laptop. These tokens are the fixed palette
that cross-device things (launcher, dashboard) build against.

Rosé Pine is frozen here as the cross-device identity, but it is no
longer the only -- or the default -- desktop look: `dotfiles/` has its
own switchable theme system (see `dotfiles/README.md`'s "Wallpaper &
theming" section and `dotfiles/config/themes/`), whose default is a
custom dark monochrome theme ("Graphite"), with Rosé Pine kept there
as one option among ~11. That default lives entirely in the theme
system's own files, not here -- `tokens.json` is not touched by it and
stays Rosé Pine-only, since it serves a different, cross-device
purpose (launcher/web) than the desktop pywal theme switcher.

## Layout

- `tokens.json` — the tokens themselves, in Style Dictionary's
  value/type format.
- `config.base.json` — platform/file definitions (plain JSON).
- `config.js` — the actual Style Dictionary config used at build time;
  it loads `config.base.json` and layers in the custom format functions
  from `formats/` (JSON alone can't hold function references, so the
  custom formats live in their own files and get wired in here).
- `formats/` — custom Style Dictionary formats: a Nix attrset, a plain
  Nix-importable JSON file, and a Compose `Color` object for Kotlin.
- `build/` — generated output. **Committed to git**, so consumers
  (home-manager, the Android launcher) don't need Node installed.

## What's a token, and how to add one

A token is one named design value (a color, a font size, a spacing
step, a radius) with a `type` and a `value`:

```json
"gold": { "value": "#f6c177", "type": "color" }
```

To add one:

1. Add it to `tokens.json`, grouped under `color`, `font`, `spacing`,
   or `radius` (add a new top-level group only if none of those fit).
2. If it's a color that should also get a semantic alias (e.g. a new
   state color), add the alias under `color.semantic` referencing the
   raw palette value, e.g. `"value": "{color.pine.value}"`.
3. Rebuild (`npm run build`) and commit both the token change and the
   regenerated files under `build/`.

Don't hand-edit anything under `build/` — it's overwritten on every
build.

## Rebuilding

```sh
npm install
npm run build
```

This runs `style-dictionary build --config config.js`, regenerating:

- `build/nix/theme.nix` — a Nix attrset of the color tokens, importable
  by a home-manager module.
- `build/nix/theme.json` — the same color tokens as plain JSON, for
  `builtins.fromJSON` / `lib.importJSON`.
- `build/android/colors.xml` — Android color resources.
- `build/android/Theme.kt` — a generated Kotlin object
  (`platform.theme.PlatformTheme`) exposing the palette as Jetpack
  Compose `Color` values. Generated — do not hand-edit.
- `build/css/variables.css` — CSS custom properties for colors,
  spacing, radius, and font sizes.

Node/npm aren't needed to *consume* the tokens — only to regenerate
`build/` after a change to `tokens.json`.
