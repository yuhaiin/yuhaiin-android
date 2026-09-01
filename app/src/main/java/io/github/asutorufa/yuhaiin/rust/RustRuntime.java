package io.github.asutorufa.yuhaiin.rust;

/**
 * Small JNI facade for the Rust runtime host.
 *
 * The VPN service detaches the ParcelFileDescriptor before calling start.
 * Rust then owns that descriptor and closes it when the runtime shuts down.
 */
public final class RustRuntime {
    static {
        System.loadLibrary("yuhaiin_android");
    }

    private RustRuntime() {
    }

    public static long start(
            String database,
            int tunFd,
            int mtu,
            String ipv4,
            int ipv4Prefix,
            String ipv6,
            int ipv6Prefix,
            String webRoot
    ) {
        return nativeStart(database, tunFd, mtu, ipv4, ipv4Prefix, ipv6, ipv6Prefix, webRoot);
    }

    public static void stop(long handle) {
        nativeStop(handle);
    }

    public static int apiPort(long handle) {
        return nativeApiPort(handle);
    }

    private static native long nativeStart(
            String database,
            int tunFd,
            int mtu,
            String ipv4,
            int ipv4Prefix,
            String ipv6,
            int ipv6Prefix,
            String webRoot
    );

    private static native void nativeStop(long handle);

    private static native int nativeApiPort(long handle);
}
