# Android verification

Use the installed Android SDK and JDK 25. `local.properties` selects the SDK; download the latest available native AAR from a successful upstream `go.yml` build on `main`, as described in the [Build instructions](../README.md#build).

Automatic Android CI runs lint, unit tests, the release build and static APK/ELF alignment checks. Emulator regressions run on demand: in GitHub Actions select **Android device regression** and **Run workflow**. API 34 and Android 15 (16 KB) run in parallel with KVM enabled, a five-minute boot timeout and a twenty-minute job timeout. Run these before releasing changes to navigation, persistence, VPN lifecycle or the native core. Ordinary pushes and pull requests do not start emulators or build the instrumentation APK.

- Build, lint and unit tests: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
- Native AAR selection regressions: `python3 scripts/test-resolve-latest-core.py` (requires `jq`).
- Device regressions: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`
- API 35 16 KB emulator: use SDK image `system-images;android-35;google_apis_ps16k;x86_64`; check `adb shell getconf PAGESIZE` is `16384`, use at least 4 GB emulator RAM, then run the same device regressions.
- APK/ELF gate: `python3 scripts/check-native-pages.py app/build/outputs/apk/release/yuhaiin-arm64-v8a-release-unsigned.apk --sdk "$ANDROID_HOME"`
- Baseline profile: set the app language to English, then `./gradlew :app:generateReleaseBaselineProfile` on an API 33+ emulator/device. Review generated source profiles before committing them.
- Release macrobenchmarks: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` on a physical device. The tests compare cold startup with/without profiles and measure frame timings while navigating, searching and scrolling. Emulator values are useful for test execution, not phone performance claims.

The native integration tests exercise VPN consent, failed startup, repair/reconnect and the real JNI preference contract. Run them on a dedicated emulator/device with disposable app data. The install recovery test grants the install app-op; revoking it kills the app, so reset it from the host after the test run with `adb shell appops set io.github.asutorufa.yuhaiin REQUEST_INSTALL_PACKAGES default`. Large drafts are stored in `no_backup/route-drafts`, not Android saved-instance-state or cloud backups. Cloud backup and device transfer include the native external `yuhaiin` directory, while excluding logs, caches and device-specific installer/document IDs.

Unsigned local builds use `-release-unsigned.apk`; CI signing produces `-release.apk`. `resolve-latest-core.sh` queries published AAR artifacts, skips expired artifacts and verifies each candidate's individual workflow run, selecting the newest successful Go build on `main`. This avoids GitHub's workflow-run listing API, which can return stale history. Android CI downloads the selected artifact by ID and records the commit, run ID, artifact ID and AAR SHA-256 in `build-provenance.txt`.
