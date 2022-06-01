package io.github.asutorufa.yuhaiin.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Profile::class, lastProfile::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class YuhaiinDatabase : RoomDatabase() {
    abstract fun ProfileDao(): ProfileDao

    companion object {
        private var instance: YuhaiinDatabase? = null

        fun getInstance(context: Context): YuhaiinDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    try {
                        it.ProfileDao().getLastProfile().let { name ->
                            if (name == null)
                                it.ProfileDao().setLastProfile(lastProfile(name = "Default"))
                        }
                        if (!it.ProfileDao().isProfileExists("Default")) {
                            it.ProfileDao().addProfile(Profile(name = "Default"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    instance = it
                }
            }
        }

        private fun buildDatabase(context: Context): YuhaiinDatabase {
            return Room.databaseBuilder(context, YuhaiinDatabase::class.java, "yuhaiin")
                .allowMainThreadQueries()
                .build()
        }
    }
}