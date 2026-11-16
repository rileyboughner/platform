#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

docker compose up -d --build

# cAdvisor lives on the plain default "bridge" network. That network has
# no embedded DNS and Docker refuses network-scoped aliases on it, so it
# can't be declared via compose's networks: -- attach it here instead.
# Safe to re-run; already-connected is a no-op.
docker network connect bridge dashboard 2>/dev/null || true
