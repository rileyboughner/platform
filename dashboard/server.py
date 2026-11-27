#!/usr/bin/env python3
"""Dashboard backend: reads secrets server-side, never echoes them, and
proxies a handful of homelab APIs into one glanceable JSON payload."""

import datetime
import hmac
import http.client
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import threading
import time
import urllib.parse
import urllib.request
from http import cookies as http_cookies
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = int(os.environ.get("PORT", "8099"))
STATIC_DIR = Path(__file__).parent / "static"

SEERR_URL = os.environ.get("SEERR_URL", "http://seerr:5055")
SEERR_SETTINGS_PATH = os.environ.get(
    "SEERR_SETTINGS_PATH", "/mnt/tank/services/jellyseer/settings.json"
)


CADVISOR_URL_OVERRIDE = os.environ.get("CADVISOR_URL", "").strip()
DOCKER_SOCKET = os.environ.get("DOCKER_SOCKET", "/var/run/docker.sock")
HOST_UPTIME_PATH = os.environ.get("HOST_UPTIME_PATH", "/host/proc-uptime")
DISK_PATH = os.environ.get("DISK_PATH", "/mnt/tank/services/jellyseer/settings.json")

CLOUDFLARED_CONFIG_PATH = os.environ.get(
    "CLOUDFLARED_CONFIG_PATH", "/mnt/tank/services/cloudflared/config.yml"
)

KUBECONFIG = os.environ.get("KUBECONFIG", "/kube/k3s.yaml")
K8S_API_SERVER = os.environ.get("K8S_API_SERVER", "https://192.168.1.2:6443")

DASHBOARD_PASSWORD = os.environ.get("DASHBOARD_PASSWORD", "")

# Friendly display names for services whose container/pod name does not say
# what the service does. Keys are the real container/pod names; values are
# what the Services list shows (and sorts/filters on). Add entries here as
# more cryptic names need explaining; anything not listed shows as-is.
DISPLAY_NAMES = {
    "epg": "EPG (TV Guide for Jellyfin)",
}


def display_name(name):
    return DISPLAY_NAMES.get(name, name)


# ------------------------------------------------------------------ groups --

# Services grouped by purpose for the Services card. Keys are real
# container/pod names (or, for Kubernetes, the Deployment/StatefulSet-ish
# prefix the pod name starts with); anything not listed falls into "Other".
# Regroup by editing this dict -- nothing else needs to change.
GROUP_ORDER = [
    "Media", "Downloads", "Photos", "Files & Sync", "Home", "Apps",
    "Network & Auth", "Monitoring", "System", "Other",
]

GROUPS = {
    "Media": [
        "jellyfin", "sonarr", "radarr", "lidarr", "prowlarr", "readarr",
        "audiobookshelf", "movie-wheel", "seerr", "epg",
    ],
    "Downloads": ["qbittorrent", "gluetun"],
    "Photos": [
        "immich_server", "immich_machine_learning", "immich_postgres", "immich_redis",
    ],
    "Files & Sync": ["nextcloud", "nextcloud-db", "nextcloud-redis", "syncthing"],
    "Home": ["homeassistant", "frigate"],
    "Apps": [
        "beast", "class", "dashboard", "minecraft", "windows",
        "docker-weave-web-1", "docker-weave-server-1", "homer",
    ],
    "Network & Auth": [
        "cloudflared", "authentik-server", "authentik-worker",
        "authentik-postgresql-0", "cadvisor",
    ],
    "Monitoring": [
        "monitoring-grafana", "alertmanager", "monitoring-kube-prometheus-operator",
        "monitoring-kube-state-metrics", "monitoring-prometheus-node-exporter", "prometheus",
    ],
    "System": [
        "coredns", "traefik", "svclb-traefik", "local-path-provisioner",
        "metrics-server", "helm-install-traefik-crd", "helm-install-traefik",
    ],
}

_IDENTIFIER_TO_GROUP = {}
for _group, _names in GROUPS.items():
    for _n in _names:
        _IDENTIFIER_TO_GROUP[_n] = _group
# Longest identifier first so a pod name like "helm-install-traefik-crd-abc"
# matches "helm-install-traefik-crd" rather than the shorter "helm-install-traefik".
_IDENTIFIERS_BY_LENGTH = sorted(_IDENTIFIER_TO_GROUP, key=len, reverse=True)


def group_for(raw_name, allow_prefix=False):
    group = _IDENTIFIER_TO_GROUP.get(raw_name)
    if group:
        return group
    if allow_prefix:
        for ident in _IDENTIFIERS_BY_LENGTH:
            if raw_name.startswith(ident):
                return _IDENTIFIER_TO_GROUP[ident]
    return "Other"


# ---------------------------------------------------------------- helpers --

def _http_json(url, timeout=5, method="GET", headers=None, body=None):
    req = urllib.request.Request(url, method=method, headers=headers or {}, data=body)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read())


class _UnixHTTPConnection(http.client.HTTPConnection):
    def __init__(self, path, timeout=5):
        super().__init__("localhost", timeout=timeout)
        self._unix_path = path

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self._unix_path)


def _docker_socket_json(path, timeout=5):
    conn = _UnixHTTPConnection(DOCKER_SOCKET, timeout=timeout)
    conn.request("GET", path)
    resp = conn.getresponse()
    data = json.loads(resp.read())
    conn.close()
    return data


def _docker_socket_post(path, timeout=10):
    conn = _UnixHTTPConnection(DOCKER_SOCKET, timeout=timeout)
    conn.request("POST", path, body=b"", headers={"Content-Length": "0"})
    resp = conn.getresponse()
    resp.read()
    status = resp.status
    conn.close()
    return status


def _docker_inspect(name, timeout=5):
    return _docker_socket_json(f"/containers/{name}/json", timeout=timeout)


def _ts_to_epoch(ts):
    base, _, frac = ts.rstrip("Z").partition(".")
    dt = datetime.datetime.strptime(base, "%Y-%m-%dT%H:%M:%S")
    micros = float("0." + frac) if frac else 0.0
    return dt.timestamp() + micros


def _cpu_percent(first_stat, last_stat, divisor=1):
    try:
        dt_ns = (_ts_to_epoch(last_stat["timestamp"]) - _ts_to_epoch(first_stat["timestamp"])) * 1e9
        d_cpu_ns = last_stat["cpu"]["usage"]["total"] - first_stat["cpu"]["usage"]["total"]
        if dt_ns <= 0:
            return None
        return round((d_cpu_ns / dt_ns) / divisor * 100, 1)
    except (KeyError, TypeError, ZeroDivisionError):
        return None


# --------------------------------------------------------------- Seerr ----

def _seerr_title(api_key, media_type, tmdb_id):
    path = "movie" if media_type == "movie" else "tv"
    field = "title" if media_type == "movie" else "name"
    try:
        data = _http_json(
            f"{SEERR_URL}/api/v1/{path}/{tmdb_id}",
            timeout=3,
            headers={"X-Api-Key": api_key},
        )
        return data.get(field) or f"{media_type} #{tmdb_id}"
    except Exception:
        return f"{media_type} #{tmdb_id}"


def _seerr_status_label(request_status, media_status):
    if request_status == 1:
        return "Pending approval"
    if request_status == 3:
        return "Declined"
    return {1: "Unknown", 2: "Pending", 3: "Processing", 4: "Partially available", 5: "Available"}.get(
        media_status, "Unknown"
    )


def get_seerr():
    try:
        with open(SEERR_SETTINGS_PATH) as f:
            settings = json.load(f)
        api_key = (settings.get("main") or {}).get("apiKey")
    except Exception:
        return {"error": "Could not read the Seerr settings file."}

    if not api_key:
        return {"error": "No Seerr API key configured."}

    try:
        data = _http_json(
            f"{SEERR_URL}/api/v1/request?take=25&sort=added",
            headers={"X-Api-Key": api_key},
        )
    except Exception:
        return {"error": f"Seerr at {SEERR_URL} is not reachable."}

    items = []
    for r in data.get("results", []):
        media = r.get("media") or {}
        media_type = r.get("type")
        status = _seerr_status_label(r.get("status"), media.get("status"))
        if status == "Available":
            continue
        tmdb_id = media.get("tmdbId")
        title = _seerr_title(api_key, media_type, tmdb_id) if tmdb_id else "Unknown title"
        items.append({
            "title": title,
            "type": media_type,
            "status": status,
        })
        if len(items) >= 8:
            break
    return {"items": items}


# ------------------------------------------------------------ Server status

def _cloudflared_port_map():
    """Public hostname per host port, from the cloudflared ingress config.
    Only http/https services are linkable; tcp entries (e.g. Minecraft)
    and the catch-all 404 are skipped. First hostname wins on a shared port."""
    port_map = {}
    try:
        with open(CLOUDFLARED_CONFIG_PATH) as f:
            lines = f.readlines()
    except Exception:
        return port_map
    hostname = None
    for raw_line in lines:
        line = raw_line.strip()
        if line.startswith("- hostname:"):
            hostname = line.split(":", 1)[1].strip()
        elif line.startswith("service:") and hostname:
            service = line.split(":", 1)[1].strip()
            if service.startswith("http://") or service.startswith("https://"):
                try:
                    port = int(service.rsplit(":", 1)[1])
                except ValueError:
                    port = None
                if port and port not in port_map:
                    port_map[port] = hostname
            hostname = None
    return port_map


def _cadvisor_base_url():
    """cAdvisor sits on the plain default "bridge" network, which has no
    embedded DNS, so resolve its reachable address via the Docker socket
    we already mount read-only rather than joining another network."""
    if CADVISOR_URL_OVERRIDE:
        return CADVISOR_URL_OVERRIDE
    info = _docker_socket_json("/containers/cadvisor/json", timeout=3)
    networks = info["NetworkSettings"]["Networks"]
    ip = next(iter(networks.values()))["IPAddress"]
    return f"http://{ip}:8080"


def get_server():
    result = {}

    try:
        cadvisor_url = _cadvisor_base_url()
        machine = _http_json(f"{cadvisor_url}/api/v1.3/machine")
        num_cores = machine.get("num_cores") or 1
        mem_capacity = machine.get("memory_capacity")

        root = _http_json(
            f"{cadvisor_url}/api/v1.3/containers/",
            method="POST",
            body=json.dumps({"num_stats": 2, "subcontainers": False}).encode(),
        )
        rstats = root.get("stats", [])
        host_cpu_pct = _cpu_percent(rstats[0], rstats[-1], num_cores) if len(rstats) >= 2 else None
        host_mem_used = rstats[-1].get("memory", {}).get("working_set") if rstats else None

        result["host"] = {
            "cpu_percent": host_cpu_pct,
            "memory_used_bytes": host_mem_used,
            "memory_total_bytes": mem_capacity,
            "num_cores": num_cores,
        }
    except Exception:
        result["host_error"] = "cAdvisor is not reachable."

    try:
        du = shutil.disk_usage(DISK_PATH)
        result["disk"] = {"total": du.total, "used": du.used, "free": du.free}
    except Exception:
        result["disk_error"] = "/mnt/tank is not reachable."

    try:
        with open(HOST_UPTIME_PATH) as f:
            result["uptime_seconds"] = float(f.read().split()[0])
    except Exception:
        result["uptime_error"] = "Host uptime is not available."

    return result


# ----------------------------------------------------------------- Docker

MAX_SANE_MEMORY_LIMIT = 2 ** 62  # above this, cgroups mean "no limit", not a real cap


def get_docker():
    """Containers via the Docker socket, enriched with cAdvisor CPU/RAM and
    a public URL when a published port has a cloudflared hostname."""
    port_map = _cloudflared_port_map()
    containers = {}
    try:
        raw = _docker_socket_json("/containers/json?all=1")
        for c in raw:
            name = (c.get("Names") or ["?"])[0].lstrip("/")
            public_ports = sorted({
                p.get("PublicPort")
                for p in c.get("Ports") or []
                if p.get("PublicPort")
            })
            url = None
            for port in public_ports:
                if port in port_map:
                    url = f"https://{port_map[port]}"
                    break
            containers[name] = {
                "name": display_name(name),
                "raw_name": name,
                "group": group_for(name),
                "image": c.get("Image"),
                "state": c.get("State"),
                "status": c.get("Status"),
                "url": url,
                "protected": name == "dashboard",
                "storage_bytes": _service_storage_bytes(("docker", name)),
            }
    except Exception:
        return {"error": "Docker socket is not reachable; container state unavailable."}

    result = {}
    try:
        cadvisor_url = _cadvisor_base_url()
        host_mem_total = None
        try:
            machine = _http_json(f"{cadvisor_url}/api/v1.3/machine", timeout=5)
            host_mem_total = machine.get("memory_capacity")
        except Exception:
            pass
        docker_stats = _http_json(
            f"{cadvisor_url}/api/v1.3/docker",
            timeout=8,
            method="POST",
            body=json.dumps({"num_stats": 2}).encode(),
        )
        for info in docker_stats.values():
            aliases = info.get("aliases") or []
            if not aliases:
                continue
            name = aliases[0]
            stats = info.get("stats", [])
            if not stats or name not in containers:
                continue
            cpu_pct = _cpu_percent(stats[0], stats[-1]) if len(stats) >= 2 else None
            mem = stats[-1].get("memory", {}).get("working_set")
            containers[name]["cpu_percent"] = cpu_pct
            containers[name]["memory_bytes"] = mem

            # cgroups report "no limit" as a huge sentinel (e.g. uint64 max),
            # not as 0/absent -- anything past a sane ceiling means unset.
            limit = ((info.get("spec") or {}).get("memory") or {}).get("limit")
            if not limit or limit <= 0 or limit > MAX_SANE_MEMORY_LIMIT:
                try:
                    inspect = _docker_inspect(name, timeout=3)
                    limit = (inspect.get("HostConfig") or {}).get("Memory")
                except Exception:
                    limit = None
            if not limit or limit <= 0 or limit > MAX_SANE_MEMORY_LIMIT:
                limit = host_mem_total
            containers[name]["memory_limit_bytes"] = limit
    except Exception:
        result["stats_error"] = "cAdvisor is not reachable; CPU/RAM unavailable."

    result["containers"] = sorted(containers.values(), key=lambda c: c["name"])
    return result


# ------------------------------------------------------------- Kubernetes

def _kubectl(args, timeout=15):
    return subprocess.run(
        ["kubectl", "--kubeconfig", KUBECONFIG, "--server", K8S_API_SERVER, *args],
        capture_output=True, text=True, timeout=timeout,
    )


def _k8s_owner_map():
    """(namespace, replicaset-name) -> owning Deployment name."""
    mapping = {}
    try:
        out = _kubectl(["get", "rs", "-A", "-o", "json"])
        if out.returncode != 0:
            return mapping
        data = json.loads(out.stdout)
    except Exception:
        return mapping
    for rs in data.get("items", []):
        meta = rs.get("metadata", {})
        ns = meta.get("namespace")
        rs_name = meta.get("name")
        for ref in meta.get("ownerReferences") or []:
            if ref.get("kind") == "Deployment":
                mapping[(ns, rs_name)] = ref.get("name")
                break
    return mapping


def _pod_owner(meta, rs_map):
    ns = meta.get("namespace")
    for ref in meta.get("ownerReferences") or []:
        kind = ref.get("kind")
        if kind == "StatefulSet":
            return {"kind": "statefulset", "name": ref.get("name"), "namespace": ns}
        if kind == "ReplicaSet":
            dep = rs_map.get((ns, ref.get("name")))
            if dep:
                return {"kind": "deployment", "name": dep, "namespace": ns}
    return None


def get_kubernetes():
    """Pods across all namespaces via kubectl (kubeconfig mounted read-only),
    with a public URL when a NodePort service selecting the pod has a
    cloudflared hostname, and scale-to-0/1 owner info for the toggle."""
    try:
        out = _kubectl(["get", "pods", "-A", "-o", "json"])
    except Exception:
        return {"error": "kubectl is not available in the dashboard container."}
    if out.returncode != 0:
        return {"error": "Kubernetes API is not reachable."}
    try:
        data = json.loads(out.stdout)
    except Exception:
        return {"error": "Could not parse kubectl output."}

    rs_map = _k8s_owner_map()

    # Public URL per pod, via NodePort services whose selector matches the
    # pod's labels and whose nodePort has a cloudflared hostname.
    pod_urls = {}
    try:
        port_map = _cloudflared_port_map()
        svc_out = _kubectl(["get", "svc", "-A", "-o", "json"])
        if svc_out.returncode == 0:
            svc_data = json.loads(svc_out.stdout)
            nodeport_services = []
            for svc in svc_data.get("items", []):
                spec = svc.get("spec", {})
                selector = spec.get("selector") or {}
                if not selector:
                    continue
                for port in spec.get("ports", []):
                    node_port = port.get("nodePort")
                    if node_port and node_port in port_map:
                        nodeport_services.append((selector, port_map[node_port]))
            for item in data.get("items", []):
                meta = item.get("metadata", {})
                labels = meta.get("labels") or {}
                for selector, hostname in nodeport_services:
                    if all(labels.get(k) == v for k, v in selector.items()):
                        pod_urls[f"{meta.get('namespace', '?')}/{meta.get('name', '?')}"] = (
                            f"https://{hostname}"
                        )
                        break
    except Exception:
        pass  # URLs are a bonus; pods still render without them.

    pods = []
    for item in data.get("items", []):
        meta = item.get("metadata", {})
        status = item.get("status", {})
        container_statuses = status.get("containerStatuses", [])
        total = len(container_statuses)
        ready = sum(1 for c in container_statuses if c.get("ready"))
        restarts = sum(c.get("restartCount", 0) for c in container_statuses)
        namespace = meta.get("namespace", "?")
        raw_name = meta.get("name", "?")
        pods.append({
            "namespace": namespace,
            "name": display_name(raw_name),
            "raw_name": raw_name,
            "group": group_for(raw_name, allow_prefix=True),
            "ready": f"{ready}/{total}" if total else "—",
            "status": status.get("phase", "?"),
            "restarts": restarts,
            "url": pod_urls.get(f"{namespace}/{raw_name}"),
            "owner": _pod_owner(meta, rs_map),
            "storage_bytes": _service_storage_bytes(("k8s", namespace, raw_name)),
        })

    # Scaling a Deployment/StatefulSet to 0 deletes its pod entirely, so a
    # workload we scaled down via the dashboard would otherwise vanish from
    # this list with no way to click Start again. Keep a placeholder row for
    # any owner we've scaled to 0 until a real pod for it reappears.
    represented_owners = {
        (p["owner"]["namespace"], p["owner"]["kind"], p["owner"]["name"])
        for p in pods if p["owner"]
    }
    with _replica_lock:
        stopped_snapshot = list(STOPPED_OWNERS.keys())
    for (namespace, kind, name) in stopped_snapshot:
        if (namespace, kind, name) in represented_owners:
            continue
        pods.append({
            "namespace": namespace,
            "name": display_name(name),
            "raw_name": name,
            "group": group_for(name, allow_prefix=True),
            "ready": "0/0",
            "status": "Scaled to 0",
            "restarts": 0,
            "url": None,
            "owner": {"kind": kind, "name": name, "namespace": namespace},
            "storage_bytes": _service_storage_bytes(("k8s", namespace, name)),
        })

    pods.sort(key=lambda p: (p["namespace"], p["name"]))
    return {"pods": pods}


def _k8s_get_replicas(namespace, kind, name):
    out = _kubectl(["get", kind, name, "-n", namespace, "-o", "jsonpath={.spec.replicas}"])
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    try:
        return int(out.stdout.strip())
    except ValueError:
        return 1


def _k8s_scale(namespace, kind, name, replicas):
    out = _kubectl(["scale", f"{kind}/{name}", "-n", namespace, f"--replicas={replicas}"])
    if out.returncode != 0:
        raise RuntimeError(out.stderr)


_replica_lock = threading.Lock()
ORIGINAL_REPLICAS = {}  # (namespace, kind, name) -> replica count before it was stopped
STOPPED_OWNERS = {}  # (namespace, kind, name) -> True while scaled to 0 by the dashboard


# --------------------------------------------------------------- Storage --

TANK_MOUNT = "/mnt/tank"
K3S_STORAGE_MOUNT = "/var/lib/rancher/k3s/storage"
STORAGE_REFRESH_SECONDS = 600
DU_TIMEOUT = 20

_storage_lock = threading.Lock()
_storage_state = {
    "service_paths": {},  # key -> [host paths]
    "path_bytes": {},     # host path -> bytes or None
    "ready": False,
}


def _du_bytes(path):
    try:
        out = subprocess.run(["du", "-sb", path], capture_output=True, text=True, timeout=DU_TIMEOUT)
        if out.returncode != 0:
            return None
        return int(out.stdout.split()[0])
    except Exception:
        return None


def _docker_mount_paths(raw_name):
    try:
        info = _docker_inspect(raw_name, timeout=5)
    except Exception:
        return []
    paths = set()
    for m in info.get("Mounts", []):
        src = m.get("Source")
        if src and src.startswith(TANK_MOUNT + "/"):
            paths.add(src)
    return sorted(paths)


def _compute_storage():
    service_paths = {}
    path_cache = {}

    try:
        raw = _docker_socket_json("/containers/json?all=1")
        names = [(c.get("Names") or ["?"])[0].lstrip("/") for c in raw]
    except Exception:
        names = []
    for name in names:
        paths = _docker_mount_paths(name)
        service_paths[("docker", name)] = paths
        for p in paths:
            path_cache.setdefault(p, None)

    try:
        pvc_out = _kubectl(["get", "pvc", "-A", "-o", "json"], timeout=20)
        pv_out = _kubectl(["get", "pv", "-o", "json"], timeout=20)
        pod_out = _kubectl(["get", "pods", "-A", "-o", "json"], timeout=20)
        pvc_data = json.loads(pvc_out.stdout)
        pv_data = json.loads(pv_out.stdout)
        pod_data = json.loads(pod_out.stdout)

        pv_hostpath = {}
        for pv in pv_data.get("items", []):
            pv_name = pv.get("metadata", {}).get("name")
            spec = pv.get("spec", {})
            hp = (spec.get("hostPath") or {}).get("path") or (spec.get("local") or {}).get("path")
            if pv_name and hp:
                pv_hostpath[pv_name] = hp

        pvc_to_hostpath = {}
        for pvc in pvc_data.get("items", []):
            ns = pvc.get("metadata", {}).get("namespace")
            name = pvc.get("metadata", {}).get("name")
            volname = pvc.get("spec", {}).get("volumeName")
            if ns and name and volname in pv_hostpath:
                pvc_to_hostpath[(ns, name)] = pv_hostpath[volname]

        for pod in pod_data.get("items", []):
            meta = pod.get("metadata", {})
            ns = meta.get("namespace")
            pod_name = meta.get("name")
            paths = set()
            for vol in pod.get("spec", {}).get("volumes", []) or []:
                claim = (vol.get("persistentVolumeClaim") or {}).get("claimName")
                if not claim:
                    continue
                hp = pvc_to_hostpath.get((ns, claim))
                if hp and hp.startswith(K3S_STORAGE_MOUNT):
                    paths.add(hp)
            service_paths[("k8s", ns, pod_name)] = sorted(paths)
            for p in paths:
                path_cache.setdefault(p, None)
    except Exception:
        pass

    for p in path_cache:
        path_cache[p] = _du_bytes(p)

    with _storage_lock:
        _storage_state["service_paths"] = service_paths
        _storage_state["path_bytes"] = path_cache
        _storage_state["ready"] = True


def _storage_loop():
    while True:
        try:
            _compute_storage()
        except Exception:
            pass
        time.sleep(STORAGE_REFRESH_SECONDS)


def _service_storage_bytes(key):
    with _storage_lock:
        paths = _storage_state["service_paths"].get(key)
        path_bytes = _storage_state["path_bytes"]
        if not paths:
            return None
        total = 0
        known = False
        for p in paths:
            b = path_bytes.get(p)
            if b is not None:
                total += b
                known = True
        return total if known else None


def _group_storage_totals(entries):
    """entries: iterable of (group, storage_key). Dedupes shared host paths
    within a group so a shared mount isn't counted twice."""
    with _storage_lock:
        path_bytes = dict(_storage_state["path_bytes"])
        service_paths = dict(_storage_state["service_paths"])
    totals = {}
    seen_paths = {}
    for group, key in entries:
        for p in service_paths.get(key, []):
            seen = seen_paths.setdefault(group, set())
            if p in seen:
                continue
            seen.add(p)
            b = path_bytes.get(p)
            if b is not None:
                totals[group] = totals.get(group, 0) + b
    return totals


def _storage_ready():
    with _storage_lock:
        return _storage_state["ready"]


# ------------------------------------------------------------------ auth --

SESSION_COOKIE = "dashboard_session"
SESSION_MAX_AGE = 30 * 24 * 3600  # 30 days

_sessions_lock = threading.Lock()
_sessions = {}  # token -> expiry epoch


def _create_session():
    token = secrets.token_urlsafe(32)
    with _sessions_lock:
        _sessions[token] = time.time() + SESSION_MAX_AGE
    return token


def _valid_session(token):
    if not token:
        return False
    with _sessions_lock:
        expiry = _sessions.get(token)
        if expiry is None:
            return False
        if expiry < time.time():
            del _sessions[token]
            return False
        return True


def _invalidate_session(token):
    with _sessions_lock:
        _sessions.pop(token, None)


def _render_login(error=None):
    error_html = f'<p class="error">{error}</p>' if error else ""
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dashboard login</title>
<style>
  :root {{
    --color-semantic-background: #0b0b0c;
    --color-semantic-surface: #141415;
    --color-semantic-overlay: #1e1e20;
    --color-semantic-text: #e6e6e6;
    --color-semantic-text-muted: #8a8a90;
    --color-semantic-border: #2e2e33;
    --color-semantic-error: #c07a7a;
    --color-semantic-accent: #d4d4d8;
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--color-semantic-background);
    color: var(--color-semantic-text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }}
  form {{
    background: var(--color-semantic-surface);
    border: 1px solid var(--color-semantic-border);
    border-radius: 8px;
    padding: 32px;
    width: 280px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }}
  h1 {{ margin: 0 0 8px 0; font-size: 20px; font-weight: 600; }}
  input {{
    background: var(--color-semantic-overlay);
    border: 1px solid var(--color-semantic-border);
    border-radius: 6px;
    color: var(--color-semantic-text);
    font-size: 14px;
    padding: 10px 12px;
  }}
  input:focus {{ outline: 1px solid var(--color-semantic-accent); }}
  button {{
    background: var(--color-semantic-accent);
    color: var(--color-semantic-background);
    border: none;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 600;
    padding: 10px 12px;
    cursor: pointer;
  }}
  .error {{ color: var(--color-semantic-error); font-size: 13px; margin: 0; }}
</style>
</head>
<body>
  <form method="POST" action="/login">
    <h1>Dashboard</h1>
    {error_html}
    <input type="password" name="password" placeholder="Password" autofocus>
    <button type="submit">Log in</button>
  </form>
</body>
</html>"""


# -------------------------------------------------------------- HTTP server

CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
}

DOCKER_NAME_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")
K8S_LABEL_RE = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
DOCKER_ACTION_RE = re.compile(r"^/api/docker/([^/]+)/(start|stop)$")
K8S_ACTION_RE = re.compile(r"^/api/k8s/([^/]+)/(deployment|statefulset)/([^/]+)/(start|stop)$")


def build_payload():
    docker_data = get_docker()
    k8s_data = get_kubernetes()

    entries = []
    for c in docker_data.get("containers", []):
        entries.append((c["group"], ("docker", c["raw_name"])))
    for p in k8s_data.get("pods", []):
        entries.append((p["group"], ("k8s", p["namespace"], p["raw_name"])))

    return {
        "seerr": get_seerr(),
        "server": get_server(),
        "docker": docker_data,
        "kubernetes": k8s_data,
        "group_order": GROUP_ORDER,
        "group_storage": _group_storage_totals(entries),
        "storage_ready": _storage_ready(),
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # keep upstream secrets (and noise) out of stdout entirely

    def _send_json(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def _send_file(self, path, content_type):
        body = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, html, status=200):
        body = html.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def _redirect(self, location):
        self.send_response(302)
        self.send_header("Location", location)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _get_cookie(self, name):
        header = self.headers.get("Cookie")
        if not header:
            return None
        jar = http_cookies.SimpleCookie()
        try:
            jar.load(header)
        except Exception:
            return None
        morsel = jar.get(name)
        return morsel.value if morsel else None

    def _is_authenticated(self):
        if not DASHBOARD_PASSWORD:
            return False
        return _valid_session(self._get_cookie(SESSION_COOKIE))

    def _read_form_body(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length) if length else b""
        return urllib.parse.parse_qs(raw.decode())

    def do_GET(self):
        path = self.path.split("?", 1)[0]

        if path == "/login":
            self._send_html(_render_login())
            return

        if not self._is_authenticated():
            if path.startswith("/api/"):
                self._send_json({"error": "unauthorized"}, status=401)
            else:
                self._redirect("/login")
            return

        if path == "/" or path == "/index.html":
            self._send_file(STATIC_DIR / "index.html", CONTENT_TYPES[".html"])
            return

        if path.startswith("/static/"):
            rel = path[len("/static/"):]
            target = (STATIC_DIR / rel).resolve()
            if STATIC_DIR.resolve() in target.parents and target.is_file():
                self._send_file(target, CONTENT_TYPES.get(target.suffix, "application/octet-stream"))
            else:
                self.send_response(404)
                self.end_headers()
            return

        if path == "/api/all":
            self._send_json(build_payload())
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        path = self.path.split("?", 1)[0]

        if path == "/login":
            self._handle_login_post()
            return
        if path == "/logout":
            self._handle_logout_post()
            return

        if not self._is_authenticated():
            self._send_json({"error": "unauthorized"}, status=401)
            return

        m = DOCKER_ACTION_RE.match(path)
        if m:
            self._handle_docker_action(m.group(1), m.group(2))
            return

        m = K8S_ACTION_RE.match(path)
        if m:
            self._handle_k8s_action(m.group(1), m.group(2), m.group(3), m.group(4))
            return

        self.send_response(404)
        self.end_headers()

    def _handle_login_post(self):
        fields = self._read_form_body()
        password = (fields.get("password") or [""])[0]
        if DASHBOARD_PASSWORD and hmac.compare_digest(password, DASHBOARD_PASSWORD):
            token = _create_session()
            self.send_response(302)
            self.send_header("Location", "/")
            self.send_header(
                "Set-Cookie",
                f"{SESSION_COOKIE}={token}; Path=/; Max-Age={SESSION_MAX_AGE}; HttpOnly; Secure; SameSite=Lax",
            )
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self._send_html(_render_login(error="Incorrect password."), status=401)

    def _handle_logout_post(self):
        token = self._get_cookie(SESSION_COOKIE)
        if token:
            _invalidate_session(token)
        self.send_response(302)
        self.send_header("Location", "/login")
        self.send_header(
            "Set-Cookie",
            f"{SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax",
        )
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _handle_docker_action(self, name, action):
        if not DOCKER_NAME_RE.match(name):
            self._send_json({"error": "invalid container name"}, status=400)
            return
        if name == "dashboard" and action == "stop":
            self._send_json({"error": "refusing to stop the dashboard itself"}, status=403)
            return
        try:
            status = _docker_socket_post(f"/containers/{name}/{action}")
        except Exception:
            self._send_json({"error": "docker socket request failed"}, status=502)
            return
        if status not in (204, 304):
            self._send_json({"error": f"docker returned HTTP {status}"}, status=502)
            return
        self._send_json({"ok": True})

    def _handle_k8s_action(self, namespace, kind, name, action):
        if not K8S_LABEL_RE.match(namespace) or not K8S_LABEL_RE.match(name):
            self._send_json({"error": "invalid namespace or name"}, status=400)
            return
        key = (namespace, kind, name)
        try:
            if action == "stop":
                with _replica_lock:
                    if key not in ORIGINAL_REPLICAS:
                        ORIGINAL_REPLICAS[key] = _k8s_get_replicas(namespace, kind, name)
                    STOPPED_OWNERS[key] = True
                _k8s_scale(namespace, kind, name, 0)
            else:
                with _replica_lock:
                    target = ORIGINAL_REPLICAS.get(key, 1)
                    STOPPED_OWNERS.pop(key, None)
                _k8s_scale(namespace, kind, name, target)
        except Exception:
            self._send_json({"error": "kubectl scale failed"}, status=502)
            return
        self._send_json({"ok": True})


def main():
    threading.Thread(target=_storage_loop, daemon=True).start()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
