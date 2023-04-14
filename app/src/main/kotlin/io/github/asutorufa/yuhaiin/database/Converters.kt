package io.github.asutorufa.yuhaiin.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): Set<String> {
        return gson.fromJson(value, TypeToken.getParameterized(Set::class.java,String::class.java).type)
    }

    @TypeConverter
    fun fromSet(list: Set<String?>?): String {
        return gson.toJson(list)
    }


    @TypeConverter
    fun fromMapString(value: String?): Map<String, String> {
        return gson.fromJson(value, TypeToken.getParameterized(Map::class.java,String::class.java,String::class.java).type)
    }

    @TypeConverter
    fun fromMap(list: Map<String, String>?): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun stringToDNS(value: String?): DNS {
        return gson.fromJson(value, TypeToken.get(DNS::class.java).type)
    }

    @TypeConverter
    fun fromDNS(dns: DNS?): String {
        return gson.toJson(dns)
    }

    @TypeConverter
    fun stringToBypass(value: String?): Bypass {
        return gson.fromJson(value, TypeToken.get(Bypass::class.java).type)
    }

    @TypeConverter
    fun fromBypass(b: Bypass?): String {
        return gson.toJson(b)
    }
}
