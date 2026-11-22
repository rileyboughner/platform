#!/usr/bin/env python3
"""Dashboard backend: reads secrets server-side, never echoes them, and
proxies a handful of homelab APIs into one glanceable JSON payload."""

import datetime
import http.client
import json
import os
import shutil
import socket
import subprocess
import urllib.request
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

KUBECONFIG = os.environ.get("KUBECONFIG", "/kube/k3s.yaml")
K8S_API_SERVER = os.environ.get("K8S_API_SERVER", "https://192.168.1.2:6443")


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
    containers = {}

    try:
        raw = _docker_socket_json("/containers/json?all=1")
        for c in raw:
            name = (c.get("Names") or ["?"])[0].lstrip("/")
            containers[name] = {
                "name": name,
                "image": c.get("Image"),
                "state": c.get("State"),
                "status": c.get("Status"),
            }
    except Exception:
        result["containers_error"] = "Docker socket is not reachable; container state unavailable."

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

        result["host"] = {
            "cpu_percent": host_cpu_pct,
            "memory_used_bytes": host_mem_used,
            "memory_total_bytes": mem_capacity,
            "num_cores": num_cores,
        }
    except Exception:
        result["host_error"] = "cAdvisor is not reachable."

    result["containers"] = sorted(containers.values(), key=lambda c: c["name"])

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


# ------------------------------------------------------------- Kubernetes

def get_kubernetes():
    """Pods across all namespaces via kubectl (kubeconfig mounted read-only)."""
    try:
        out = subprocess.run(
            [
                "kubectl",
                "--kubeconfig", KUBECONFIG,
                "--server", K8S_API_SERVER,
                "get", "pods", "-A", "-o", "json",
            ],
            capture_output=True, text=True, timeout=15,
        )
    except Exception:
        return {"error": "kubectl is not available in the dashboard container."}
    if out.returncode != 0:
        return {"error": "Kubernetes API is not reachable."}
    try:
        data = json.loads(out.stdout)
    except Exception:
        return {"error": "Could not parse kubectl output."}
    pods = []
    for item in data.get("items", []):
        meta = item.get("metadata", {})
        status = item.get("status", {})
        container_statuses = status.get("containerStatuses", [])
        total = len(container_statuses)
        ready = sum(1 for c in container_statuses if c.get("ready"))
        restarts = sum(c.get("restartCount", 0) for c in container_statuses)
        pods.append({
            "namespace": meta.get("namespace", "?"),
            "name": meta.get("name", "?"),
            "ready": f"{ready}/{total}" if total else "—",
            "status": status.get("phase", "?"),
            "restarts": restarts,
        })
    pods.sort(key=lambda p: (p["namespace"], p["name"]))
    return {"pods": pods}


# -------------------------------------------------------------- HTTP server

CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
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

    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self._send_file(STATIC_DIR / "index.html", CONTENT_TYPES[".html"])
            return

        if self.path.startswith("/static/"):
            rel = self.path[len("/static/"):].split("?", 1)[0]
            target = (STATIC_DIR / rel).resolve()
            if STATIC_DIR.resolve() in target.parents and target.is_file():
                self._send_file(target, CONTENT_TYPES.get(target.suffix, "application/octet-stream"))
            else:
                self.send_response(404)
                self.end_headers()
            return

        if self.path == "/api/all":
            self._send_json({
                "seerr": get_seerr(),
                "server": get_server(),
                "kubernetes": get_kubernetes(),
            })
            return

        self.send_response(404)
        self.end_headers()


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
