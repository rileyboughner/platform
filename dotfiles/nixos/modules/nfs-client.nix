{ config, pkgs, hosts, username, ... }:
{

  fileSystems."/home/${username}/.vault" = {
    device = "${hosts.server.tailnetName}:/mnt/tank/files/obsidian";
    fsType = "nfs";
    options = [ "rw" "_netdev" "x-systemd.automount" "noauto" "nofail" "vers=4" ];
  };

  fileSystems."/home/${username}/Documents" = {
    device = "${hosts.server.tailnetName}:/mnt/tank/files/Documents";
    fsType = "nfs";
    options = [ "rw" "_netdev" "x-systemd.automount" "noauto" "nofail" "vers=4" ];
  };

  environment.systemPackages = with pkgs; [
    nfs-utils
  ];

}
