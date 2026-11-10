{ config, lib, pkgs, hosts, ... }:

{
  imports =
    [
      ../../configuration.nix
      ../../modules/wireless-networking.nix
      ../../modules/nfs-client.nix
      ../../modules/audio.nix
      ../../modules/quickemu.nix
    ];

  networking.hostName = "laptop";

  # Trust the public half of server-to-laptop-key.age (see
  # nixos/secrets/secrets.nix), used by Claude Code Remote Control running
  # on `server` to reach this machine.
  users.users.rileyboughner.openssh.authorizedKeys.keys = [
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDHk8gh3PFQisND9c+yno1y837kTbP/CHm9KurwQV7fn server-to-laptop"
  ];

  # -- lid switch --
  # Shut down on lid close (undocked/battery); ignored when docked
  # so an external monitor can still be used in clamshell mode.
  services.logind.settings.Login = {
    HandleLidSwitch = "poweroff";
    HandleLidSwitchExternalPower = "poweroff";
    HandleLidSwitchDocked = "ignore";
  };

  # -- fingerprint --
  # Framework's built-in reader (Goodix Moc, USB ID 27c6:609c) is
  # supported directly by libfprint's gxfp driver, no TOD/proprietary
  # driver needed. After rebuilding, enroll a finger with:
  #   fprintd-enroll
  # and verify with:
  #   fprintd-verify
  services.fprintd.enable = true;
  security.pam.services.sudo.fprintAuth = true;

  # -- VPN auto-connect --
  # Bring the "clownweb" WireGuard tunnel up whenever we're not on home
  # Wi-Fi, and drop it when we're back home. Runs as root via a
  # NetworkManager dispatcher hook, so it needs no sudo rule (unlike the
  # manual toggle in the Quickshell bar).
  #
  # Also reacts to "connectivity-change", not just "up"/"down": captive-
  # portal networks (college Wi-Fi) report the interface "up" the moment
  # you associate, well before you've passed the portal and have real
  # internet - an attempt at that point fails and nothing used to retry
  # once real connectivity actually arrived. "connectivity-change" fires
  # when NetworkManager confirms full internet access, giving us that
  # retry. Logs outcomes via `logger` (journalctl -t vpn-autoconnect) so
  # failures are debuggable instead of silently swallowed.
  #
  # clownweb is a full-tunnel (0.0.0.0/0 AllowedIPs) config, so wg-quick's
  # own policy routing (an ip rule matching all non-tunnel-marked traffic,
  # installed ahead of the main table) wins over the connected-subnet
  # route for the home LAN whenever both the tunnel and the home LAN are
  # up at once (e.g. the VPN manually toggled on from the Quickshell bar
  # while still on home Wi-Fi). That sends replies to anything on
  # 192.168.1.0/24 - including the SSH reply traffic for `ssh laptop` -
  # out through the tunnel instead of back out over the LAN, so they're
  # dropped. Pin an ip rule for the home LAN back to the main table at a
  # priority ahead of wg-quick's rules so LAN-destined traffic always
  # takes the LAN route regardless of tunnel state; it's a no-op (lookup
  # just misses) when the LAN isn't actually reachable. Handled in two
  # places below: inline wherever this script itself runs `wg-quick`, and
  # in a dedicated branch for the "vpn" interface's own up/down event (the
  # Quickshell bar's manual toggle calls `wg-quick` directly, bypassing
  # this script's SSID logic entirely, but NetworkManager still dispatches
  # the resulting interface transition - hence the pre-existing
  # lo|"$vpn_name" exit guard below, now put to use instead of a no-op).
  networking.networkmanager.dispatcherScripts = [
    {
      source = pkgs.writeShellScript "vpn-autoconnect" ''
        interface="$1"
        action="$2"
        home_ssid="The-Clown-House"
        vpn_name="vpn"
        home_lan="${hosts.lan}"
        lan_rule_pref=100
        wg_quick="/run/current-system/sw/bin/wg-quick"
        nmcli="/run/current-system/sw/bin/nmcli"
        ip="/run/current-system/sw/bin/ip"
        logger="/run/current-system/sw/bin/logger -t vpn-autoconnect"

        pin_lan_route() {
          "$ip" rule del pref "$lan_rule_pref" to "$home_lan" table main >/dev/null 2>&1
          "$ip" rule add pref "$lan_rule_pref" to "$home_lan" table main 2>&1 | $logger
        }
        unpin_lan_route() {
          "$ip" rule del pref "$lan_rule_pref" to "$home_lan" table main 2>&1 | $logger
        }

        case "$action" in
          up|down|connectivity-change) ;;
          *) exit 0 ;;
        esac

        if [ "$interface" = "$vpn_name" ]; then
          case "$action" in
            up) pin_lan_route; $logger "pinned $home_lan to the LAN route ($vpn_name up)" ;;
            down) unpin_lan_route; $logger "unpinned $home_lan ($vpn_name down)" ;;
          esac
          exit 0
        fi
        case "$interface" in
          lo) exit 0 ;;
        esac

        current_ssid=$("$nmcli" -t -f active,ssid dev wifi 2>/dev/null | awk -F: '$1=="yes"{print $2; exit}')
        vpn_up=0
        "$ip" link show "$vpn_name" >/dev/null 2>&1 && vpn_up=1

        if [ "$current_ssid" = "$home_ssid" ]; then
          if [ "$vpn_up" = 1 ]; then
            "$wg_quick" down "$vpn_name" 2>&1 | $logger
            unpin_lan_route
            $logger "took down $vpn_name (home: $current_ssid)"
          fi
        elif [ "$vpn_up" = 0 ]; then
          if "$wg_quick" up "$vpn_name" 2>&1 | $logger; then
            pin_lan_route
            $logger "brought up $vpn_name (ssid: $current_ssid, trigger: $action)"
          else
            $logger "failed to bring up $vpn_name (ssid: $current_ssid, trigger: $action), will retry on next event"
          fi
        fi
      '';
      type = "basic";
    }
  ];
}

