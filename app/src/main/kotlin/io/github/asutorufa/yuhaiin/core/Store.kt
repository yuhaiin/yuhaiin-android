package io.github.asutorufa.yuhaiin.core

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Typed preferences backed by the core's SQLite database (the former gomobile Store).
 * Reads of a missing or unreadable key return the core's default; writes throw on failure.
 */
interface Store {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun putBytes(key: String, value: ByteArray)
    fun getString(key: String): String
    fun getInt(key: String): Int
    fun getBoolean(key: String): Boolean
    fun getLong(key: String): Long
    fun getFloat(key: String): Float
    fun getBytes(key: String): ByteArray
}

/**
 * [Store] over `store.get` / `store.put`. [transport] carries one request to the core: JNI in
 * ":bg", a ContentProvider call from the UI process.
 */
class JsonStore(private val transport: (operation: String, request: ByteArray) -> ByteArray) :
    Store {
    private fun get(key: String, type: String): JsonElement? =
        try {
            val request = buildJsonObject {
                put("keys", JsonArray(listOf(buildJsonObject { put("key", key); put("type", type) })))
            }
            val response = transport("store.get", request.toString().toByteArray())
            Json.parseToJsonElement(response.decodeToString())
                .jsonObject["values"]!!.jsonArray.first()
        } catch (e: Exception) {
            Log.e("Store", "read $key failed", e)
            null
        }

    private fun put(key: String, type: String, value: JsonElement) {
        val request = buildJsonObject {
            put(
                "entries",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("key", key)
                            put("type", type)
                            put("value", value)
                        }
                    )
                ),
            )
        }
        transport("store.put", request.toString().toByteArray())
    }

    override fun putString(key: String, value: String) = put(key, "string", JsonPrimitive(value))
    override fun putInt(key: String, value: Int) = put(key, "int", JsonPrimitive(value))
    override fun putBoolean(key: String, value: Boolean) = put(key, "bool", JsonPrimitive(value))
    override fun putLong(key: String, value: Long) = put(key, "long", JsonPrimitive(value))
    override fun putFloat(key: String, value: Float) = put(key, "float", JsonPrimitive(value))
    override fun putBytes(key: String, value: ByteArray) =
        put(key, "bytes", JsonPrimitive(kotlin.io.encoding.Base64.Default.encode(value)))

    override fun getString(key: String): String =
        get(key, "string")?.jsonPrimitive?.contentOrNull.orEmpty()

    override fun getInt(key: String): Int = get(key, "int")?.jsonPrimitive?.int ?: 0
    override fun getBoolean(key: String): Boolean = get(key, "bool")?.jsonPrimitive?.boolean ?: false
    override fun getLong(key: String): Long = get(key, "long")?.jsonPrimitive?.long ?: 0L
    override fun getFloat(key: String): Float = get(key, "float")?.jsonPrimitive?.double?.toFloat() ?: 0f

    override fun getBytes(key: String): ByteArray =
        get(key, "bytes")?.jsonPrimitive?.contentOrNull?.let { kotlin.io.encoding.Base64.Default.decode(it) }
            ?: ByteArray(0)
}
