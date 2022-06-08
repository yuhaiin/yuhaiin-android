package io.github.asutorufa.yuhaiin.database

import androidx.room.*

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile")
    fun getAll(): List<Profile>

    @Query("SELECT profile_name FROM profile")
    fun getProfileNames(): List<String>

    @Query("SELECT * FROM profile WHERE profile_name = :name")
    fun getProfileByName(name: String): Profile

    @Query("SELECT EXISTS(SELECT * FROM profile WHERE profile_name = :name)")
    fun isProfileExists(name: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addProfile(profile: Profile)

    @Update
    fun updateProfile(profile: Profile)

    @Delete
    fun deleteProfile(profile: Profile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setLastProfile(profile: LastProfile)

    @Query("SELECT name FROM last_profile WHERE `key`=0")
    fun getLastProfile(): String?
}