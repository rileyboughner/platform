# Dedicated outbound SSH access from `server` to the workstations, used by
# Claude Code Remote Control running on `server` to reach into `desktop` and
# `laptop`. See nixos/secrets/secrets.nix for the key rationale.
{ username, pkgs, hosts, ... }:
{
  age.secrets.server-to-desktop-key = {
    file = ../secrets/server-to-desktop-key.age;
    owner = username;
  };
  age.secrets.server-to-laptop-key = {
    file = ../secrets/server-to-laptop-key.age;
    owner = username;
  };
  age.secrets.server-to-router-key = {
    file = ../secrets/server-to-router-key.age;
    owner = username;
  };

  programs.ssh.extraConfig = ''
    Host desktop
      HostName ${hosts.desktop.ip}
      User ${hosts.desktop.user}
      IdentityFile /run/agenix/server-to-desktop-key

    # The laptop only answers on its home-LAN address when it's actually
    # home. Off home Wi-Fi, NetworkManager auto-brings up the "clownweb"
    # WireGuard tunnel, and the laptop is only reachable at its tunnel
    # address instead - see hosts/laptop/configuration.nix's
    # vpn-autoconnect dispatcher. Try the LAN address as the real transport
    # and fall back to the tunnel address only if that connection attempt
    # itself fails, so `ssh laptop` keeps working in both states.
    #
    # Deliberately NOT a "probe, then connect" ProxyCommand (that used to
    # do `nc -z` to test the LAN address before connecting for real): a
    # throwaway connect-then-disconnect against sshd gets penalised by
    # OpenSSH's PerSourcePenalties (added for the "noauth"/"crash" case,
    # i.e. exactly what an unauthenticated probe looks like from sshd's
    # side), which then closes the very next real connection attempt
    # during kex. Letting the first nc *be* the connection avoids ever
    # opening and abandoning an unauthenticated session.
    Host laptop
      HostName ${hosts.laptop.ip}
      User ${hosts.laptop.user}
      IdentityFile /run/agenix/server-to-laptop-key
      ProxyCommand ${pkgs.netcat}/bin/nc ${hosts.laptop.ip} 22 2>/dev/null || exec ${pkgs.netcat}/bin/nc ${hosts.laptop.tunnelIp} 22

    Host router
      HostName ${hosts.router.ip}
      User ${hosts.router.user}
      IdentityFile /run/agenix/server-to-router-key
  '';

  # Let the admin user's systemd --user instance (and the
  # claude-remote-control.service defined on it, see users/admin.nix)
  # start at boot and keep running with no login session. Equivalent to
  # `loginctl enable-linger admin`, done declaratively since that command
  # itself needs root.
  systemd.tmpfiles.rules = [ "f /var/lib/systemd/linger/${username} 0644 root root -" ];
}
