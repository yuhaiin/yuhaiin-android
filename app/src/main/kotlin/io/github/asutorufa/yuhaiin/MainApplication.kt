package io.github.asutorufa.yuhaiin

import android.app.Application
import androidx.preference.Preference
import com.google.android.material.color.DynamicColors
import go.Seq
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.database.ProfileDao
import io.github.asutorufa.yuhaiin.database.YuhaiinDatabase

open class MainApplication : Application() {

    companion object {
        lateinit var db: ProfileDao
        lateinit var profile:Profile

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

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)
        db = YuhaiinDatabase.getInstance(applicationContext).ProfileDao()
        profile = db.getProfileByName(db.getLastProfile() ?: "Default")
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}