#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ ${PREFIX:-} != */com.termux/files/usr || $(id -u) == 0 ]]; then
  printf '%s\n' 'Execute na sessão normal do Termux no Android.' >&2
  exit 1
fi
if [[ $# != 1 || ! -f $1 ]]; then
  printf '%s\n' 'Uso: bash start-termux.sh caminho-da-chave-publica.pub' >&2
  exit 1
fi
if [[ ! -x "$PREFIX/bin/sshd" ]]; then
  printf '%s\n' 'Instale OpenSSH no Termux com pkg install openssh.' >&2
  exit 1
fi

mapfile -t keys < "$1"
if [[ ${#keys[@]} != 1 || ${keys[0]} != ssh-ed25519\ * ]] || ! ssh-keygen -lf "$1" >/dev/null; then
  printf '%s\n' 'Forneça uma única chave pública SSH Ed25519.' >&2
  exit 1
fi

state="$HOME/.neon-bridge"
mkdir -p "$state"
chmod 700 "$state"
if [[ ! -s "$state/host_ed25519" || ! -s "$state/host_ed25519.pub" ]]; then
  ssh-keygen -q -t ed25519 -N '' -f "$state/host_ed25519"
fi
printf 'restrict,pty %s\n' "${keys[0]}" > "$state/authorized_keys"
user="$(id -un)"
cat > "$state/sshd_config" <<CONFIG
ListenAddress 127.0.0.1
Port 38222
HostKey "$state/host_ed25519"
PidFile "$state/sshd.pid"
AuthorizedKeysFile "$state/authorized_keys"
AllowUsers $user
AuthenticationMethods publickey
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitEmptyPasswords no
PermitRootLogin no
DisableForwarding yes
PermitUserEnvironment no
PermitUserRC no
MaxAuthTries 3
MaxSessions 2
LoginGraceTime 20
ClientAliveInterval 30
ClientAliveCountMax 2
CONFIG

"$PREFIX/bin/sshd" -t -f "$state/sshd_config"
printf '\nUsuário SSH: %s\nChave pública deste aparelho:\n' "$user"
cat "$state/host_ed25519.pub"
ssh-keygen -lf "$state/host_ed25519.pub"
printf '\nServidor local em 127.0.0.1:38222. Encerre a sessão SSH e pressione Ctrl+C para parar.\n'
exec "$PREFIX/bin/sshd" -D -e -f "$state/sshd_config"
