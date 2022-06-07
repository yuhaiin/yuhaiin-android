package io.github.asutorufa.yuhaiin.util

import androidx.preference.PreferenceDataStore
import java.util.*

class DataStore : PreferenceDataStore() {
    private val store = Collections.synchronizedMap(HashMap<String, Any>())
    private fun put(key: String?, value: Any?) {
        store[key] = value
    }

    override fun putString(key: String?, value: String?) = put(key, value)
    override fun putStringSet(key: String?, values: Set<String?>?) = put(key, values)
    override fun putInt(key: String?, value: Int) = put(key, value)
    override fun putLong(key: String?, value: Long) = put(key, value)
    override fun putFloat(key: String?, value: Float) = put(key, value)
    override fun putBoolean(key: String?, value: Boolean) = put(key, value)

    override fun getString(key: String?, defValue: String?): String? =
        store[key]?.toString() ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? =
        store[key] as Set<String?>? ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        store[key]?.toString()?.toInt() ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        store[key]?.toString()?.toLong() ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        store[key]?.toString()?.toFloat() ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        store[key]?.toString()?.toBoolean() ?: defValue
}