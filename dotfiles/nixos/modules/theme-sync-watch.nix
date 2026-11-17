# Theme/wallpaper sync watcher -- workstation-only (desktop + laptop). Polls
# the theme-sync-hub (server, see theme-sync-hub.nix) every 15s and
# reconciles this machine's local theme/wallpaper state against it. Design,
# protocol and the `boner sync` toggle: ../../scripts/theme-sync/README.md.
{ pkgs, ... }:
let
  watch = pkgs.runCommand "theme-sync-watch" { } ''
    install -Dm755 ${../../scripts/theme-sync-watch} $out/bin/theme-sync-watch
  '';
in
{
  # curl isn't pulled onto PATH by anything every host already imports
  # (unlike jq, see shell.nix) -- declare it explicitly instead of relying
  # on it arriving transitively via some other package's closure.
  environment.systemPackages = [ watch pkgs.curl ];

  # This repo's Hyprland setup (hyprland.nix) is a plain greetd launch with
  # no systemd session integration, so there's no graphical-session.target
  # to bind to. default.target is what starts with this user's login
  # instead -- the same choice already made for claude-remote-control on
  # the server (users/admin.nix). Also starting during a bare SSH login is
  # harmless: the watcher's own no-session check
  # (theme_sync_hypr_session_active in lib/theme-sync-common.sh) already
  # makes it a no-op for the wallpaper half when there's no Hyprland
  # socket, and sync is opt-in per machine and OFF by default regardless
  # (boner sync on/off/status).
  systemd.user.services.theme-sync-watch = {
    description = "Theme/wallpaper sync watcher";
    after = [ "network-online.target" ];
    wantedBy = [ "default.target" ];
    serviceConfig = {
      ExecStart = "${watch}/bin/theme-sync-watch";
      Restart = "always";
      RestartSec = 5;
    };
  };
}
