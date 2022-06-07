package io.github.asutorufa.yuhaiin.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): Set<String> {
        val listType: Type = object : TypeToken<Set<String?>?>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromSet(list: Set<String?>?): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun stringToDNS(value: String?): DNS {
        val dnsType: Type = object : TypeToken<DNS?>() {}.type
        return gson.fromJson(value, dnsType)
    }

    @TypeConverter
    fun fromDNS(dns: DNS?): String {
        return gson.toJson(dns)
    }
}
