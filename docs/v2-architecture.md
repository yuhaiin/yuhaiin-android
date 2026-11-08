# yuhaiin Android v2 architecture

Status: **experimental, `v2` branches only**. Nothing here is merged into `main`.

v2 turns the Android app from a native UI that starts the Go core together with
the VPN into **Go core + yuhaiin Web UI + a small Android platform adapter**.

## Repositories and branches

| Repository | Branch | What changes |
|---|---|---|
| yuhaiin/yuhaiin (core) | `v2` | On-demand platform TUN, Android VPN provider bridge, Android platform API |
| yuhaiin/yuhaiin-android | `v2` | `YuhaiinCoreService`, slim `YuhaiinVpnService`, WebView main screen, `v2` flavor |
| yuhaiin/yuhaiin-react | `v2` | Android page, VPN card, per-app VPN, platform detection |
| yuhaiin/yuhaiin.github.io | `v2` (written by React CI) | v2 Web UI build embedded only by core `v2` builds |

`main` of every repository is unchanged. CI on the v2 branches never tags or
publishes releases.

## Process layout

```
Main process (UI)                        ":bg" process
MainActivity                             YuhaiinCoreService (foreground, specialUse)
 ├─ WebView: http://127.0.0.1:<port>      ├─ Go core (App): DNS, FakeIP, routing, nodes,
 ├─ consent / VpnService.prepare()        │   inbounds, HTTP+HTTPS management server,
 ├─ file chooser, native screens          │   Android platform API (/android/v1/)
 └─ Binder client ──── IYuhaiinVpnBinder ─┤
                                          ├─ VpnCoordinator (in-process, no Binder)
                                          │   implements yuhaiin.VpnProvider
                                          └─ YuhaiinVpnService (VpnService)
                                              Builder, routes, DNS, MTU, per-app,
                                              protect(), underlying networks, revoke
```

* Core and VPN share `:bg`, so the TUN descriptor never crosses Binder.
* The WebView runs in the UI process (and its renderer in its own sandbox), so a
  WebView or UI crash does not stop the core. Verified on an emulator: killing
  the UI process leaves the core and the management API running.

## Lifecycle

* **Core**: `MainActivity.onStart` starts `YuhaiinCoreService` (foreground) and
  binds it. The core starts **without a TUN descriptor**. Stopping the VPN never
  stops the core. `Exit` (notification) stops the core, which closes its TUN
  inbound and releases the VPN.
* **Background policy** (`core_background_policy`): `while_needed` (default) stops
  the core 60 s after the VPN is off and no UI is attached; `always` keeps it
  running for proxy/LAN use.
* **Crash recovery**: the service is `START_STICKY`. After the `:bg` process dies,
  Android restarts it, the core starts again and the persisted TUN inbound state
  restores the VPN. An unexpected core exit inside a live process is retried
  with backoff (2 s, 5 s, 15 s). Verified on an emulator with `kill -9`.

## The VPN is the core's TUN inbound

The Android VPN is a TUN inbound whose device name is `platform://vpn`.
`enabled` in its configuration is the **desired** state; the platform VPN status
is the **actual** state. Both are shown in the Web UI.

```
Web UI / tile / notification / boot → enable TUN inbound (core)
  → inbound runtime → platformvpn.Accepter
  → VpnProvider.Establish(generation, config)      (Kotlin, ":bg")
       consent? VpnService.prepare()? → else "consent_required"/"permission_required"
       VpnService.Builder(...).establish() → ParcelFileDescriptor
  → lightweight TUN stack on fd://<fd> (Go dups it)
  → status published to Kotlin (notification, tile, widget) and the Web UI
```

### Descriptor ownership

* Kotlin keeps each generation's `ParcelFileDescriptor` until the core calls
  `Release(generation)`; Go reads its own duplicate. Neither side closes the
  other's descriptor.
* Every `Establish` gets a new process-wide generation. `Release` and revoke
  name a generation; stale callbacks are ignored (`TestStaleRevokeIsIgnored`).
* Start, reconfigure and close of an accepter are serialized; repeated saves of
  an unchanged configuration are no-ops.
* A failed start is an inbound runtime error (`failed`, with the reason) and
  never stops the core.

### Reconfiguration (FakeIP, routes, DNS, MTU, per-app)

* The core computes the requested `platformvpn.Config` (addresses, DNS, MTU,
  explicit routes/excludes, **active FakeIP pools**, platform revision).
* FakeIP pool changes arrive through the existing `UpdateFakeIPRanges`
  subscription; bursts are coalesced (300 ms). TUN contract changes (MTU,
  routes, portal, stack tuning) are applied in place through a new optional
  `ReconfigureContract` hook in `Inbound.SaveContract`. Android-side preference
  changes (per-app, route profile, metered, HTTP proxy) bump the platform
  revision via the platform API.
* Nothing happens when the requested configuration equals the applied one.
  Network switches only update `setUnderlyingNetworks`; they never rebuild.
* **Make before break**: a new interface and stack are created first; only then
  is the old stack closed and its generation released. If `establish()` is
  refused, the old interface stays. If the new stack fails, the previous
  configuration is restored.
* Android replaces the old tun interface during `establish()`, so TCP sessions of
  the old stack can reset. Measured switch time on an API 34 emulator: 28–36 ms
  (`lastSwitchMillis` in the status). The core, the management server and other
  inbounds are not restarted, and the WebView is not reloaded.

### FakeIP defaults

The VPN uses the pools the core actually uses (`Fakedns.FakeIPRanges()`), so an
empty database yields `198.18.0.0/16` and `2001:2::/64` from
`pkg/net/defaults`, and configured ranges always win. Verified on an emulator by
changing the pool through the RPC API: the VPN route moved to the new pool.

## Web UI and the platform API

* The app's main screen is the Web UI of the running core. A native screen is
  shown only while the core starts or after a failure (retry, logs).
* A restarted core changes the platform token (and maybe the port); the WebView
  reconnects then. A VPN rebuild does not reload it.
* The Web UI detects Android through `GET /android/v1/capabilities`, never the
  User-Agent. Device controls appear only when that response says `session:true`.

### Security model

`/android/v1/*` (except `capabilities`) requires **all** of:

1. a loopback peer — LAN clients get 403 even with LAN management enabled;
2. the per-start platform token in the cookie `yuhaiin_android_platform`
   (`HttpOnly; SameSite=Strict; Path=/android/`). Only the in-app WebView gets
   it, from `:bg` over the non-exported Binder service;
3. for state changes, `X-Yuhaiin-Platform-Request: 1` and a JSON body; no CORS
   is ever granted for this path. `Sec-Fetch-Site: cross-site` is rejected.

Origin/Host are not used as the boundary. There is no `addJavascriptInterface`.
OS dialogs (VPN disclosure, `VpnService.prepare`, notification permission) are
opened only by `MainActivity` after the page navigates to a fixed
`yuhaiin-android://action/<name>` link from a user gesture in the main frame of
the local page (`NativeAction`). `POST /android/v1/vpn {"enabled":true}` is
refused with `consent_required` / `permission_required` and nothing is persisted
until the user completes those dialogs. Background components (tile, boot,
always-on) never show dialogs.

## Builds, data and rollback

* Flavors: `v2` (default, `io.github.asutorufa.yuhaiin.v2`, label "yuhaiin v2",
  version suffix `-v2`) and `stable` (stable package name, not built by v2 CI).
  The v2 app installs next to the stable app with separate data. Android still
  allows only one active VPN at a time.
* Update track: the v2 flavor only accepts releases tagged `v2-*`; stable builds
  reject them, and package-name validation rejects cross-package APKs.
* Data migration (core, at startup): a v1 `fd://<n>` TUN inbound (or the seeded
  desktop `tun://tun0`) is converted once to `platform://vpn`, **disabled**, with
  automatic addresses/MTU. Upgrading never connects a VPN by itself. A v1 APK
  installed over the same data rewrites the inbound on its next VPN start, so
  rollback needs no restore step (`TestEnsureAndroidTUNMigratesLegacyAndStaysRollbackSafe`).
  Preference keys are unchanged.

## Native UI status

| Area | v2 |
|---|---|
| Dashboard, VPN connect/state, core state | Web UI (native dashboard removed from start) |
| Routing, DNS/FakeIP, TUN settings, nodes | Web UI (existing core pages) |
| Android settings (MTU, metered, route profile choice, boot, background, notification speed) | Web UI Android page |
| Per-app VPN | Web UI (no icons) and native list (icons) |
| Route profile editor, logs, backup/restore, updates, privacy | **Native**, opened from the Web UI via `NativeAction` |
| Old native settings screen | Kept as "Android settings" in the WebView menu |

Native screens were not deleted because their Web UI replacements do not exist
yet. WebView cannot save script-generated blob downloads without a JavaScript
bridge, so backup export from the Web UI redirects to the native backup screen.
