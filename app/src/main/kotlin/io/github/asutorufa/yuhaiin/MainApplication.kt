package io.github.asutorufa.yuhaiin

import android.app.Application
import com.google.android.material.color.DynamicColors
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.database.ProfileDao
import io.github.asutorufa.yuhaiin.database.YuhaiinDatabase

open class MainApplication : Application() {

    companion object {
        lateinit var db: ProfileDao
        lateinit var profile: Profile
    }

    override fun onCreate() {
        super.onCreate()

        db = YuhaiinDatabase.getInstance(applicationContext).ProfileDao()
        profile = db.getProfileByName(db.getLastProfile() ?: "Default")

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}