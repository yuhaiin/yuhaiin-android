#

[![GitHub license](https://img.shields.io/github/license/Asutorufa/yuhaiin-android)](https://github.com/Asutorufa/yuhaiin-android/blob/master/LICENSE)
[![releases](https://img.shields.io/github/release-pre/asutorufa/yuhaiin-android.svg)](https://github.com/Asutorufa/yuhaiin-android/releases)
![languages](https://img.shields.io/github/languages/top/asutorufa/yuhaiin-android.svg)

## yuhaiin-android

Android Client for [yuhaiin](https://github.com/Asutorufa/yuhaiin).

## Support Version

Android 5.0+ (API level 21)

## Download

[Release](https://github.com/Asutorufa/yuhaiin-android/releases)

## Build

Use JDK 25 (the version used by CI), Android SDK Platform 37 and Build Tools 36.1.0. Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your Android SDK, or configure the SDK with `sdk.dir` in `local.properties`. The commands below use Bash, Git and the GitHub CLI (`gh`).

```bash
git clone https://github.com/Asutorufa/yuhaiin-android.git
cd yuhaiin-android
```

Download the native AAR from the latest successful `go.yml` workflow run on the upstream `main` branch, using an authenticated GitHub CLI (`gh auth login`). CI selects the same way:

```bash
set -e
core_run=$(gh run list --repo yuhaiin/yuhaiin --branch main --workflow go.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId // empty')
test -n "$core_run"
gh run download "$core_run" --repo yuhaiin/yuhaiin --name yuhaiin.aar --dir yuhaiin
```

Android CI records the selected upstream commit, workflow run and downloaded AAR checksum in `build-provenance.txt`. See [Android verification](scripts/README.md) for more details.

Run lint and unit tests, then build the release APKs:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:assembleRelease --stacktrace
```

APKs are written to `app/build/outputs/apk/release/` for `arm64-v8a` and `x86_64`. Without signing configuration, their names end in `-release-unsigned.apk`. For an installable debug build, run `./gradlew :app:assembleDebug`; APKs are written to `app/build/outputs/apk/debug/`.

To sign a release with your own keystore, export all four signing variables before running the release build. Use an absolute keystore path:

```bash
export KEYSTORE_PATH=/absolute/path/to/release.keystore
export KEY_ALIAS=your_key_alias
read -r -s -p 'Keystore password: ' KEYSTORE_PASSWORD
printf '\n'
read -r -s -p 'Key password: ' KEY_PASSWORD
printf '\n'
export KEYSTORE_PASSWORD KEY_PASSWORD
./gradlew :app:assembleRelease --stacktrace
```

Signed APK names end in `-release.apk`. Release alignment checks, device regressions and baseline profile instructions are documented in [Android verification](scripts/README.md).

## Screenshot

![screenshot](assets/image-v2.png "screenshot")

## Acknowledgement

- [bndeff/socksdroid](https://github.com/bndeff/socksdroid)
- [Navigation Componentのいい感じのアニメーションを検討する【サンプルアプリあり】](https://at-sushi.work/blog/21/)  
