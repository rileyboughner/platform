# dashboard

The cross-device web dashboard: Jellyfin "continue watching," Seerr
request status, Home Assistant presence, and links to everything else
— one page, meant to be glanceable from any device.

**Not built yet.** Planned for Phase 2/3, after the launcher phase
starts — see the root README and `../docs/architecture.md`.

## Planned shape

- Small web app, behind Authentik (same SSO scope as Grafana/Seerr/
  Homer).
- Styled using the CSS custom properties generated in
  `../theme/build/css/variables.css` — no separate theme
  implementation.
- Data sources: Jellyfin API, Seerr API, Home Assistant REST/WebSocket
  API — the same sources the launcher's home-controls panel will use.
