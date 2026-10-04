package io.github.asutorufa.yuhaiin.data

import io.github.asutorufa.yuhaiin.Constants
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
