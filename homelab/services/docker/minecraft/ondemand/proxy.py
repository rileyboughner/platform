#!/usr/bin/env python3
"""mc-ondemand: Minecraft Java on-demand proxy + idle-stop watcher.

Listens on LISTEN_PORT (default 25565). When a player connects:
- backend READY  -> proxy the connection straight through to the server.
- backend DOWN   -> start the container, tell the player the server is
                    starting and to reconnect in a moment.
- backend STARTING -> tell the player to try again in a few seconds.
While the backend is down/starting, server-list status pings are answered
locally with an "asleep / starting" MOTD so the server still shows in the
list. A watcher thread stops the container after IDLE_MINUTES with 0
players online.

Standard library only. Docker control via the Engine API over
/var/run/docker.sock.
"""
import http.client
import json
import os
import signal
import socket
import struct
import sys
import threading
import time
from datetime import datetime

BACKEND_HOST = os.environ.get("BACKEND_HOST", "minecraft")
BACKEND_PORT = int(os.environ.get("BACKEND_PORT", "25565"))
CONTAINER_NAME = os.environ.get("CONTAINER_NAME", "minecraft")
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "25565"))
IDLE_MINUTES = float(os.environ.get("IDLE_MINUTES", "10"))
POLL_SECONDS = float(os.environ.get("POLL_SECONDS", "30"))
STOP_GRACE_SECONDS = int(os.environ.get("STOP_GRACE_SECONDS", "60"))
MOTD_ASLEEP = os.environ.get("MOTD_ASLEEP", "Server is asleep - connect to wake it up!")
MOTD_STARTING = os.environ.get("MOTD_STARTING", "Server is starting up - try again in a moment!")
VERSION_NAME = os.environ.get("VERSION_NAME", "Paper 26.3")
MAX_PLAYERS = int(os.environ.get("MAX_PLAYERS", "100"))
DOCKER_SOCK = os.environ.get("DOCKER_SOCK", "/var/run/docker.sock")
STATUS_CACHE_SECONDS = float(os.environ.get("STATUS_CACHE_SECONDS", "2"))

SHUTDOWN = threading.Event()


def log(msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


# --------------------------------------------------------------------------
# Minecraft protocol helpers (modern protocol, 1.7+)
# --------------------------------------------------------------------------

def encode_varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            out += bytes([b])
            return out


def read_varint_from(data, idx):
    n = 0
    shift = 0
    while True:
        if idx >= len(data):
            raise EOFError("varint past end of buffer")
        b = data[idx]
        idx += 1
        n |= (b & 0x7F) << shift
        if not (b & 0x80):
            return n, idx
        shift += 7
        if shift > 35:
            raise ValueError("varint too long")


def encode_string(s):
    b = s.encode("utf-8")
    return encode_varint(len(b)) + b


def read_string_from(data, idx):
    ln, idx = read_varint_from(data, idx)
    s = data[idx:idx + ln].decode("utf-8")
    return s, idx + ln


def make_packet(pid, payload=b""):
    body = encode_varint(pid) + payload
    return encode_varint(len(body)) + body


def read_exact_sock(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    return data


def read_packet_raw(sock):
    """Read one length-prefixed packet; return its exact raw bytes."""
    length_bytes = b""
    length = 0
    shift = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise EOFError("connection closed while reading length")
        length_bytes += b
        length |= (b[0] & 0x7F) << shift
        if not (b[0] & 0x80):
            break
        shift += 7
        if shift > 35:
            raise ValueError("packet length varint too long")
    payload = read_exact_sock(sock, length)
    return length_bytes + payload


def parse_handshake(raw):
    """Parse raw packet bytes as a handshake. Returns
    (protocol, address, port, next_state) or None if not a handshake."""
    try:
        _, idx = read_varint_from(raw, 0)  # length prefix
        pid, idx = read_varint_from(raw, idx)  # packet id
        if pid != 0:
            return None
        protocol, idx = read_varint_from(raw, idx)
        address, idx = read_string_from(raw, idx)
        port = struct.unpack(">H", raw[idx:idx + 2])[0]
        idx += 2
        next_state, idx = read_varint_from(raw, idx)
        return protocol, address, port, next_state
    except (EOFError, ValueError, IndexError, UnicodeDecodeError):
        return None


# --------------------------------------------------------------------------
# Docker Engine API over the Unix socket
# --------------------------------------------------------------------------

class UnixHTTPConnection(http.client.HTTPConnection):
    def __init__(self, path, timeout=10):
        super().__init__("localhost", timeout=timeout)
        self._unix_path = path

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self._unix_path)


def docker_request(method, path):
    conn = UnixHTTPConnection(DOCKER_SOCK, timeout=10)
    try:
        conn.request(method, path)
        resp = conn.getresponse()
        body = resp.read()
        return resp.status, body
    finally:
        conn.close()


def container_running():
    try:
        status, body = docker_request("GET", f"/containers/{CONTAINER_NAME}/json")
    except OSError as e:
        log(f"docker api error: {e}")
        return False
    if status == 404:
        return False
    if status != 200:
        log(f"docker api: GET container returned {status}")
        return False
    try:
        return bool(json.loads(body).get("State", {}).get("Running"))
    except Exception:
        return False


def docker_start():
    try:
        status, _ = docker_request("POST", f"/containers/{CONTAINER_NAME}/start")
        return status in (204, 304)
    except OSError as e:
        log(f"docker start failed: {e}")
        return False


def docker_stop():
    try:
        status, _ = docker_request(
            "POST", f"/containers/{CONTAINER_NAME}/stop?t={STOP_GRACE_SECONDS}")
        return status in (204, 304)
    except OSError as e:
        log(f"docker stop failed: {e}")
        return False


# --------------------------------------------------------------------------
# Backend state
# --------------------------------------------------------------------------

def status_ping(timeout=2.0):
    """Server-list status ping straight to the backend. Returns the parsed
    status JSON dict, or None if the backend does not answer."""
    try:
        s = socket.create_connection((BACKEND_HOST, BACKEND_PORT), timeout=timeout)
    except (socket.gaierror, OSError):
        return None
    try:
        f = s.makefile("rb")
        hs = (encode_varint(765) + encode_string(BACKEND_HOST)
              + struct.pack(">H", BACKEND_PORT) + encode_varint(1))
        s.sendall(make_packet(0, hs))
        s.sendall(make_packet(0))
        # read length varint byte by byte from the file object
        length = 0
        shift = 0
        while True:
            b = f.read(1)
            if not b:
                raise EOFError("closed during status reply")
            length |= (b[0] & 0x7F) << shift
            if not (b[0] & 0x80):
                break
            shift += 7
        data = b""
        while len(data) < length:
            chunk = f.read(length - len(data))
            if not chunk:
                raise EOFError("closed during status reply")
            data += chunk
        pid, idx = read_varint_from(data, 0)
        if pid != 0:
            return None
        ln, idx = read_varint_from(data, idx)
        js = json.loads(data[idx:idx + ln].decode("utf-8"))
        s.sendall(make_packet(1, struct.pack(">q", 12345)))
        length = 0
        shift = 0
        while True:
            b = f.read(1)
            if not b:
                raise EOFError("closed during pong")
            length |= (b[0] & 0x7F) << shift
            if not (b[0] & 0x80):
                break
            shift += 7
        data = b""
        while len(data) < length:
            chunk = f.read(length - len(data))
            if not chunk:
                raise EOFError("closed during pong")
            data += chunk
        pid, _ = read_varint_from(data, 0)
        if pid != 1:
            return None
        return js
    except (OSError, EOFError, ValueError, json.JSONDecodeError):
        return None
    finally:
        try:
            s.close()
        except OSError:
            pass


def check_backend_state():
    """Fresh check. Returns (state, players) where state is one of
    DOWN, STARTING, READY."""
    if not container_running():
        return "DOWN", 0
    js = status_ping()
    if js is None:
        return "STARTING", 0
    players = js.get("players", {}).get("online", 0)
    return "READY", players


_state_cache = {"state": None, "players": 0, "ts": 0.0}
_state_lock = threading.Lock()


def get_backend_state():
    """Cached state for connection handling (2s TTL)."""
    with _state_lock:
        now = time.time()
        if (_state_cache["state"] is not None
                and now - _state_cache["ts"] < STATUS_CACHE_SECONDS):
            return _state_cache["state"], _state_cache["players"]
        state, players = check_backend_state()
        _state_cache.update({"state": state, "players": players, "ts": now})
        return state, players


# --------------------------------------------------------------------------
# Connection handling
# --------------------------------------------------------------------------

def connect_backend():
    try:
        s = socket.create_connection((BACKEND_HOST, BACKEND_PORT), timeout=5)
        s.settimeout(None)
        return s
    except (socket.gaierror, OSError):
        return None


def splice(a, b):
    def pump(src, dst):
        try:
            while True:
                data = src.recv(65536)
                if not data:
                    break
                dst.sendall(data)
        except OSError:
            pass
        finally:
            for s in (a, b):
                try:
                    s.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            for s in (a, b):
                try:
                    s.close()
                except OSError:
                    pass

    t1 = threading.Thread(target=pump, args=(a, b), daemon=True)
    t2 = threading.Thread(target=pump, args=(b, a), daemon=True)
    t1.start()
    t2.start()
    t1.join()
    t2.join()


def close_quietly(s):
    try:
        s.close()
    except OSError:
        pass


def answer_status(client, protocol, motd, color):
    """Answer a server-list status ping locally. The client has already sent
    the handshake; it sends a status request packet next, then a ping.
    Read the request first, then the response, then pong the ping."""
    try:
        payload = {
            "version": {"name": VERSION_NAME, "protocol": protocol},
            "players": {"max": MAX_PLAYERS, "online": 0},
            "description": {"text": motd, "color": color},
            "enforcesSecureChat": False,
        }
        response = make_packet(0, encode_string(json.dumps(payload)))
        client.settimeout(2)
        pong_payload = None
        try:
            raw = read_packet_raw(client)
            _, idx = read_varint_from(raw, 0)
            pid, idx = read_varint_from(raw, idx)
            if pid == 1:
                # Client skipped the status request and sent the ping first.
                pong_payload = raw[idx:]
            elif pid != 0:
                return
        except (OSError, EOFError, ValueError):
            pass
        client.sendall(response)
        if pong_payload is not None:
            client.sendall(make_packet(1, pong_payload))
            return
        # Read the ping packet and reply with a pong.
        client.settimeout(2)
        try:
            raw = read_packet_raw(client)
        except (OSError, EOFError, ValueError):
            return
        _, idx = read_varint_from(raw, 0)
        pid, idx = read_varint_from(raw, idx)
        if pid == 1:
            client.sendall(make_packet(1, raw[idx:]))
    except OSError:
        pass


def send_login_disconnect(client, component):
    try:
        client.sendall(make_packet(
            0, encode_string(json.dumps(component, separators=(",", ":")))))
    except OSError:
        pass


def handle_client(client, addr):
    client.settimeout(5)
    try:
        raw = read_packet_raw(client)
    except (OSError, EOFError, ValueError):
        close_quietly(client)
        return
    handshake = parse_handshake(raw)
    if handshake is None:
        # Not a handshake: proxy the raw bytes if the backend is up.
        state, _ = get_backend_state()
        if state == "READY":
            backend = connect_backend()
            if backend is not None:
                try:
                    backend.sendall(raw)
                except OSError:
                    close_quietly(client)
                    close_quietly(backend)
                    return
                splice(client, backend)
                return
        close_quietly(client)
        return

    protocol, _address, _port, next_state = handshake
    state, _players = get_backend_state()

    if next_state == 1:  # status
        if state == "READY":
            backend = connect_backend()
            if backend is not None:
                try:
                    backend.sendall(raw)
                except OSError:
                    close_quietly(client)
                    close_quietly(backend)
                    return
                splice(client, backend)
                return
            # backend connect failed despite READY: fall through, answer locally
        if state == "DOWN":
            answer_status(client, protocol, MOTD_ASLEEP, "gray")
        else:
            answer_status(client, protocol, MOTD_STARTING, "yellow")
    elif next_state == 2:  # login
        if state == "READY":
            backend = connect_backend()
            if backend is not None:
                try:
                    backend.sendall(raw)
                except OSError:
                    close_quietly(client)
                    close_quietly(backend)
                    return
                splice(client, backend)
                return
        if state == "DOWN":
            ok = docker_start()
            log(f"wake: {addr[0]} connected while DOWN, docker start -> {ok}")
            send_login_disconnect(client, {
                "text": "The server is starting up! Give it about 30 seconds, then reconnect.",
                "color": "yellow",
            })
        else:
            send_login_disconnect(client, {
                "text": "The server is still starting up - try again in a few seconds.",
                "color": "yellow",
            })
    else:
        log(f"unknown next state {next_state} from {addr[0]}")
    close_quietly(client)


# --------------------------------------------------------------------------
# Idle-stop watcher
# --------------------------------------------------------------------------

def watcher():
    idle = 0.0
    last_state = None
    while not SHUTDOWN.is_set():
        SHUTDOWN.wait(POLL_SECONDS)
        if SHUTDOWN.is_set():
            break
        try:
            state, players = check_backend_state()
        except Exception as e:
            log(f"watcher check failed: {e}")
            continue
        if state != last_state:
            log(f"backend state: {last_state} -> {state} (players={players})")
            last_state = state
        if state == "READY" and players == 0:
            idle += POLL_SECONDS
            if idle >= IDLE_MINUTES * 60:
                log(f"idle timeout ({IDLE_MINUTES:g} min, 0 players): stopping {CONTAINER_NAME}")
                ok = docker_stop()
                log(f"docker stop -> {ok}")
                idle = 0.0
        else:
            idle = 0.0


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main():
    def _sig(signum, _frame):
        SHUTDOWN.set()

    signal.signal(signal.SIGTERM, _sig)
    signal.signal(signal.SIGINT, _sig)
    log(f"mc-ondemand starting: listen :{LISTEN_PORT}, backend "
        f"{BACKEND_HOST}:{BACKEND_PORT}, container {CONTAINER_NAME}, "
        f"idle {IDLE_MINUTES:g} min, poll {POLL_SECONDS:g}s")
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", LISTEN_PORT))
    srv.listen(128)
    srv.settimeout(1.0)
    threading.Thread(target=watcher, daemon=True).start()
    while not SHUTDOWN.is_set():
        try:
            client, addr = srv.accept()
        except socket.timeout:
            continue
        except OSError:
            break
        threading.Thread(target=handle_client, args=(client, addr), daemon=True).start()
    try:
        srv.close()
    except OSError:
        pass
    log("mc-ondemand stopped")


if __name__ == "__main__":
    main()
