package io.github.asutorufa.yuhaiin.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object Converters {
    @TypeConverter
    fun fromString(value: String?): Set<String> {
        val listType: Type = object : TypeToken<Set<String?>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromSet(list: Set<String?>?): String {
        val gson = Gson()
        return gson.toJson(list)
    }
}
