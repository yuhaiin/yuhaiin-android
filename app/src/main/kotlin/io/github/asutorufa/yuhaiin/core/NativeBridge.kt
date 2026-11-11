package io.github.asutorufa.yuhaiin.core

/** A failed native call. [status] is a YH_ERR* code from yuhaiin_abi.h. */
class NativeException(val status: Int, message: String) : RuntimeException(message) {
    companion object {
        const val ERR = -1
        const val UNKNOWN_OP = -2
        const val BAD_REQUEST = -3
        const val PANIC = -4
        const val NO_HOST = -5
    }
}

/**
 * The only class that talks to libyuhaiin.so (Go core + JNI bridge in one library). Loading it
 * starts the Go runtime, so it must be
 * touched only in the ":bg" process (see [NativeRuntime]).
 */
object NativeBridge {
    /** Must equal YH_ABI_VERSION of the bundled core; checked in [NativeRuntime.ensureInit]. */
    const val ABI_VERSION = 1

    init {
        System.loadLibrary("yuhaiin")
    }

    /** yh_call: low-frequency control operation. Throws [NativeException] on failure. */
    @JvmStatic external fun nativeCall(operation: String, request: ByteArray?): ByteArray

    @JvmStatic external fun nativeAbiVersion(): Int

    /** Installs the callback table once per process; false when already installed. */
    @JvmStatic external fun nativeInstallHost(host: NativeHost): Boolean
}
