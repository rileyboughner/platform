<img width="1200" height="300" alt="WTF Homelab(1)" src="https://github.com/user-attachments/assets/2268c437-7e36-4ed9-8cbe-4311e699d16a" />


<div align="center">
  <img src="https://img.shields.io/badge/Servarr-e66001?style=for-the-badge&logo=BitTorrent&logoColor=white" alt="Servarr" />
  <img src="https://img.shields.io/badge/home%20assistant-%2341BDF5.svg?style=for-the-badge&logo=home-assistant&logoColor=white" alt="Home Assistant" />
  <img src="https://img.shields.io/badge/vaultwarden-%23175DDC.svg?style=for-the-badge&logo=bitwarden&logoColor=white" alt="Vaultwarden" />
  <img src="https://img.shields.io/badge/file%20server-18c249?style=for-the-badge&logo=Files&logoColor=white" alt="File Server" />
  <img src="https://img.shields.io/badge/immich-ed79b5?style=for-the-badge&logo=immich&logoColor=white" alt="Immich" />
  <img src="https://img.shields.io/badge/Jellyfin-9a5ec0?style=for-the-badge&logo=jellyfin&logoColor=white" alt="Jellyfin" />
  <img src="https://img.shields.io/badge/frigate-%23000000.svg?style=for-the-badge&logo=frigate&logoColor=white" alt="Frigate" />
</div>

## Overview
### Features
- Containers
- Virtual Machines

---
## 🚀 Getting Started
1. Create a fresh install of [nixos](https://nixos.org/download/)
2. Clone the Repo
```bash
git clone https://github.com/rileyboughner/homelab.git
```
3. Run the install script
```bash
cd homelab
sudo chmod +x install.sh
./install.sh
```
4. Reboot

---


## 📁 File Structure 
| Path | Purpose |
| --- | --- |
| `/mnt/tank` | Bulk storage location. |
| `/mnt/tank/<service>` | Defaut location of Container files. Config and database is stored within|
| `/mnt/tank/users/<user>` | User data mounted via SSHFS to clients. Migrating to NFS |
| `/srv/containers` | Docker Compose files |
| `/srv/websites` | Serving websites like a portfolio. |

---
## 🔒 Backups
**Status:** Work in progress.

---

## ✅ Dependencies
### Current
- **Docker Compose** (required)

### Planned
- **Kubernetes** (planned migration)
- **VM support** (planned for spinning up virtual machines)
- **Traefik** (planned for reverse proxy and load balancing)
- **CI/CD** (planned for automated deployments)
- **Terraform** (planned for infrastructure as code)

---

## 🧰 Docker Services
- [x] File syncing
- [x] Home Assistant
- [x] Immich
- [x] Frigate
- [x] Proxy
- [x] Arr stack
- [x] Vaultwarden
- [x] Dashboard
- [ ] AI
- [ ] Websites
- [ ] OpenTime
- [ ] Email
- [x] Git repo
- [ ] Container repo
- [ ] Notifications
- [ ] Authentication

## 🧰 Kubernetes Services
- [ ] Home Assistant
- [x] Immich
- [x] Nextcloud
- [x] Graphana
- [ ] Frigate
- [ ] Proxy
- [ ] Arr stack
- [x] Vaultwarden
- [ ] Dashboard
- [ ] AI
- [ ] Websites
- [ ] OpenTime
- [ ] Email
- [ ] Git repo
- [ ] Container repo
- [ ] Notifications
- [ ] Authentication

---

##  TODO
- [ ] enable auto mount of "/tank/mnt"
- [ ] create and enable nfs server
- [ ] migrate from docker to kubernetes
- [ ] complete install script
    - [x] figure out env vars
    - [ ] figure out setting symlinks
- [ ] create port map
- [x] standardize docker compose files
- [x] complete test of configuraion.nix
- [ ] create robust system for backups
- [ ] create status monitor script
- [ ] create global env var for config
- [ ] rethink naming conventions for "TANK_ROOT"

---


---

## Platform move (2026-11-21)

This tree is a fresh-commit copy of the `~/.homelab` working tree into the
platform repo. History was intentionally NOT carried over: the old repo's
history contains stale credentials, so this is a clean snapshot only.

- Live `.env` files were NOT copied. They stay on disk next to the compose
  files (gitignored, never committed) and must be present for a stack to
  start. The repo is public; no secret ever goes in it.
- Excluded from the copy: the abandoned `services/docker/openclaw/` draft,
  the removed `services/docker/vaultwarden/` service, the stale
  `services/docker/dashboard/` (the dashboard already lives at
  `platform/dashboard/`), plus all `.env*`, `*.bak`, `*.log`, key/cert/secret
  files.
- `~/containers/` (class, nextcloud) was copied to `platform/containers/`.
- The live services are being cut over to run from this repo. Data stays in
  `/mnt/tank/services/<name>/`; only the compose-file location changes.
