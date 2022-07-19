package io.github.asutorufa.yuhaiin.database

import androidx.preference.Preference
import io.github.asutorufa.yuhaiin.MainApplication

object Manager {
    val db: ProfileDao = MainApplication.db

    var profile: Profile = db.getProfileByName(db.getLastProfile() ?: "Default")

    fun switchProfile(name: String) {
        db.getProfileByName(name).let {
            profile = it
            db.setLastProfile(LastProfile(name = name))
        }
    }

    fun addProfile(name: String) {
        if (db.isProfileExists(name)) {
            throw Exception("Profile $name already exists")
        }
        profile = Profile(name = name)
        db.addProfile(profile)
        db.setLastProfile(LastProfile(name = name))
    }

    fun deleteProfile() {
        if (profile.name == "Default") {
            throw Exception("Default profile can't be deleted")
        }
        db.deleteProfile(profile)
        switchProfile("Default")
    }

    fun getProfileNames(): List<String> {
        return db.getProfileNames()
    }

    fun setOnPreferenceChangeListener(
        it: Preference,
        run: (p: Preference, newValue: Any) -> Unit
    ) {
        it.setOnPreferenceChangeListener { preference, newValue ->
            try {
                run(preference, newValue)
                db.updateProfile(profile)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}