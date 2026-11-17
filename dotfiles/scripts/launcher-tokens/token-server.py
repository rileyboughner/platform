#!/usr/bin/env python3
# Launcher token server: stdlib-only HTTP server that hands the launcher's
# own tokens (Home Assistant, Todoist, Jellyfin) to Riley's devices so he
# never has to paste them into Settings by hand on each one. The launcher
# fetches them with a "Sync tokens from server" tap in Settings (phone /
# tablet) or automatically on launch (TV, which has no Settings screen).
#
# SECURITY POSTURE — read before touching the bind addresses: this serves
# live long-lived tokens over plain HTTP to anyone who can reach it. It
# binds ONLY to the server's private addresses (LAN + tailnet, never
# 0.0.0.0) and has NO cloudflared route — Riley chose LAN + tailnet
# explicitly (2026-11-17) so the TV can reach it, accepting that anything
# on his home WiFi can fetch the tokens. Do not widen this without his
# explicit say-so.
#
# Token files live in TOKEN_SECRETS_DIR (default /home/admin/.launcher-secrets,
# mode 700, owned by admin — the service runs as User=admin for exactly
# this reason) and are read fresh on EVERY request, so dropping a new file
# in takes effect with no restart. Files (each holding one bare token,
# trailing whitespace stripped):
#   ha-token         -> homeAssistantToken
#   todoist-token    -> todoistToken
#   jellyfin-api-key -> jellyfinApiKey
# Missing or empty files are simply omitted from the response — a device
# keeps whatever it already has for that token. NOTHING secret is ever
# logged: request logs carry method + path + status only, never a body.
import json
import os
import socketserver
import sys
import threading
from http.server import BaseHTTPRequestHandler

PORT = int(os.environ.get("TOKEN_SERVER_PORT", "8766"))
# Comma-separated list of addresses to bind to. One listening socket per
# address -- deliberately not "0.0.0.0" (see the header comment).
HOSTS = [h.strip() for h in os.environ.get("TOKEN_SERVER_HOSTS", "127.0.0.1").split(",") if h.strip()]
SECRETS_DIR = os.environ.get("TOKEN_SECRETS_DIR", "/home/admin/.launcher-secrets")

# File name in SECRETS_DIR -> key in the GET /tokens JSON response.
TOKEN_FILES = {
    "ha-token": "homeAssistantToken",
    "todoist-token": "todoistToken",
    "jellyfin-api-key": "jellyfinApiKey",
}


def _read_tokens():
    tokens = {}
    for filename, key in TOKEN_FILES.items():
        try:
            with open(os.path.join(SECRETS_DIR, filename), "r") as f:
                value = f.read().strip()
        except (FileNotFoundError, OSError):
            continue
        if value:
            tokens[key] = value
    return tokens


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        # Default handler logging goes to stderr; keep it, but only ever
        # method + path + status — this handler never sees a body worth
        # logging, and token values must never reach a log.
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send_json(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/tokens":
            tokens = _read_tokens()
            # Log which keys were served, never the values.
            print("served /tokens keys: %s" % ",".join(sorted(tokens)), file=sys.stderr)
            self._send_json(200, tokens)
        elif self.path == "/health":
            self._send_json(200, {"ok": True})
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        self.send_response(404)
        self.end_headers()


class _Server(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    servers = [_Server((host, PORT), Handler) for host in HOSTS]
    threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in servers]
    for host, t in zip(HOSTS, threads):
        t.start()
        print(f"launcher-token-server listening on {host}:{PORT}", file=sys.stderr)
    print(f"secrets dir: {SECRETS_DIR}", file=sys.stderr)

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
