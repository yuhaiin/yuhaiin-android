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
        fun changeStore(name: String) {
            store = Yuhaiin.getStore(name)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)
        Yuhaiin.initDB(getExternalFilesDir("yuhaiin").toString(),applicationInfo.dataDir)
        store = Yuhaiin.getStore("Default")
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

