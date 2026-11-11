# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate
# Called by name from the JNI bridge in libyuhaiin.so (cmd/androidlib/jni_android.c in the core).
-keep class io.github.asutorufa.yuhaiin.core.NativeException { <init>(int, java.lang.String); }
-keep class io.github.asutorufa.yuhaiin.core.NativeHost {
    boolean protect(int);
    long bind(int, java.lang.String);
    int dumpUid(int, java.lang.String, int, java.lang.String, int);
    int establish(long, byte[], byte[][]);
    byte[] call(java.lang.String, byte[]);
}
-keep class io.github.asutorufa.yuhaiin.core.NativeBridge { native <methods>; }
