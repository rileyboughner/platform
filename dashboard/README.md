# dashboard

v1: one glanceable page covering Jellyfin "continue watching," Seerr
request status, Home Assistant presence, server/container status, and
links to everything else.

Styled using the CSS custom properties generated in
`../theme/build/css/variables.css` — no separate theme implementation
(the Dockerfile pulls it straight from the theme build output).

Stack: a single Python stdlib HTTP server (`server.py`) serving the
static frontend and a `/api/all` endpoint that fans out to Jellyfin,
Seerr, Home Assistant, cAdvisor, and the Docker socket. No frameworks,
no build step.

## Secrets

- The Jellyfin API key is read server-side from the K8s Jellyfin
  sqlite database (`/mnt/tank/config/jellyfin/data/jellyfin.db`,
  mounted read-only). Never logged.
- The Seerr API key is read server-side from
  `/mnt/tank/services/jellyseer/settings.json` (`.main.apiKey`,
  mounted read-only). Never logged.
- Home Assistant requires a long-lived access token supplied via
  `HA_TOKEN` in `.env` (gitignored, see `.env.example`). If it's
  empty, the Home section shows an honest empty state instead of
  guessing or reusing another token.

## Run

```
cp .env.example .env   # fill in HA_TOKEN when ready
./up.sh
curl http://localhost:8099/
```

`up.sh` runs `docker compose up -d --build` and then connects the
container to the plain default `bridge` network, which is where
cAdvisor lives. That network has no embedded DNS and the Docker
engine refuses network-scoped aliases on it, so it can't be declared
in `docker-compose.yml`'s `networks:` block — compose would reject the
whole file. The dashboard resolves cAdvisor's address at runtime via
the read-only Docker socket mount instead of hardcoding it. Re-running
`up.sh` is safe any time the container is recreated.

## Known follow-ups

- Authentik gating hasn't been wired up yet for this host/dashboard
  combination (matches how the other services are currently exposed
  — see the root README for the plan).
- Home Assistant section stays empty until `HA_TOKEN` is set.
