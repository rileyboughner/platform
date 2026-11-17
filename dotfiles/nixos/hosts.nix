# Single source of truth for this household's machines, on the LAN and the
# tailnet, used by any module that needs to reach another host by address
# (ssh.nix, nfs-client.nix, nfs-server.nix, server-remote-access.nix)
# instead of hand-typing IPs/users.
{
  lan = "192.168.1.0/24";
  tailnet = "100.64.0.0/10";

  desktop = {
    ip = "192.168.1.4";
    tailnetName = "desktop.taila93a39.ts.net";
    user = "rileyboughner";
  };

  laptop = {
    ip = "192.168.1.3";
    tailnetName = "laptop.taila93a39.ts.net";
    user = "rileyboughner";
  };

  server = {
    ip = "192.168.1.2";
    tailnetName = "server.taila93a39.ts.net";
    # Tailscale-assigned address for this host (`tailscale ip -4`), stable
    # across reboots for a given node key. Needed by theme-sync-hub.nix,
    # which has to bind an explicit address rather than resolve a hostname.
    tailnetIp = "100.124.77.118";
    user = "admin";
  };

  router = {
    ip = "192.168.1.1";
    user = "root";
  };
}
