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
      HostName ${hosts.desktop.tailnetName}
      User ${hosts.desktop.user}
      IdentityFile /run/agenix/server-to-desktop-key

    # Reached over Tailscale MagicDNS, so `ssh laptop` works from
    # anywhere the laptop is on the tailnet, home or away.
    Host laptop
      HostName ${hosts.laptop.tailnetName}
      User ${hosts.laptop.user}
      IdentityFile /run/agenix/server-to-laptop-key

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
