package io.github.asutorufa.yuhaiin.data

import io.github.asutorufa.yuhaiin.Constants
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import yuhaiin.Store

private class MemoryStore : Store {
    val values = mutableMapOf<String?, Any?>()

    override fun getString(key: String?) = values[key]?.toString().orEmpty()

    override fun getBoolean(key: String?) = values[key] as? Boolean ?: false

    override fun getInt(key: String?) = values[key] as? Int ?: 0

    override fun getLong(key: String?) = values[key] as? Long ?: 0L

    override fun getFloat(key: String?) = values[key] as? Float ?: 0f

    override fun getBytes(key: String?) = values[key] as? ByteArray

    override fun putString(key: String?, value: String?) {
        values[key] = value
    }

    override fun putBoolean(key: String?, value: Boolean) {
        values[key] = value
    }

    override fun putInt(key: String?, value: Int) {
        values[key] = value
    }

    override fun putLong(key: String?, value: Long) {
        values[key] = value
    }

    override fun putFloat(key: String?, value: Float) {
        values[key] = value
    }

    override fun putBytes(key: String?, value: ByteArray?) {
        values[key] = value
    }
}

class SettingsMigrationTest {
    @Test
    fun explicitFalseFromBrokenUiOverridesOldTrue() {
        val store = MemoryStore()
        store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, true)
        store.putBoolean(Constants.LEGACY_UI_HTTP_PROXY_KEY, false)
        migrateHttpProxyKey(store)
        assertFalse(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
    }

    @Test
    fun migratedKeyRemainsAuthoritativeOnSubsequentLaunches() {
        val store = MemoryStore()
        store.putBoolean(Constants.LEGACY_UI_HTTP_PROXY_KEY, true)
        migrateHttpProxyKey(store)
        assertTrue(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
        store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, false)
        migrateHttpProxyKey(store)
        assertFalse(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
    }

    @Test
    fun absentUiKeyPreservesExistingCoreValue() {
        val store = MemoryStore()
        store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, true)
        migrateHttpProxyKey(store)
        assertTrue(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
    }
}

class TunSettingsFlushTest {
    @Test
    fun fastStackAndControllerChangesArePersistedBeforeFlushCompletes() = runBlocking {
        val store = MemoryStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = AppSettings(store, scope, initialize = {})
            settings.ready.await()
            settings.set(Settings.tunStack, "lightweight")
            settings.set(Settings.tunCongestionControl, "reno")
            // An immediate second update must be the value read by native startup.
            settings.set(Settings.tunCongestionControl, "bbr")
            settings.flush()
            assertEquals("lightweight", store.getString(Constants.TUN_STACK_KEY))
            assertEquals("bbr", store.getString(Constants.TUN_CONGESTION_CONTROL_KEY))
            settings.set(Settings.tunStack, "")
            settings.flush()
            assertEquals("", store.getString(Constants.TUN_STACK_KEY))
        } finally {
            scope.cancel()
        }
    }
}
