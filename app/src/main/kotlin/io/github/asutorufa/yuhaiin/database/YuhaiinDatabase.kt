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
    version = 8,
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

                        if (!it.ProfileDao().isProfileExists("Default"))
                            it.ProfileDao().addProfile(Profile(name = "Default"))
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
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8
                )
                .build()
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN remote_dns TEXT NOT NULL DEFAULT '${DNS.DefaultRemoteJson}'")
                database.execSQL("ALTER TABLE profile ADD COLUMN local_dns TEXT NOT NULL DEFAULT '${DNS.DefaultLocalJson}'")
                database.execSQL("ALTER TABLE profile ADD COLUMN bootstrap_dns TEXT NOT NULL DEFAULT '${DNS.DefaultBootstrapJson}'")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN rule_update_bypass_url TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN dns_hijacking INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN append_http_proxy_to_system INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN bypass TEXT NOT NULL DEFAULT '${Bypass.DefaultJson}'")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE profile ADD COLUMN log_level INTEGER NOT NULL DEFAULT 2")
            }
        }
    }
}