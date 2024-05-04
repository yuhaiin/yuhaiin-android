package io.github.asutorufa.yuhaiin.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Converters {
    @TypeConverter
    fun fromString(value: String?): Set<String> {
        return Json.decodeFromString(value.orEmpty())
    }

    @TypeConverter
    fun fromSet(list: Set<String?>?): String {
       return  Json.encodeToString(list.orEmpty())
    }

    @TypeConverter
    fun fromMapString(value: String?): Map<String, String> {
        return Json.decodeFromString(value.orEmpty())
    }

    @TypeConverter
    fun fromMap(list: Map<String, String>?): String {
        return Json.encodeToString(list.orEmpty())
    }

    @TypeConverter
    fun stringToDNS(value: String?): DNS {
        return  Json.decodeFromString(value.orEmpty())
    }

    @TypeConverter
    fun fromDNS(dns: DNS?): String {
        return Json.encodeToString(dns)
    }

    @TypeConverter
    fun stringToBypass(value: String?): Bypass {
        return  Json.decodeFromString(value.orEmpty())
    }

    @TypeConverter
    fun fromBypass(b: Bypass?): String {
        return Json.encodeToString(b)
    }
}
