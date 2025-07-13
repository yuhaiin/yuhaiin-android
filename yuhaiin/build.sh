current=$(dirname "$(readlink -f "$0")")

if [ -d "${HOME}/.local/storage/Android/Sdk/" ]; then
  export ANDROID_HOME="${HOME}/.local/storage/Android/Sdk/"
fi

if [ -d "/Volumes/PSSD/Library/Android/Sdk" ]; then
  export ANDROID_HOME="/Volumes/PSSD/Library/Android/Sdk"
fi

if [ -d "${HOME}/.local/storage/Android/android-ndk-r23c" ]; then
  export ANDROID_NDK_HOME="${HOME}/.local/storage/Android/android-ndk-r23c"
fi

if [ -d "/opt/android-ndk" ]; then
  export ANDROID_NDK_HOME="/opt/android-ndk"
fi


export PATH=$PATH:"${HOME}/.local/share/JetBrains/Toolbox/apps/android-studio/jbr/bin"
export PATH=$PATH:"/Volumes/PSSD/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin"

if [ -d "/Volumes/PSSD/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Volumes/PSSD/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

#java="${HOME}/.local/share/JetBrains/Toolbox/apps/android-studio"
#if [ -d "${java}" ]; then
#  for file in "${java}"/*; do
#    if [ -f "${file}/jbr/bin/javac" ]; then
#      export PATH=$PATH:"${file}/jbr/bin"
#      break
#    fi
#  done
#fi

set -x && \
  cd "${current}"/../../yuhaiin && \
  make yuhaiin_android_aar && \
  cp yuhaiin.aar "${current}"/yuhaiin.aar
