{ username, config, ... }:
{
  imports = [ ./git-credentials.nix ];

  age.secrets.todoist-token = {
    file = ../secrets/todoist-token.age;
    owner = username;
  };

  age.secrets.qbittorrent = {
    file = ../secrets/qbittorrent.age;
    owner = username;
  };

  age.secrets.gcalcli-client-secret = {
    file = ../secrets/gcalcli-client-secret.age;
    owner = username;
  };

  # Nextcloud CalDAV credentials for the calendar-events script (two lines:
  # username, then an app password). Create the secret with
  # `boner secrets update nextcloud-caldav` before rebuilding.
  age.secrets.nextcloud-caldav = {
    file = ../secrets/nextcloud-caldav.age;
    owner = username;
  };

  # Each host decrypts its own dedicated server-access key, but consumers
  # (nixos/modules/ssh.nix) always read it from this same stable path.
  age.secrets.server-ssh-key = {
    file = ../secrets/server-ssh-key-${config.networking.hostName}.age;
    owner = username;
  };

  # Key for logging into `desktop` from other workstations (nixos/modules/ssh.nix).
  age.secrets.desktop-ssh-key = {
    file = ../secrets/desktop-ssh-key.age;
    owner = username;
  };
}
