# Android privacy and permission review

Reviewed against the official guidance on November 6, 2026. The Android app remains
distributed through GitHub. This change does not introduce distribution flavors or
replace the existing user-initiated updater. Downloaded updates now also undergo
package-name, signing-certificate/verified-rotation and non-downgrade checks before
requesting installation. Equal version codes remain allowed for rolling main builds;
the system installer remains the final authority.

## Permissions

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Proxy/DNS traffic, subscriptions, connectivity checks and update downloads. |
| `ACCESS_NETWORK_STATE` | Observe connectivity and the underlying physical network. |
| `RECEIVE_BOOT_COMPLETED` | Reconnect only when the user enabled auto-connect, accepted the current VPN disclosure, and Android VPN permission is still granted. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Run the user-visible VPN service with connection controls. |
| `POST_NOTIFICATIONS` | Show VPN state and connection controls on Android 13+. Requested after VPN disclosure; denial does not silently grant any other permission. |
| `QUERY_ALL_PACKAGES` | Populate the core per-app VPN include/bypass chooser, including background/system apps without launcher activities. Reading the complete inventory requires separate affirmative consent. A launcher-only `<queries>` declaration would omit these apps. UID/package lookup for active VPN connections is covered by the VPN disclosure. |
| `REQUEST_INSTALL_PACKAGES` | Existing GitHub self-update through the Android package installer, after user action. This is not a claim that self-updating is eligible for Google Play distribution. |

`WRITE_SETTINGS` is removed: the app does not modify system settings.
`CHANGE_NETWORK_STATE` is removed: the underlying-network monitor now registers
passive callbacks and does not actively request or retain networks. Required system
service binding permissions (`BIND_VPN_SERVICE`, `BIND_QUICK_SETTINGS_TILE`,
`MANAGE_DOCUMENTS`) protect components; they are not additional runtime requests.
AndroidX also generates the app-scoped `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
to protect its internal receivers; it does not grant access to other device data.

## Disclosures and control

- The VPN disclosure precedes notification/VPN system permission prompts and
  connection startup. It explains traffic, DNS, destination and app-owner metadata,
  configured recipients, background operation and protocol-dependent encryption.
- MainActivity, launcher/widget actions, the Quick Settings tile and boot reconnect
  respect the choice. The VPN service also checks it before handling direct,
  always-on, sticky restart, reconnect and pause-resume starts. Without consent it
  stops and returns `START_NOT_STICKY`.
- The installed-app chooser asks separately. The repository checks consent before
  reading even a cached inventory or an icon. Declining it does not prevent VPN use.
- Only the affirmative button grants consent. Back, outside-tap, cancellation and
  Activity recreation do not grant it. Disk errors fail closed.
- Versioned receipts are private files under `noBackupFilesDir/privacy-consent`.
  They are immediately visible across the UI and VPN processes, and cannot be
  imported through configuration backup, Android backup or device transfer. Bump
  the relevant receipt version when its data use/disclosure materially changes.
- **About → Privacy policy** works offline and includes developer/contact
  information, data use and recipients, storage, retention, deletion and withdrawal.
  Withdrawal clears both receipts and the in-memory inventory/icon cache, and
  disconnects the VPN. Configuration is retained; the policy explains how to delete it.
- LAN access has an adjacent disclosure about access to configuration, credentials
  and connection details. Log export requires confirmation before writing or sharing
  a file. Existing configuration-export credential warnings remain in place.

The in-app policy is localized in English and Japanese. Its English
web/repository counterpart is [privacy-policy.md](privacy-policy.md); update both
when changing data handling. Project issues are public and must not receive secrets.

## Before a future Google Play submission

These code changes prepare the app; they do not certify store eligibility or resolve
a Play Protect classification by themselves. Before submission:

1. Publish the reviewed privacy policy at a stable, public, non-geofenced URL and
   enter it in Play Console. Ensure the listing names the same app/developer and
   provides accurate support contact information.
2. Complete Data safety from the actual shipped APK, native dependencies and optional
   services, rather than declaring that a VPN processes no data. Audit SDK/native
   dependency behavior for that build.
3. Submit the VPN and broad package-visibility declarations with an accurate
   explanation of the core functionality. Record the normal consent, decline,
   connection and disconnection flows for review.
4. Meet the VPN encryption requirement for the Play build. Yuhaiin currently exposes
   user-configurable protocols, including unencrypted transports. The disclosure
   accurately describes this; disclosure alone does not meet the Play requirement
   to encrypt traffic from the device to a VPN tunnel endpoint.
5. Resolve the GitHub self-updater/`REQUEST_INSTALL_PACKAGES` policy for Play
   distribution. This was explicitly deferred; no flavor split is implemented here.
6. Verify current target API, signing, increasing version codes, foreground-service
   requirements and developer-account requirements for the intended release.
7. If Play Protect still flags a compliant build, use its classification appeal with
   the exact APK, SHA-256, signing-certificate fingerprint and build provenance.
   Do not instruct users to disable Play Protect.

## Verification

Run `:app:lintDebug`, `:app:testDebugUnitTest`, debug/release builds and
`:app:connectedDebugAndroidTest` on the API 34 and API 35 16 KB emulator configurations
described in [Android verification](../scripts/README.md).
`PrivacyConsentTest` covers independent, persistent receipts, cross-instance reads,
withdrawal and failure to persist. `PrivacyFlowsTest` covers actual disclosure UI,
rotation/cancellation, separate app-list consent, direct service bypass attempts,
withdrawal during a real VPN session, log-share cancellation and merged permissions.
`ApkIdentityTest` covers package/version/signer decisions and rotation;
`UpdateRecoveryTest` also parses a real signed APK and proves that a valid checksum
for another app cannot create an installer session. Other lifecycle tests explicitly establish the disclosure precondition before testing
their existing scenarios.

## Validation record (November 6, 2026)

- Final Android base: `15ea6ba` (#269), including the updated lightweight TUN
  settings and migration assertions.
- Final native AAR: upstream core `6dbb7b6f8627bc3d169582523fc119c25785a6e7`,
  workflow run `42149116854`, SHA-256
  `0d8f0892b1d374e45ce45dd90fd2d4e550d876cf25171a52fcb5ff765af3420c`.
- Temurin JDK 25.0.5, Gradle 9.8.2, SDK 37 and Build Tools 36.1.0.
- Debug lint: zero errors (128 warnings remain); all 60 JVM tests pass.
- Debug APK, instrumentation APK and both unsigned release APKs build successfully.
- Final ARM64 and x86_64 APK/ELF alignment checks pass. The merged release APK
  omits both removed permissions and retains the required permissions above.
- Before rebasing onto #269, all 11 focused instrumentation tests (`PrivacyFlowsTest`, `UpdateRecoveryTest`)
  pass on API 34 (4 KB pages) and API 35 (16 KB pages), using separate disposable
  emulator data directories. These include actual foreground-service rejection,
  withdrawal during a VPN session, policy access without consent/VPN, and installer
  preflight with real signed archives.
- The initial full API 34 run completed 27 tests: 26 passed. The old legacy-stack
  expectation in `TunStackDeviceTest.legacyAndLightweightSchedulersStartAndAcceptTCPUDP`
  fails: expected an empty stack but the native runtime reports `lightweight`.
  The selected core [explicitly migrates legacy settings to lightweight](https://github.com/yuhaiin/yuhaiin/blob/030a0a0d6df05c8f8d5d2e994faf8c614628bcd8/cmd/android/yuhaiin.go#L270).
  The final base already includes the updated assertions from #269. The subsequent
  full run was stopped at the user's request before reaching the TUN test; a green
  full-suite result is not claimed.
- The initial full API 35 run completed 22 tests successfully, including all six privacy
  tests present at that point, then terminates during
  `StatusMonitoringTest.backgroundWithoutConsumersStopsSnapshotsAndPeriodicHealthThenResumes`.
  The process received SIGTRAP in the system WebView's `libmonochrome_64.so`
  (`MemoryInfra` thread). The 11 focused tests passed independently afterwards.
  A temporary diagnostic also reproduced the same failure without starting a VPN:
  open and close the dashboard, then request a Chromium memory dump through CDP.
  The API 35 r05 image uses WebView `124.0.6367.219`. Further emulator/WebView
  investigation was deferred at the user's request; no provider replacement or
  application workaround is shipped. The full device suite is **not fully green**.

## Official references

- [Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Mobile Unwanted Software principles](https://developers.google.com/android/play-protect/mobile-unwanted-software)
- [User Data: disclosures, privacy policy, retention and deletion](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [VpnService policy](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en)
- [Broad package visibility](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en-GB)
- [Package installation permission](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en-GB)
- [ConnectivityManager callbacks and permissions](https://developer.android.com/reference/android/net/ConnectivityManager)

- [Android signing identities and verified key rotation](https://developer.android.com/reference/android/content/pm/SigningInfo)
