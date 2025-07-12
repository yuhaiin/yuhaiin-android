package io.github.asutorufa.yuhaiin

import android.app.Application
import com.google.android.material.color.DynamicColors
import go.Seq
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yuhaiin.Store
import yuhaiin.Yuhaiin

open class MainApplication : Application() {

    companion object {
        lateinit var store: Store
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)
        Yuhaiin.initDB(getExternalFilesDir("yuhaiin").toString(),applicationInfo.dataDir)
        store = Yuhaiin.getStore()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

fun Store.getStringSet(key: String?): Set<String> {
    val data = getString(key)
    if (data.isEmpty()) return HashSet()
    return Json.decodeFromString<Set<String>>(data)
}

fun Store.putStringSet(key: String?, values: Set<String?>?) {
    putString(key, Json.encodeToString(values))
}

fun Store.getStringMap(key: String?): Map<String, String> {
    val data = getString(key)
    if (data.isEmpty()) return HashMap()
    return Json.decodeFromString<Map<String, String>>(data)
}

fun Store.putStringMap(key: String?, values: Map<String, String>) {
    putString(key, Json.encodeToString(values))
}

fun Store.getStringArrayList(key: String): ArrayList<String> {
    val data = getString(key)
    if (data.isEmpty()) return ArrayList()
    return Json.decodeFromString<ArrayList<String>>(data)
}

fun Store.putStringArrayList(key: String, values: ArrayList<String>) {
    putString(key, Json.encodeToString(values))
}
