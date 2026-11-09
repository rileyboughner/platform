# platform

Unified platform monorepo: one design language and, eventually, one
launcher and one dashboard across laptop, desktop, server, phone,
tablet, and smart TV.

Repo: `rileyboughner/platform` (personal GitHub account — the
BonerLinux org is being retired).

## Layout

```
platform/
  theme/      # design tokens (Rosé Pine) + Style Dictionary build -> Nix, Android, CSS
  launcher/   # Android launcher (phone/tablet/Google TV) - not built yet
  dashboard/  # cross-device web dashboard - not built yet
  docs/       # architecture notes, device inventory, network/identity model
```

`dotfiles` (NixOS/home-manager, separate repo) stays the
machine-provisioning repo for server/desktop/laptop. This repo is
cross-device app code and design assets — a different kind of thing.

## Goal

One design language — colors, typography, spacing — defined once and
applied across every device: the NixOS/home-manager machines
(server/desktop/laptop), the Android launcher (phone/tablet/Google
TV), and any web dashboard. Define it once, generate per-target
outputs, instead of hand-maintaining copies that drift apart.

## Phases

- **Phase 1 — foundation** (in progress): scaffold this repo, define
  the design tokens (`theme/`) and the Style Dictionary build that
  generates Nix/home-manager, Android, and CSS outputs from them.
- **Phase 2 — launcher + per-device rollout** (not started): build the
  Android launcher against the Phase 1 tokens — phone and tablet
  layouts first, Google TV once its default-launcher support is
  confirmed for the specific model. Close the laptop dotfiles-sync gap
  found during discovery.
- **Phase 3 — cross-device features** (not started): presence-driven
  automations, and dashboard/launcher widgets sharing the same
  Jellyfin/Seerr/Home Assistant data sources.

## Current status

Phase 1 scaffolding in progress. `theme/` has tokens and a working
Style Dictionary build with generated output committed under
`theme/build/`. The launcher and dashboard apps have not been built —
see their respective READMEs.

## Recorded decisions

- **Repo location:** `rileyboughner/platform`, not `BonerLinux/platform`
  — the BonerLinux org is being retired.
- **Palette:** Rosé Pine, frozen as the fixed cross-device design
  identity. pywal stays as separate, local, wallpaper-driven flair on
  Linux desktop/laptop only — unrelated to these tokens.
- **Network fabric:** the WireGuard tunnel ("clownweb") is the fabric
  for Riley's own devices (server/desktop/laptop/router). Tailscale is
  used only for Muse's access to the LAN — the two are not
  alternatives to each other, both exist for different purposes.
- **Domain:** all services live on `rileyboughner.dev`.
  `virtualpicklenetwork.com` is not being renewed and is not a live
  domain for this platform.

See `docs/architecture.md` for the fuller picture (device inventory,
identity/SSO scope, network model).
