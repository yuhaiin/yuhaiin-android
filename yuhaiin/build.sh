current=$(dirname $(readlink -f "$0"))

# android ndk 自带clang有问题
# 直接是个txt里面写的clang-xx 然后任何参数都没传
# 需要手动将toolchains/llvm/prebuilt/linux-x86_64/bin/clang-xx软链接到clang和clang++
if [ -d "${HOME}/Documents/Programming/yuhaiin" ]; then
  cd ${HOME}/Documents/Programming/yuhaiin
elif [ -d "/mnt/shareSSD/code/yuhaiin" ];then
  cd /mnt/shareSSD/code/yuhaiin
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

if [ -d "${HOME}/.local/share/JetBrains/Toolbox/apps/AndroidStudio/ch-0/222.4345.14.2221.9252092/jbr/bin/" ]; then
  export PATH=$PATH:${HOME}/.local/share/JetBrains/Toolbox/apps/AndroidStudio/ch-0/222.4345.14.2221.9252092/jbr/bin/
fi

CGO_ENABLED=0 GOPROXY=https://goproxy.cn,direct gomobile bind -ldflags='-s -w -buildid=' -trimpath -target="android/arm64,android/amd64" -o ${current}/yuhaiin.aar -v ./cmd/android/
