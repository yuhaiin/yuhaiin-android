#

[![GitHub license](https://img.shields.io/github/license/Asutorufa/yuhaiin-android)](https://github.com/Asutorufa/yuhaiin-android/blob/master/LICENSE)
[![releases](https://img.shields.io/github/release-pre/asutorufa/yuhaiin-android.svg)](https://github.com/Asutorufa/yuhaiin-android/releases)
![languages](https://img.shields.io/github/languages/top/asutorufa/yuhaiin-android.svg)

## yuhaiin-android

Android Client for [yuhaiin](https://github.com/Asutorufa/yuhaiin).

## Support Version

Android 5.0+ (API level 21)

## Rust runtime build

The VPN data plane is hosted by yuhaiin-rust through a JNI library. The
Android UI still uses the existing compatibility store while the migration is
in progress.

Build the native libraries from local checkouts with:

```bash
ANDROID_NDK_HOME=/path/to/android-ndk \
YUHAIIN_RUST_ROOT=/Volumes/PSSD/Documents/yuhaiin-rust \
./scripts/build-rust-native.sh
```

The script builds arm64-v8a and x86_64 libraries into
app/build/generated/rustJniLibs. The Rust checkout is intentionally kept
outside this repository.

## Download

[Release](https://github.com/Asutorufa/yuhaiin-android/releases)

## Build

```shell
git clone https://github.com/yuhaiin/yuhaiin.git yuhaiin/code
./yuhaiin/build.sh
export KEYSTORE_PATH=keystore.keystore
export KEY_ALIAS=key0
KEYSTORE_PASSWORD=keystore_password
KEY_PASSWORD=key_password
./gradlew app:assembleRelease --stacktrace
```

## Screenshot

![screenshot](https://raw.githubusercontent.com/Asutorufa/yuhaiin-android/main/assets/image.png "screenshot")

## Acknowledgement

- [bndeff/socksdroid](https://github.com/bndeff/socksdroid)
- [Navigation Componentのいい感じのアニメーションを検討する【サンプルアプリあり】](https://at-sushi.work/blog/21/)  
