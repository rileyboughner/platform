# Launcher token server -- server-only. Serves the launcher's own tokens
# (Home Assistant, Todoist, Jellyfin) to Riley's devices over plain HTTP
# on the private networks only, so Settings never needs a hand-pasted
# token per device. Script: ../../scripts/launcher-tokens/token-server.py.
{ pkgs, hosts, ... }:
let
  port = 8766;
  server = pkgs.writers.writePython3Bin "launcher-token-server"
    { flakeIgnore = [ "E501" "E203" "E265" ]; } # same as theme-sync-hub.nix: the script isn't wrapped to flake8's rules
    (builtins.readFile ../../scripts/launcher-tokens/token-server.py);
in

{
  systemd.services.launcher-token-server = {
    description = "Launcher token server";
    after = [ "network.target" ];
    wantedBy = [ "multi-user.target" ];
    environment = {
      TOKEN_SERVER_PORT = toString port;
      # Bind only to this server's private addresses -- never "0.0.0.0".
      # Both the LAN address and the tailnet (Tailscale) address: the TV
      # is LAN-only (no Tailscale), the phone/tablet roam. Riley chose
      # LAN + tailnet explicitly (2026-11-17), accepting that anything
      # on the home WiFi can fetch these tokens. No cloudflared route.
      TOKEN_SERVER_HOSTS = "${hosts.server.ip},${hosts.server.tailnetIp}";
      TOKEN_SECRETS_DIR = "/home/admin/.launcher-secrets";
    };
    serviceConfig = {
      ExecStart = "${server}/bin/launcher-token-server";
      # Runs as admin (NOT DynamicUser): the token files live in
      # /home/admin/.launcher-secrets (mode 700, admin-owned) and the
      # service must read them. It touches nothing else.
      User = "admin";
      Restart = "on-failure";
      RestartSec = 5;
    };
  };

  # Private networks only, no auth -- same posture as theme-sync-hub.nix:
  # the bind addresses above already limit reachability, and the router
  # never port-forwards this to the WAN. No cloudflared route.
  networking.firewall.allowedTCPPorts = [ port ];
}
