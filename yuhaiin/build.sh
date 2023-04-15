current=$(dirname "$(readlink -f "$0")")

# android ndk 自带clang有问题
# 直接是个txt里面写的clang-xx 然后任何参数都没传
# 需要手动将toolchains/llvm/prebuilt/linux-x86_64/bin/clang-xx软链接到clang和clang++
if [ -d "${HOME}/Documents/Programming/yuhaiin" ]; then
  cd "${HOME}"/Documents/Programming/yuhaiin || exit 1
elif [ -d "/mnt/shareSSD/code/yuhaiin" ]; then
  cd /mnt/shareSSD/code/yuhaiin || exit 1
else
  echo "can't find yuhaiin dir"
  exit 1
fi

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

GOPROXY=https://goproxy.cn,direct gomobile bind -ldflags='-s -w -buildid=' -trimpath -target="android/arm64,android/amd64" -o "${current}"/yuhaiin.aar -v ./cmd/android/
