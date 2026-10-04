package io.github.asutorufa.yuhaiin.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule val profile = BaselineProfileRule()

    @Test
    fun startup() =
        profile.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
        }

    @Test
    fun coreScreens() =
        profile.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.appJourney()
        }
}
