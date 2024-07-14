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
