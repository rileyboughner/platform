#!/usr/bin/env python3
# Theme-sync hub: stdlib-only HTTP server holding theme/wallpaper state
# documents plus the wallpaper blobs clients need to apply them. See
# README.md next to this file for the full protocol.
#
# State comes in three flavors, each last-write-wins independently:
#   - the legacy shared document (GET/POST /state) -- back-compat, used by
#     any client that doesn't care about groups.
#   - one document per group (GET/POST /state/<group>, group one of
#     "workstation"/"mobile") -- used when the hub's mode is "split".
# A global /mode (GET/POST, "unified" or "split") tells clients which of
# "the shared document" or "their own group's document" to read/write.
# Wallpaper blobs are content-addressed by sha256 and shared across all of
# the above -- any state document can reference any blob, so there's
# nothing group-specific about blob storage.
import hashlib
import json
import os
import socketserver
import sys
import threading
from http.server import BaseHTTPRequestHandler

STATE_DIR = os.environ.get("THEME_SYNC_STATE_DIR", "/var/lib/theme-sync")
STATE_FILE = os.path.join(STATE_DIR, "state.json")
GROUPS_FILE = os.path.join(STATE_DIR, "groups.json")
MODE_FILE = os.path.join(STATE_DIR, "mode.json")
BLOB_DIR = os.path.join(STATE_DIR, "wallpapers")
PORT = int(os.environ.get("THEME_SYNC_PORT", "8765"))
# Comma-separated list of addresses to bind to. One listening socket per
# address -- deliberately not "0.0.0.0", so the hub is only ever reachable
# from the private addresses it's explicitly told about (LAN + tailnet,
# see theme-sync-hub.nix / README.md).
HOSTS = [h.strip() for h in os.environ.get("THEME_SYNC_HOSTS", "127.0.0.1").split(",") if h.strip()]

REQUIRED_FIELDS = {"theme", "pinned", "wallpaper", "wallpaper_sha256", "updated_at", "writer"}
GROUPS = ("workstation", "mobile")
MODES = ("unified", "split")
DEFAULT_MODE = "unified"

# Guards the in-process read-modify-write of state.json/groups.json/mode.json
# (POST /state, /state/<group>, /mode) and blob writes, since each bound
# address runs its own server instance on its own thread.
_lock = threading.Lock()


def _read_json(path):
    try:
        with open(path, "r") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def _write_json(path, obj):
    os.makedirs(STATE_DIR, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(obj, f)
    os.replace(tmp, path)


def _read_state():
    return _read_json(STATE_FILE)


def _write_state(state):
    _write_json(STATE_FILE, state)


def _read_group_state(group):
    groups = _read_json(GROUPS_FILE) or {}
    return groups.get(group)


def _write_group_state(group, state):
    groups = _read_json(GROUPS_FILE) or {}
    groups[group] = state
    _write_json(GROUPS_FILE, groups)


def _read_mode():
    stored = _read_json(MODE_FILE)
    if stored and stored.get("mode") in MODES:
        return stored["mode"]
    return DEFAULT_MODE


def _write_mode(mode):
    _write_json(MODE_FILE, {"mode": mode})


class Handler(BaseHTTPRequestHandler):
    server_version = "theme-sync-hub/1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send_json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/state":
            with _lock:
                state = _read_state()
            self._send_json(200, state if state is not None else {})
            return
        if self.path.startswith("/state/"):
            group = self.path[len("/state/") :]
            if group not in GROUPS:
                self._send_json(404, {"error": "unknown group", "groups": list(GROUPS)})
                return
            with _lock:
                state = _read_group_state(group)
            self._send_json(200, state if state is not None else {})
            return
        if self.path == "/mode":
            with _lock:
                mode = _read_mode()
            self._send_json(200, {"mode": mode})
            return
        if self.path.startswith("/wallpaper/"):
            self._serve_blob(self.path[len("/wallpaper/") :], send_body=True)
            return
        self._send_json(404, {"error": "not found"})

    def do_HEAD(self):
        if self.path.startswith("/wallpaper/"):
            self._serve_blob(self.path[len("/wallpaper/") :], send_body=False)
            return
        self.send_response(404)
        self.end_headers()

    def _digest_path(self, digest):
        digest = digest.lower()
        if not digest or len(digest) != 64 or not all(c in "0123456789abcdef" for c in digest):
            return None
        return os.path.join(BLOB_DIR, digest)

    def _serve_blob(self, digest, send_body):
        path = self._digest_path(digest)
        if path is None:
            self.send_response(400)
            self.end_headers()
            return
        if not os.path.isfile(path):
            self.send_response(404)
            self.end_headers()
            return
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(size))
        self.end_headers()
        if send_body:
            with open(path, "rb") as f:
                self.wfile.write(f.read())

    def do_PUT(self):
        if not self.path.startswith("/wallpaper/"):
            self._send_json(404, {"error": "not found"})
            return
        digest = self.path[len("/wallpaper/") :].lower()
        path = self._digest_path(digest)
        if path is None:
            self._send_json(400, {"error": "bad sha256"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        data = self.rfile.read(length)
        actual = hashlib.sha256(data).hexdigest()
        if actual != digest:
            self._send_json(400, {"error": "sha256 mismatch", "expected": digest, "actual": actual})
            return
        with _lock:
            os.makedirs(BLOB_DIR, exist_ok=True)
            tmp = path + ".tmp"
            with open(tmp, "wb") as f:
                f.write(data)
            os.replace(tmp, path)
        self._send_json(200, {"ok": True})

    def do_POST(self):
        if self.path == "/state":
            self._post_state(_read_state, _write_state)
            return
        if self.path.startswith("/state/"):
            group = self.path[len("/state/") :]
            if group not in GROUPS:
                self._send_json(404, {"error": "unknown group", "groups": list(GROUPS)})
                return
            self._post_state(lambda: _read_group_state(group), lambda s: _write_group_state(group, s))
            return
        if self.path == "/mode":
            self._post_mode()
            return
        self._send_json(404, {"error": "not found"})

    def _read_body_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(length))

    # Shared by /state and /state/<group>: both store a last-write-wins
    # document with the same schema, just under a different read/write pair.
    def _post_state(self, read_current, write_new):
        try:
            incoming = self._read_body_json()
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid json"})
            return
        if not isinstance(incoming, dict) or not REQUIRED_FIELDS.issubset(incoming.keys()):
            self._send_json(400, {"error": "missing fields", "required": sorted(REQUIRED_FIELDS)})
            return
        with _lock:
            current = read_current()
            if current is None or incoming["updated_at"] > current["updated_at"]:
                write_new(incoming)
                result, accepted = incoming, True
            else:
                result, accepted = current, False
        self._send_json(200, {"accepted": accepted, "state": result})

    def _post_mode(self):
        try:
            incoming = self._read_body_json()
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid json"})
            return
        mode = incoming.get("mode") if isinstance(incoming, dict) else None
        if mode not in MODES:
            self._send_json(400, {"error": "invalid mode", "allowed": list(MODES)})
            return
        with _lock:
            _write_mode(mode)
        self._send_json(200, {"mode": mode})


class _Server(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    os.makedirs(STATE_DIR, exist_ok=True)
    os.makedirs(BLOB_DIR, exist_ok=True)

    servers = [_Server((host, PORT), Handler) for host in HOSTS]
    threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in servers]
    for host, t in zip(HOSTS, threads):
        t.start()
        print(f"theme-sync-hub listening on {host}:{PORT}", file=sys.stderr)
    print(f"state dir: {STATE_DIR}", file=sys.stderr)

    try:
        for t in threads:
            t.join()
    except KeyboardInterrupt:
        pass
    finally:
        for s in servers:
            s.shutdown()


if __name__ == "__main__":
    main()
