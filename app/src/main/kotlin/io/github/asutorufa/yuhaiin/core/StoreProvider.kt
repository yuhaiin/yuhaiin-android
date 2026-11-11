package io.github.asutorufa.yuhaiin.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Preference access for the UI process, which never loads the Go runtime. It runs in ":bg" (not
 * exported: only this app can reach it) and forwards `store.get` / `store.put` to the core, so
 * there is one SQLite writer and no second Go runtime. Android starts the process on demand.
 *
 * Calls are synchronous like the former in-process Store; nothing else is exposed.
 */
class StoreProvider : ContentProvider() {
    companion object {
        const val EXTRA_REQUEST = "request"
        const val EXTRA_RESPONSE = "response"
        const val EXTRA_ERROR = "error"
        private val allowed = setOf("store.get", "store.put")

        fun authority(context: Context) = "${context.packageName}.store"
    }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = Bundle()
        if (method !in allowed) {
            result.putString(EXTRA_ERROR, "unsupported method $method")
            return result
        }
        try {
            NativeRuntime.ensureInit(context!!)
            result.putByteArray(
                EXTRA_RESPONSE,
                NativeCore.callRaw(method, extras?.getByteArray(EXTRA_REQUEST)),
            )
        } catch (e: Throwable) {
            result.putString(EXTRA_ERROR, e.message ?: e.toString())
        }
        return result
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}

/** [Store] for processes without the Go runtime: every call is served by ":bg". */
fun remoteStore(context: Context): Store {
    val resolver = context.applicationContext.contentResolver
    val uri = Uri.parse("content://${StoreProvider.authority(context)}")
    return JsonStore { operation, request ->
        val reply =
            resolver.call(uri, operation, null, Bundle().apply { putByteArray(StoreProvider.EXTRA_REQUEST, request) })
                ?: throw java.io.IOException("preference service is unavailable")
        reply.getString(StoreProvider.EXTRA_ERROR)?.let { throw java.io.IOException(it) }
        reply.getByteArray(StoreProvider.EXTRA_RESPONSE) ?: ByteArray(0)
    }
}
