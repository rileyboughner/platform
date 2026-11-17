{ config, pkgs, lib, hosts, ... }:

{
  services.openssh.enable = true;
  services.openssh.settings.PasswordAuthentication = true;
  programs.ssh = {
    extraConfig = ''

      Host server
        HostName ${hosts.server.tailnetName}
        User ${hosts.server.user}
        IdentityFile /run/agenix/server-ssh-key

      Host desktop
        HostName ${hosts.desktop.tailnetName}
        User ${hosts.desktop.user}
        IdentityFile /run/agenix/desktop-ssh-key

      # Reached over Tailscale MagicDNS, not the LAN -- this box isn't
      # part of hosts.nix's LAN address scheme.
      Host devbits
        HostName devbits
        User boner
        IdentityFile ~/.ssh/devbits

    '';
  };

  networking.firewall.allowedTCPPorts = [ 22 ];
}
