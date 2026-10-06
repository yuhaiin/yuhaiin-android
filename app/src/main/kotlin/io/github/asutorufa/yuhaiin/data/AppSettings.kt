package io.github.asutorufa.yuhaiin.data

import android.util.Log
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.getStringSet
import io.github.asutorufa.yuhaiin.putStringSet
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yuhaiin.Store

/**
 * One persisted truth: the native Store. All UI writes use this process-lifetime IO queue.
 * Resume/IPC events reload from Store; a process-local Flow is only a UI snapshot, not an IPC bus.
 */
class AppSettings(
    private val store: Store,
    scope: CoroutineScope,
    lockFile: File? = null,
    initialize: () -> Unit,
) {
    private data class Pending(val sequence: Long, val value: Any)

    private val sequence = AtomicLong()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val _failure = MutableStateFlow<String?>(null)
    val failure = _failure.asStateFlow()
    private val requests = Channel<() -> Unit>(Channel.UNLIMITED)
    private val _snapshot = MutableStateFlow<Map<String, Any>>(emptyMap())
    val snapshot = _snapshot.asStateFlow()
    val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val initializeStore = {
                    migrateHttpProxyKey(store)
                    initialize()
                }
                if (lockFile == null) initializeStore()
                else
                    RandomAccessFile(lockFile, "rw").use { file ->
                        file.channel.lock().use { initializeStore() }
                    }
                reload()
                ready.complete(Unit)
            } catch (error: Exception) {
                ready.completeExceptionally(error)
                _failure.value = error.message
                Log.e("AppSettings", "Unable to initialize settings", error)
            }
            for (request in requests) {
                runCatching {
                    request()
                    reload()
                }
                    .onFailure {
                        _failure.value = it.message
                        reload()
                        Log.e("AppSettings", "Unable to update settings", it)
                    }
            }
        }
    }

    fun refresh() {
        requests.trySend {}
    }

    fun <T : Any> set(key: SettingKey<T>, value: T) {
        val update = Pending(sequence.incrementAndGet(), value)
        pending[key.name] = update
        _snapshot.update { it + (key.name to value) }
        requests.trySend {
            try {
                key.write(store, value)
                check(key.read(store) == value) { "Unable to save ${key.name}" }
                _failure.value = null
            } finally {
                pending.remove(key.name, update)
            }
        }
    }

    fun edit(block: (Store) -> Unit) {
        requests.trySend { block(store) }
    }

    suspend fun commit(block: (Store) -> Unit) {
        ready.await()
        val completed = CompletableDeferred<Unit>()
        requests.send {
            runCatching { block(store) }
                .fold(
                    onSuccess = { completed.complete(Unit) },
                    onFailure = { completed.completeExceptionally(it) },
                )
        }
        completed.await()
    }

    suspend fun flush() {
        ready.await()
        val completed = CompletableDeferred<Unit>()
        requests.send { completed.complete(Unit) }
        completed.await()
    }

    private fun reload() {
        _snapshot.value =
            Settings.all.associate { it.name to it.read(store) } +
                pending.mapValues { it.value.value }
    }
}

class SettingKey<T : Any>(
    val name: String,
    val default: T,
    val read: (Store) -> T,
    val write: (Store, T) -> Unit,
)

object Settings {
    private fun boolean(name: String, default: Boolean = false) =
        SettingKey(
            name,
            default,
            { store: Store -> store.getBoolean(name) },
            { store, value -> store.putBoolean(name, value) },
        )

    private fun text(name: String, default: String) =
        SettingKey(
            name,
            default,
            { store: Store -> store.getString(name).ifBlank { default } },
            { store, value -> store.putString(name, value) },
        )

    val httpProxy = boolean(Constants.APPEND_HTTP_PROXY_KEY)
    val allowLan = boolean(Constants.ALLOW_LAN_KEY)
    val speed = boolean(Constants.NOTIFICATION_SPEED_KEY)
    val perApp = boolean(Constants.PER_APP_KEY)
    val bypass = boolean(Constants.APP_BYPASS_KEY)
    val sniff = boolean(Constants.SNIFF_KEY, true)
    val dnsHijacking = boolean(Constants.DNS_HIJACKING_KEY, true)
    val autoConnect = boolean(Constants.AUTO_CONNECT_KEY)
    val trackNetwork = boolean(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY, true)
    val route = text(Constants.ROUTE_KEY, Constants.ALL_ROUTE)
    val processLookup = text(Constants.PROCESS_LOOKUP_MODE_KEY, "always")
    val metered = text(Constants.METERED_MODE_KEY, "auto")
    val mtu = text(Constants.VPN_MTU_PROFILE_KEY, "auto")
    val bootPolicy = text(Constants.BOOT_CONNECT_POLICY_KEY, "always")
    val tunDriver = text(Constants.TUN_DRIVER_KEY, "fdbased")
    val httpPort =
        SettingKey(
            Constants.HTTP_PORT_KEY,
            0,
            { store: Store -> store.getInt(Constants.HTTP_PORT_KEY) },
            { store, value -> store.putInt(Constants.HTTP_PORT_KEY, value) },
        )
    val applications =
        SettingKey(
            Constants.APP_LIST_KEY,
            emptySet<String>(),
            { store: Store -> store.getStringSet(Constants.APP_LIST_KEY) },
            { store, value -> store.putStringSet(Constants.APP_LIST_KEY, value) },
        )
    val routes =
        SettingKey(
            Constants.SAVED_ROUTES_LIST,
            emptySet<String>(),
            { store: Store -> store.getStringSet(Constants.SAVED_ROUTES_LIST) },
            { store, value -> store.putStringSet(Constants.SAVED_ROUTES_LIST, value) },
        )
    val all: List<SettingKey<*>> =
        listOf(
            httpProxy,
            allowLan,
            speed,
            perApp,
            bypass,
            sniff,
            dnsHijacking,
            autoConnect,
            trackNetwork,
            route,
            processLookup,
            mtu,
            metered,
            bootPolicy,
            tunDriver,
            httpPort,
            applications,
            routes,
        )
}

internal fun migrateHttpProxyKey(store: Store) {
    val migration = "android_http_proxy_key_migration_v1"
    if (store.getBoolean(migration)) return
    // The broken UI key takes precedence exactly once. Preserve explicit false as well as true.
    if (store.getString(Constants.LEGACY_UI_HTTP_PROXY_KEY).isNotBlank()) {
        store.putBoolean(
            Constants.APPEND_HTTP_PROXY_KEY,
            store.getBoolean(Constants.LEGACY_UI_HTTP_PROXY_KEY),
        )
    }
    store.putBoolean(migration, true)
}
