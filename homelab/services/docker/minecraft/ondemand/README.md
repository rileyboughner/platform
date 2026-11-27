# mc-ondemand — Minecraft on-demand proxy + idle-stop watcher

A tiny always-on proxy that owns host port 25565. Java only; Bedrock is
not part of the on-demand path.

- Player connects while the `minecraft` container is stopped: the proxy
  starts it and tells the player the server is starting up, reconnect in
  about 30 seconds.
- Player connects while it is starting: the proxy says try again in a few
  seconds.
- Player connects while it is up: the connection is proxied straight
  through to the server.
- Server-list pings while the server is down/starting are answered by the
  proxy itself with an "asleep / starting" MOTD, so the server still shows
  in the list.
- A watcher stops the container after IDLE_MINUTES (default 10) with
  0 players online.

## Env vars

| var | default | what it does |
| --- | ------- | ------------ |
| BACKEND_HOST | minecraft | backend container name (Docker DNS) |
| BACKEND_PORT | 25565 | backend container port |
| CONTAINER_NAME | minecraft | container to start/stop |
| LISTEN_PORT | 25565 | port the proxy listens on |
| IDLE_MINUTES | 10 | empty-server minutes before auto-stop |
| POLL_SECONDS | 30 | watcher check interval |
| STOP_GRACE_SECONDS | 60 | seconds the server gets to save+stop |
| MOTD_ASLEEP | Server is asleep - connect to wake it up! | list MOTD while down |
| MOTD_STARTING | Server is starting up - try again in a moment! | list MOTD while booting |
| VERSION_NAME | Paper 26.3 | version name shown while down |
| MAX_PLAYERS | 100 | max players shown while down |

## Operations

- Status: `docker logs mc-ondemand` (state transitions, wakes, idle stops)
- Restart: `cd /home/admin/.platform/homelab/services/docker/minecraft && docker compose restart mc-ondemand`
- Test the proxy path: `docker run --rm --network host -v /home/admin/mc_ping.py:/mc_ping.py:ro python:3.13-slim python /mc_ping.py 127.0.0.1 25565`
- The proxy reaches the backend by container name on the compose network
  and controls it through /var/run/docker.sock (mounted read-write).
