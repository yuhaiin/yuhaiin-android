package io.github.asutorufa.yuhaiin.core

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The wire format of store.get / store.put, shared with cmd/androidlib. */
class JsonStoreTest {
    private val requests = mutableListOf<Pair<String, String>>()
    private var response = """{"values":[]}"""

    private val store = JsonStore { op, req ->
        requests += op to req.decodeToString()
        response.toByteArray()
    }

    @Test
    fun getsEachType() {
        response = """{"values":["hello"]}"""
        assertEquals("hello", store.getString("s"))
        response = """{"values":[true]}"""
        assertTrue(store.getBoolean("b"))
        response = """{"values":[9007199254740993]}"""
        assertEquals(9007199254740993L, store.getLong("l"))
        response = """{"values":[1.5]}"""
        assertEquals(1.5f, store.getFloat("f"))
        response = """{"values":["AQID"]}"""
        assertContentEquals(byteArrayOf(1, 2, 3), store.getBytes("y"))
        response = """{"values":[null]}"""
        assertContentEquals(ByteArray(0), store.getBytes("empty"))

        val first = Json.parseToJsonElement(requests.first().second).jsonObject["keys"]!!.jsonArray[0].jsonObject
        assertEquals("s", first["key"]!!.jsonPrimitive.content)
        assertEquals("string", first["type"]!!.jsonPrimitive.content)
        assertEquals("store.get", requests.first().first)
    }

    @Test
    fun putsEncodeValueAndType() {
        store.putString("k", "vé😀")
        store.putInt("i", 7)
        store.putBytes("y", byteArrayOf(1, 2, 3))
        val entries = requests.map {
            Json.parseToJsonElement(it.second).jsonObject["entries"]!!.jsonArray[0].jsonObject
        }
        assertEquals("vé😀", entries[0]["value"]!!.jsonPrimitive.content)
        assertEquals("int", entries[1]["type"]!!.jsonPrimitive.content)
        assertEquals("AQID", entries[2]["value"]!!.jsonPrimitive.content)
        assertTrue(requests.all { it.first == "store.put" })
    }

    @Test
    fun readFailureReturnsDefaultButWriteFailureThrows() {
        val broken = JsonStore { _, _ -> throw java.io.IOException("down") }
        assertEquals("", broken.getString("k"))
        assertEquals(0, broken.getInt("k"))
        assertEquals(false, broken.getBoolean("k"))
        assertFailsWith<java.io.IOException> { broken.putString("k", "v") }
    }
}
