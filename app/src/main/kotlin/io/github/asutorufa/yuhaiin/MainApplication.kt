package io.github.asutorufa.yuhaiin

import android.app.Application
import com.google.android.material.color.DynamicColors

open class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}