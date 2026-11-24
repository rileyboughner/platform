# oh-my-posh bakes the resolved Nix store path of its own binary into the cached
# init script (~/.cache/oh-my-posh/init.*.zsh). Nix rebuilds can change that path
# without oh-my-posh noticing, and weekly nix.gc then deletes the old one out from
# under it. Detect a stale cache and clear it before sourcing.
for _omp_cached_init in $HOME/.cache/oh-my-posh/init.*.zsh(N); do
  _omp_cached_bin=$(sed -n "s/.*_omp_executable=\$'\(.*\)'.*/\1/p" "$_omp_cached_init" 2>/dev/null | head -n1)
  if [[ -n "$_omp_cached_bin" && ! -x "$_omp_cached_bin" ]]; then
    rm -rf "$HOME/.cache/oh-my-posh"
    break
  fi
done
unset _omp_cached_init _omp_cached_bin

eval "$(oh-my-posh init zsh --config $HOME/.config/ohmyposh/zen.toml)"

_todo() {
  case $CURRENT in
    2)
      local -a cmds
      cmds=(
        'task:manage todoist tasks'
        'config:view or change todo settings'
        'tui:launch the vim-motion gum interface'
      )
      _describe 'command' cmds
      ;;
    3)
      if [[ ${words[2]} == task ]]; then
        local -a actions
        actions=(
          'complete:mark a task done by number or text'
          'create:add a new task'
          'remove:delete a task by number or text'
          'list:show tasks (all, overdue, or today)'
        )
        _describe 'action' actions
      elif [[ ${words[2]} == config ]]; then
        local -a keys
        keys=('default_mode:which interface todo launches by default')
        _describe 'option' keys
      fi
      ;;
    4)
      if [[ ${words[2]} == task && ${words[3]} == list ]]; then
        local -a views
        views=('overdue:only overdue tasks' 'today:only tasks due today')
        _describe 'view' views
      elif [[ ${words[2]} == config && ${words[3]} == default_mode ]]; then
        local -a modes
        modes=('text:plain unix-style output' 'tui:vim-motion gum interface')
        _describe 'value' modes
      fi
      ;;
  esac
}
compdef _todo todo

_boner() {
  case $CURRENT in
    2)
      local -a cmds
      cmds=(
        'install:stow config files'
        'rebuild:rebuild the NixOS config'
        'update:update flake inputs'
        'push:push dotfiles to git'
        'pull:pull dotfiles from git'
        'secrets:manage agenix secrets'
        'vm:connect to a VM via SPICE'
        'wallpaper:manage wallpapers'
        'theme:manage themes'
        'sync:manage sync'
        'torrent:manage manual torrents'
        'cast:wake the TV and launch Moonlight'
        'help:show usage'
      )
      _describe 'command' cmds
      ;;
    3)
      local -a sub
      case ${words[2]} in
        secrets)
          sub=('update:edit a secret' 'list:list secrets')
          _describe 'command' sub
          ;;
        theme)
          sub=('list:list themes' 'current:show current theme' 'set:set a theme' 'auto:auto theme')
          _describe 'command' sub
          ;;
        vm)
          sub=('cyber-defence:Cyber Defence VM' 'windows-11:Windows 11 VM')
          _describe 'vm' sub
          ;;
        wallpaper)
          sub=('set:set a wallpaper' 'random:random wallpaper' 'restore:restore wallpaper' 'warm:pre-cache wallpaper colors' 'recolor:recolor a wallpaper')
          _describe 'command' sub
          ;;
        sync)
          sub=('on:enable sync' 'off:disable sync' 'mode:set sync mode' 'status:show sync status')
          _describe 'command' sub
          ;;
        torrent)
          sub=('add:add a torrent by magnet link' 'list:list manual torrents' 'download:download a completed torrent' 'remove:remove a torrent and delete its files')
          _describe 'command' sub
          ;;
      esac
      ;;
    4)
      case ${words[2]} in
        theme)
          if [[ ${words[3]} == set ]]; then
            local -a themes
            themes=("${(@f)$(boner theme list 2>/dev/null)}")
            if (( ${#themes[@]} > 0 )); then
              compadd -a themes
            fi
          fi
          ;;
        secrets)
          if [[ ${words[3]} == update ]]; then
            local -a secrets
            secrets=("${(@f)$(boner secrets list 2>/dev/null)}")
            if (( ${#secrets[@]} > 0 )); then
              compadd -a secrets
            fi
          fi
          ;;
        wallpaper)
          if [[ ${words[3]} == set || ${words[3]} == recolor ]]; then
            _files -W "$HOME/.wallpapers" -g '*.(jpg|jpeg|png|webp)'
          fi
          ;;
        sync)
          if [[ ${words[3]} == mode ]]; then
            local -a modes
            modes=('unified:one theme across machines' 'split:separate themes per machine')
            _describe 'mode' modes
          fi
          ;;
        torrent)
          if [[ ${words[3]} == download ]]; then
            local -a names
            names=("${(@f)$(boner torrent _complete 2>/dev/null)}")
            if (( ${#names[@]} > 0 )); then
              compadd -a names
            fi
          elif [[ ${words[3]} == remove ]]; then
            local -a names
            names=("${(@f)$(boner torrent _complete-all 2>/dev/null)}")
            if (( ${#names[@]} > 0 )); then
              compadd -a names
            fi
          fi
          ;;
      esac
      ;;
  esac
}
compdef _boner boner
