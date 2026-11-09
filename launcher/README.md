# launcher

The single Android launcher codebase: phone, tablet, and Google TV
layouts in one app, built against the design tokens in `../theme/`
(the generated Compose `Color` object in
`theme/build/android/Theme.kt`).

**Not built yet. Phase 2 has not started.** This directory is a
placeholder until that phase begins — see the root README and
`../docs/architecture.md` for the plan.

## Planned shape

- One codebase, per-form-factor layouts: phone/tablet via Compose
  window-size-class variants, Google TV via its own
  `LEANBACK_LAUNCHER` entry point (D-pad navigation, 10-foot UI) —
  sharing the same data layer (Jellyfin API client, Home Assistant API
  client) and design tokens across all three.
- Rollout order: phone + tablet first, Google TV once the new device
  is in hand.

## Key risk

**Confirm the specific Google TV model actually supports setting a
third-party app as the default home launcher before buying it.** Some
Google TV OEM builds block switching the default launcher outright,
`adb shell cmd package set-home-activity` or not — this is the single
biggest risk to the whole launcher plan, since every other step is a
known-working Android flow.
