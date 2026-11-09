# Architecture

Living doc. Supersedes the original discovery/proposal writeup for
anything that changed after it was drafted — see "Recorded decisions"
in the root README for the two things that did.

## Device inventory

| Device | Status |
|---|---|
| server (`192.168.1.2`) | NixOS, source of truth for the shared dotfiles flake |
| desktop (`192.168.1.4`) | NixOS, in sync with server |
| laptop (clownweb `10.0.0.3`) | NixOS, reachable over the clownweb WireGuard tunnel when off the home LAN; this server's keys aren't authorized on it yet, so dotfiles sync is unconfirmed |
| phone — Pixel 10 Pro | Android; launcher target; Home Assistant Companion app reports presence |
| tablet — believed Galaxy Tab S9 | Android, rooted; model not confirmed; launcher target |
| TV (current) — Samsung Tizen (`192.168.1.122`) | Tizen, bundles its own Jellyfin UI, can't host a custom launcher; being replaced for exactly that reason |
| TV (incoming) — Google TV, not yet purchased | Android-based; the actual launcher target; **confirm the specific model supports setting a third-party default launcher before buying** |

## Design language

One tokens file (`theme/tokens.json`: Rosé Pine colors, type scale,
spacing, radius) built with Style Dictionary into per-target outputs —
a Nix attrset/JSON for home-manager, Android `colors.xml` +
a generated Compose `Color` object, and CSS custom properties. Define
once, generate N outputs, instead of hand-copying hex values across
three ecosystems.

Rosé Pine is frozen as the fixed cross-device identity. It does not
replace pywal, which stays wallpaper-reactive, local-only flair on
Linux desktop/laptop.

Limits: apps the launcher only launches into (Jellyfin, Home Assistant
Companion, Google Messages) aren't retheme-able beyond what they
expose themselves.

## Launcher plan

One Android codebase, not three apps. Phone and tablet differ mainly
in screen space, handled as Compose window-size-class layout variants.
Google TV needs its own `LEANBACK_LAUNCHER` entry point (D-pad
navigation, 10-foot UI) inside the same app, sharing the data layer
(Jellyfin API client, Home Assistant API client) and tokens with the
phone/tablet layouts.

Rollout order: phone + tablet first (testable now, no new hardware
needed), Google TV once the new device is in hand and its
default-launcher support is confirmed for that specific model — some
Google TV OEM builds block switching the default launcher outright,
which is the single biggest risk to this plan.

## Identity / SSO

Authentik (`auth.rileyboughner.dev`) fronts:

- Grafana
- Seerr
- Homer
- the future cross-device dashboard

Authentik does **not** front Jellyfin, Home Assistant, or Immich (each
has its own auth plus native/TV apps that expect direct login), or the
arr-stack admin UIs (LAN-only already, no reason to add a login
screen). Vaultwarden is not currently deployed; if it comes back, it
stays local-only rather than behind Authentik SSO.

## Network

- **clownweb** — WireGuard tunnel (`10.0.0.0/24`), the fabric between
  Riley's own devices (server/desktop/laptop/router), auto-brought-up
  by NetworkManager off the home Wi-Fi.
- **Tailscale** — used only for Muse's access to the LAN. Not an
  alternative fabric for Riley's devices.
- **Cloudflare Tunnel** — exposes the public-facing services on
  `rileyboughner.dev`.
- Everything LAN-only today (arr-stack UIs, qBittorrent, Prowlarr,
  Grafana's raw NodePort, Homer) stays LAN-only — no reason to expose
  indexer/torrent UIs to the internet for a one-person setup.

`virtualpicklenetwork.com` is being retired, not renewed; it is not a
live domain for this platform.
