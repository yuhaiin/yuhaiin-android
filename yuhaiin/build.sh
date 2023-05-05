current=$(dirname "$(readlink -f "$0")")

if [ -d "${HOME}/.local/storage/Android/Sdk/" ]; then
  export ANDROID_HOME="${HOME}/.local/storage/Android/Sdk/"
fi

if [ -d "${HOME}/.local/storage/Android/android-ndk-r23c" ]; then
  export ANDROID_NDK_HOME="${HOME}/.local/storage/Android/android-ndk-r23c"
fi

java="${HOME}/.local/share/JetBrains/Toolbox/apps/AndroidStudio/ch-0"
if [ -d "${java}" ]; then
  for file in "${java}"/*; do
    if [ -f "${file}/jbr/bin/javac" ]; then
      export PATH=$PATH:"${file}/jbr/bin"
      break
    fi
  done
fi

set -x && \
cd "${current}"/code && \
gomobile bind -ldflags='-s -w -buildid=' -trimpath -target="android/arm64,android/amd64" -androidapi 21 -o "${current}"/yuhaiin.aar -v ./cmd/android/
