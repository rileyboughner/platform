{ config, pkgs, username, lib, ... }: 
let
  standardUser = username;
in
{
  environment.systemPackages = with pkgs; [
    mpv
    vivaldi
    libreoffice
    nautilus
    home-manager
    obsidian
  ];




}
