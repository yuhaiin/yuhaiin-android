package io.github.asutorufa.yuhaiin

import android.app.Application
import com.google.android.material.color.DynamicColors
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.database.YuhaiinDatabase

open class MainApplication : Application() {

    companion object {
        lateinit var db: YuhaiinDatabase
        lateinit var profile: Profile
    }

    override fun onCreate() {
        super.onCreate()

        db = YuhaiinDatabase.getInstance(applicationContext)
        profile = db.ProfileDao().getProfileByName(db.ProfileDao().getLastProfile() ?: "Default")
            ?: Profile(name = "Default")

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}