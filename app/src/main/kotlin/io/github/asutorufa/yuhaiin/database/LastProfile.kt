package io.github.asutorufa.yuhaiin.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_profile")
data class LastProfile(
    @PrimaryKey @ColumnInfo(name = "key", defaultValue = "0") val key: Int = 0,
    @ColumnInfo(name = "name", defaultValue = "Default")
    val name: String = "Default"
)