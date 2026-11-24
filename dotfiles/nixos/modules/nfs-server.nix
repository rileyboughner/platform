{ config, pkgs, hosts, ... }:

{
  imports = [];

  # Enable NFS server
  services.nfs.server.enable = true;

  # Export /mnt/tank/files to the LAN and the tailnet
  services.nfs.server.exports = ''
    /mnt/tank/files  ${hosts.lan}(rw,sync,no_subtree_check,insecure,no_root_squash)
    /mnt/tank/files  ${hosts.tailnet}(rw,sync,no_subtree_check,insecure,no_root_squash)
    /mnt/tank/media/torrents/manual  ${hosts.lan}(rw,sync,no_subtree_check,insecure,no_root_squash)
    /mnt/tank/media/torrents/manual  ${hosts.tailnet}(rw,sync,no_subtree_check,insecure,no_root_squash)
  '';

  # Firewall: allow NFS traffic (rpcbind + nfs)
  networking.firewall.allowedTCPPorts = [ 111 2049 ];
  networking.firewall.allowedUDPPorts = [ 111 2049 ];

  # Ensure rpcbind is running
  services.rpcbind.enable = true;

  # Optional: force systemd to start the NFS server daemons correctly
  systemd.services.nfs-server.enable = true;
}
