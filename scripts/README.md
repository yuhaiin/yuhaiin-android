# Android verification

Use the installed Android SDK and JDK 21. `local.properties` selects the SDK; the native AAR is pinned in `yuhaiin/core-source.properties`.

- Build, lint and unit tests: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
- Device regressions: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`
- API 35 16 KB emulator: use SDK image `system-images;android-35;google_apis_ps16k;x86_64`; check `adb shell getconf PAGESIZE` is `16384`, use at least 4 GB emulator RAM, then run the same device regressions.
- APK/ELF gate: `python3 scripts/check-native-pages.py app/build/outputs/apk/release/yuhaiin-arm64-v8a-release-unsigned.apk --sdk "$ANDROID_HOME"`
- Baseline profile: set the app language to English, then `./gradlew :app:generateReleaseBaselineProfile` on an API 33+ emulator/device. Review generated source profiles before committing them.
- Release macrobenchmarks: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` on a physical device. The tests compare cold startup with/without profiles and measure frame timings while navigating, searching and scrolling. Emulator values are useful for test execution, not phone performance claims.

The native integration tests exercise VPN consent, failed startup, repair/reconnect and the real JNI preference contract. Run them on a dedicated emulator/device with disposable app data. The install recovery test grants the install app-op; revoking it kills the app, so reset it from the host after the test run with `adb shell appops set io.github.asutorufa.yuhaiin REQUEST_INSTALL_PACKAGES default`. Large drafts are stored in `no_backup/route-drafts`, not Android saved-instance-state or cloud backups. Cloud backup and device transfer include the native external `yuhaiin` directory, while excluding logs, caches and device-specific installer/document IDs.

Unsigned local builds use `-release-unsigned.apk`; CI signing produces `-release.apk`. Core download is fixed to the recorded workflow run and SHA-256. When GitHub expires that artifact, update the provenance file after reviewing a new successful core build; do not silently substitute the latest run.
