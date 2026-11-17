{ config, pkgs, username, ...}:
let
  # One credential helper for all of github.com. It reads the request path
  # git hands it on stdin and picks the right agenix secret itself.
  # Per-URL config sections cannot do this routing: git tries credential
  # helpers in config-file order, home-manager emits the sections
  # alphabetically, and the broad github.com/rileyboughner section sorts
  # ahead of github.com/rileyboughner/platform -- so pushes to platform
  # were sent the wrong token and GitHub rejected them. Routing here, in
  # code order with the most specific path first, instead. useHttpPath
  # must stay on: without it git sends no path and this cannot route.
  gitCredentialAgenix = pkgs.writeShellScript "git-credential-agenix" ''
    set -euo pipefail
    op="$1"

    if [ "$op" != "get" ]; then
      cat >/dev/null
      exit 0
    fi

    # Credential context from git: key=value lines, blank line to end.
    path=""
    while IFS='=' read -r key value; do
      [ -z "$key" ] && break
      if [ "$key" = "path" ]; then path="$value"; fi
    done

    # Most specific first.
    case "$path" in
      rileyboughner/platform|rileyboughner/platform/*|rileyboughner/platform.git|rileyboughner/platform.git/*)
        secret_file="/run/agenix/git-credentials-platform" ;;
      BonerLinux/*)
        secret_file="/run/agenix/git-credentials-bonerlinux" ;;
      boughnerengineering/*)
        secret_file="/run/agenix/git-credentials-boughnerengineering" ;;
      weave-music/*)
        secret_file="/run/agenix/git-credentials-weave-music" ;;
      rileyboughner/*)
        secret_file="/run/agenix/git-credentials-rileyboughner" ;;
      *)
        exit 0 ;;
    esac

    [ -r "$secret_file" ] || exit 0

    line=$(cat "$secret_file")
    rest=''${line#https://}
    userpass=''${rest%%@*}
    user=''${userpass%%:*}
    pass=''${userpass#*:}

    echo "username=$user"
    echo "password=$pass"
  '';
in
{

  home.stateVersion = "25.05";
  home.username = username;
  home.homeDirectory = "/home/${username}";

  programs.home-manager.enable = true;

  programs.git = {
    enable = true;
    settings.user.name = "Riley Boughner";
    settings.user.email = "riley@clownweb.net";
    settings = {
      # One dispatcher helper for all of github.com (see
      # gitCredentialAgenix above): it routes by request path. Do not
      # split this back out into per-URL sections -- git tries helpers
      # in config-file order and the broad section shadows the specific
      # one. useHttpPath must stay on or git sends no path to route on.
      credential."https://github.com".useHttpPath = true;
      credential."https://github.com".helper = "!${gitCredentialAgenix}";
    };
  };

  # Persistent Claude Code Remote Control, running from the admin home
  # directory so it's reachable from claude.ai/code without a login
  # session kept open. Needs the admin user's systemd instance to linger
  # at boot -- see systemd.tmpfiles.rules in
  # nixos/modules/server-remote-access.nix.
  systemd.user.services.claude-remote-control = {
    Unit = {
      Description = "Claude Code Remote Control (persistent)";
      After = [ "network-online.target" ];
    };
    Service = {
      # `claude remote-control` (not the --remote-control flag on a normal
      # session) is the persistent broker that actually registers as a
      # device in claude.ai/code's device list and can spawn sessions
      # on demand. It also checks isatty(stdout) to decide whether to run
      # interactively; under systemd stdout is the journal, not a tty, so
      # `script` allocates a real pty so it starts normally.
      ExecStart = "${pkgs.util-linux}/bin/script -qefc '${pkgs.claude-code}/bin/claude remote-control --name server' /dev/null";
      WorkingDirectory = config.home.homeDirectory;
      Restart = "always";
      RestartSec = 5;
    };
    Install.WantedBy = [ "default.target" ];
  };
}

