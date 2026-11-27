# Calendar cache refresh -- workstation-only (desktop + laptop).
# Keeps the Nextcloud calendar cache warm so the Quickshell calendar
# panel opens instantly instead of doing live CalDAV requests on every
# open. The calendar-events script caches results for 10 minutes; this
# timer refreshes the cache every 10 minutes in the background so the
# panel almost always reads from a warm cache.
{ pkgs, ... }:
{
  systemd.user.services.calendar-events-refresh = {
    description = "Refresh Nextcloud calendar cache";
    after = [ "network-online.target" ];
    serviceConfig = {
      Type = "oneshot";
      # systemd user services start with a minimal PATH that lacks even
      # bash/python3, so run through a shell that sources /etc/profile
      # first to get the login PATH (where calendar-events lives).
      ExecStart = "${pkgs.bash}/bin/bash -c 'source /etc/profile; exec calendar-events refresh'";
    };
  };

  systemd.user.timers.calendar-events-refresh = {
    description = "Refresh Nextcloud calendar cache every 10 minutes";
    wantedBy = [ "timers.target" ];
    timerConfig = {
      OnBootSec = "1min";
      OnUnitActiveSec = "10min";
      Persistent = true;
    };
  };
}
