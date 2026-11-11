package io.github.asutorufa.yuhaiin.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Process-wide start of the Go runtime. Only the ":bg" process may call this: the UI process
 * never loads libyuhaiin.so and reaches preferences through [StoreProvider].
 */
object NativeRuntime {
    private const val TAG = "NativeRuntime"
    private var initialized = false

    /** Idempotent and thread safe; content provider calls can arrive before Application.onCreate. */
    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        check(NativeBridge.nativeAbiVersion() == NativeBridge.ABI_VERSION) { "core ABI mismatch" }
        check(NativeBridge.nativeInstallHost(NativeHost)) { "native host was not installed" }
        val save =
            (app.getExternalFilesDir("yuhaiin") ?: File(app.filesDir, "yuhaiin")).apply { mkdirs() }
        NativeCore.init(
            savePath = save.absolutePath,
            configLockPath = File(app.noBackupFilesDir, "configuration.lock").absolutePath,
            // The management HTTPS key stays in internal storage, outside Auto Backup, and is
            // reused across VPN reconnects and app restarts.
            managementTlsDir = File(app.noBackupFilesDir, "management-tls").absolutePath,
        )
        initialized = true
        Log.i(TAG, "core library ready, ABI ${NativeBridge.ABI_VERSION}")
    }

    /** True only where the Go runtime was started (":bg"). */
    val isReady: Boolean
        @Synchronized get() = initialized

    /** Store bound to the Go core in this process. */
    val store: Store by lazy { JsonStore(NativeCore::callRaw) }
}
