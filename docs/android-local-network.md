# Android 17 local network permission

Android 17 (API 37) enforces *Local Network Protections* for apps that target API 37. yuhaiin
targets 37, so `android.permission.ACCESS_LOCAL_NETWORK` (runtime, `NEARBY_DEVICES` group) is
required for traffic to and from the local network. Apps targeting 36 or lower keep implicit
access through `INTERNET`, and Android 16 and older never see the permission.

## What breaks without it

The OS drops LAN TCP (both outgoing connections and *accepting* inbound ones) and LAN UDP unicast,
multicast and broadcast. Loopback and Internet traffic are unaffected. For an inbound LAN client
this is a timeout: the handshake may complete, then the request data is never acknowledged. That
is why the in-app WebView (`127.0.0.1`) and Termux worked while a Mac on the same Wi-Fi hung, and
why turning the VPN off or restarting the core changed nothing.

`allow_lan` and the permission are independent. `allow_lan` only makes the core bind
`0.0.0.0` instead of `127.0.0.1`; the permission decides whether the OS lets LAN peers reach the
socket.

## Behaviour

- The manifest declares the permission. Nothing is requested below API 37.
- When Allow LAN is on and the permission is missing, the foreground `MainActivity` shows the
  standard system dialog once. After a refusal it only shows a dismissible notice (never another
  dialog): **Allow** asks again while the system still permits it, **Open settings** leads to the
  app's system settings once the system will not ask again.
- The state is read live from the OS (`LocalNetworkAccess.state`), so it is correct after a core
  restart, a process kill or a revocation in system settings. `YuhaiinCoreService` (`:bg`) never
  asks; it only logs a warning when Allow LAN is on without the permission.
- The Web UI can trigger the same flow with the native action
  `yuhaiin-android://action/network/local` (main frame, user gesture, local page only).
- The Android platform API stays loopback-only: LAN clients still get 403 on `/android/v1/*`
  even with Allow LAN and the permission.

## Verification

`scripts/check-lan-inbound.sh` (see `scripts/README.md`) checks that a non-loopback client gets
HTTP and HTTPS responses, and with `--expect-blocked` that the missing permission is the cause.
