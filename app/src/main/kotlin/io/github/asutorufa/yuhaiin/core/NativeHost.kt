package io.github.asutorufa.yuhaiin.core

import android.util.Log

/** Android implementation of the platform callbacks the Go core invokes (yh_host). */
fun interface HostHandler {
    /** Returns the raw response, or null for an empty one. May throw; the core sees a failure. */
    fun handle(request: ByteArray?): ByteArray?
}

/**
 * Receives the Go core's callbacks. The JNI bridge calls the five methods below by name from
 * Go-owned threads, so none of them may assume a looper and none may block on the core.
 *
 * The object is a process singleton registered once; the components behind it (VPN, app catalog,
 * ...) register and unregister their handlers here. A callback without a handler fails safely.
 */
object NativeHost {
    private const val TAG = "NativeHost"

    @Volatile var socketProtect: ((Int) -> Boolean)? = null
    @Volatile var socketBind: ((Int, String) -> Long)? = null
    @Volatile var uidLookup: ((Int, String, Int, String, Int) -> Int)? = null
    @Volatile var vpnEstablish: ((Long, ByteArray) -> Int)? = null

    private val handlers = java.util.concurrent.ConcurrentHashMap<String, HostHandler>()

    fun register(operation: String, handler: HostHandler) {
        handlers[operation] = handler
    }

    fun unregister(operation: String) {
        handlers.remove(operation)
    }

    // ---- called from JNI (signatures are fixed in yuhaiin_jni.c) ----

    fun protect(fd: Int): Boolean = socketProtect?.invoke(fd) ?: true

    fun bind(fd: Int, iface: String): Long = socketBind?.invoke(fd, iface) ?: 0L

    fun dumpUid(protocol: Int, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): Int =
        uidLookup?.invoke(protocol, srcIp, srcPort, dstIp, dstPort) ?: 0

    /** Returns the descriptor, or a negative value with a UTF-8 message in error[0]. */
    fun establish(generation: Long, config: ByteArray, error: Array<ByteArray?>): Int {
        val establish = vpnEstablish
        if (establish == null) {
            error[0] = "provider_unavailable: no VPN provider".toByteArray()
            return -1
        }
        return try {
            establish(generation, config)
        } catch (e: Throwable) {
            Log.w(TAG, "establish generation $generation failed", e)
            error[0] = (e.message ?: e.toString()).toByteArray()
            -1
        }
    }

    fun call(operation: String, request: ByteArray?): ByteArray? {
        val handler = handlers[operation] ?: throw IllegalStateException("no handler for $operation")
        return handler.handle(request)
    }
}
