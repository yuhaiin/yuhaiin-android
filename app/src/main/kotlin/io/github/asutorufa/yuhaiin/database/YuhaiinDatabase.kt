package io.github.asutorufa.yuhaiin.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, LastProfile::class],
    version = 3,
    exportSchema = false
)
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
                                it.ProfileDao().setLastProfile(LastProfile(name = "Default"))
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
                .addMigrations(MIGRATION_2_3)
                .build()
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN remote_dns TEXT NOT NULL DEFAULT '${DNS.DefaultRemoteJson}'")
                database.execSQL("ALTER TABLE profile ADD COLUMN local_dns TEXT NOT NULL DEFAULT '${DNS.DefaultLocalJson}'")
                database.execSQL("ALTER TABLE profile ADD COLUMN bootstrap_dns TEXT NOT NULL DEFAULT '${DNS.DefaultBootstrapJson}'")
            }
        }
    }
}