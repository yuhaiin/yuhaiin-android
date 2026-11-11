#!/usr/bin/env bash
# Verifies that the Allow LAN management server answers a client that is NOT on loopback.
#
# Android 17 (API 37) drops inbound LAN TCP for apps without ACCESS_LOCAL_NETWORK: the client's
# TCP handshake can succeed and its request is then never acknowledged, so a plain "port is
# open" check or `adb forward` (which reaches the app over loopback) proves nothing. The
# emulator's `redir` forwards a host port to the guest's eth0 address, so the app sees a real
# non-loopback peer and the check below matches what a LAN browser experiences.
#
# Usage: scripts/check-lan-inbound.sh [--expect-blocked] [guest-port]
#   ANDROID_SERIAL   emulator to test (default emulator-5554)
#   HOST_PORT        host port to redirect (default 18080)
#   PACKAGE          app whose 0.0.0.0 listener is tested (default io.github.asutorufa.yuhaiin)
#   --expect-blocked require a timeout instead of a response (permission not granted)
# Prerequisite: Allow LAN is on and the core is running; guest-port defaults to the port the app
# listens on for 0.0.0.0 (matched by UID in /proc/net/tcp).
set -euo pipefail

serial="${ANDROID_SERIAL:-emulator-5554}"
host_port="${HOST_PORT:-18080}"
package="${PACKAGE:-io.github.asutorufa.yuhaiin}"
expect_blocked=0
guest_port=""
for arg in "$@"; do
  case "$arg" in
    --expect-blocked) expect_blocked=1 ;;
    *) guest_port="$arg" ;;
  esac
done
adb=(adb -s "$serial")

if [ -z "$guest_port" ]; then
  # A listener on all addresses (state 0A) owned by the app's UID, so adbd or other listeners
  # on the device are never mistaken for the management server.
  uid=$("${adb[@]}" shell pm list packages -U "$package" | tr -d '\r' | sed -n 's/.*uid:\([0-9]*\).*/\1/p')
  [ -n "$uid" ] || { echo "$package is not installed" >&2; exit 2; }
  listeners=$("${adb[@]}" shell cat /proc/net/tcp /proc/net/tcp6 |
    awk -v uid="$uid" '$4=="0A" && $8==uid && $2 ~ /^0+:/ {split($2,a,":"); print a[2]}' | tr -d '\r')
  for hex in $listeners; do guest_port=$((16#$hex)); break; done
fi
[ -n "$guest_port" ] || { echo "no 0.0.0.0 listener found: is Allow LAN on?" >&2; exit 2; }

"${adb[@]}" emu redir remove "tcp:$host_port" >/dev/null 2>&1 || true
"${adb[@]}" emu redir add "tcp:$host_port:$guest_port" >/dev/null
trap '"${adb[@]}" emu redir remove "tcp:$host_port" >/dev/null 2>&1 || true' EXIT

status=0
for scheme in http https; do
  code=$(curl -sk -m 6 -o /dev/null -w '%{http_code}' "$scheme://127.0.0.1:$host_port/" || true)
  if [ "$expect_blocked" = 1 ]; then
    if [ "$code" = 000 ]; then echo "PASS $scheme blocked (no response), as expected"
    else echo "FAIL $scheme answered $code but the permission should be missing"; status=1; fi
  else
    if [ "$code" = 200 ]; then echo "PASS $scheme answered 200 on guest port $guest_port"
    else echo "FAIL $scheme got '$code' (000 = TCP data never answered)"; status=1; fi
  fi
done
exit $status
