package io.github.asutorufa.yuhaiin

import android.app.Application
import com.google.android.material.color.DynamicColors
import io.github.asutorufa.yuhaiin.database.ProfileDao
import io.github.asutorufa.yuhaiin.database.YuhaiinDatabase

open class MainApplication : Application() {

    companion object {
        lateinit var db: ProfileDao
    }

    override fun onCreate() {
        super.onCreate()

        db = YuhaiinDatabase.getInstance(applicationContext).ProfileDao()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}