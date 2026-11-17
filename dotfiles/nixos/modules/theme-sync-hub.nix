# Theme/wallpaper sync hub -- server-only. Holds the one shared
# theme/wallpaper state document plus the wallpaper blobs the
# theme-sync-watch user service (desktop + laptop, see
# theme-sync-watch.nix) pushes to and pulls from. Protocol, storage
# layout and design rationale: ../../scripts/theme-sync/README.md.
{ pkgs, hosts, ... }:
let
  port = 8765;
  hub = pkgs.writers.writePython3Bin "theme-sync-hub"
    { flakeIgnore = [ "E501" "E203" "E265" ]; } # hub.py isn't wrapped to flake8's 79-col/black-slice-spacing rules; E265 fires on its own `#!/usr/bin/env python3` line, which writePython3Bin demotes to a plain comment under its own injected interpreter line
    (builtins.readFile ../../scripts/theme-sync/hub.py);
in
{
  systemd.services.theme-sync-hub = {
    description = "Theme/wallpaper sync hub";
    after = [ "network.target" ];
    wantedBy = [ "multi-user.target" ];
    environment = {
      THEME_SYNC_PORT = toString port;
      # Bind only to this server's private addresses -- never "0.0.0.0".
      # Both the LAN address and the tailnet (Tailscale) address, so
      # desktop/laptop can reach the hub whether they're on the LAN or
      # only on the tailnet. See hosts.nix for both addresses.
      THEME_SYNC_HOSTS = "${hosts.server.ip},${hosts.server.tailnetIp}";
      THEME_SYNC_STATE_DIR = "/var/lib/theme-sync";
    };
    serviceConfig = {
      ExecStart = "${hub}/bin/theme-sync-hub";
      # Dedicated unprivileged dynamic user + a managed state dir
      # (-> /var/lib/theme-sync) instead of running as a fixed system
      # user: this service only ever touches its own state directory.
      DynamicUser = true;
      StateDirectory = "theme-sync";
      Restart = "on-failure";
      RestartSec = 5;
    };
  };

  # Private network only, no auth -- same posture as this host's other
  # LAN-only services (nfs-server.nix, samba-server.nix): the router
  # never port-forwards this to the WAN, and the tailnet is itself a
  # private overlay, so opening it host-wide here is consistent with the
  # rest of hosts/server/configuration.nix. This repo has no precedent
  # for per-interface firewall rules (nfs-server.nix, ssh.nix and
  # samba-server.nix are all host-wide too), and the hub's own
  # THEME_SYNC_HOSTS binding above already limits which addresses it's
  # actually reachable on, so a host-wide allow here doesn't widen that.
  # No cloudflared route for this service.
  networking.firewall.allowedTCPPorts = [ port ];
}
