{ config, pkgs, lib, ... }:

{
  environment.systemPackages = with pkgs; [
    discord-ptb
    python3  # games-service (TV launcher game list/launch)
    heroic
    gamescope
    lutris
  ];

  # Needed so `gamescope -- steam` can manage its own session/VT.
  programs.gamescope.enable = true;

  programs.steam = {
    enable = true;
    remotePlay.openFirewall = true; # Open ports in the firewall for Steam Remote Play
    dedicatedServer.openFirewall = true; # Open ports in the firewall for Source Dedicated Server
    localNetworkGameTransfers.openFirewall = true; # Open ports in the firewall for Steam Local Network Game Transfers
    extraPackages = [ pkgs.gamescope ];
  };

  # Sunshine: game streaming server for Moonlight (TV launcher game streaming).
  # Web UI on https://<host>:47990 (pair Moonlight with the PIN shown there).
  services.sunshine = {
    enable = true;
    openFirewall = true;
    capSysAdmin = true;
    autoStart = true;
  };
}
