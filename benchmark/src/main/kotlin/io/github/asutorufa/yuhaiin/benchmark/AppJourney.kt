package io.github.asutorufa.yuhaiin.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val PACKAGE = "io.github.asutorufa.yuhaiin"

/** Operate by semantics instead of screen coordinates; run with the app language set to English. */
fun UiDevice.openPage(title: String) {
    val visible = findObject(By.text(title))
    if (visible != null) {
        visible.click()
        settle()
        return
    }
    // Home retains its scroll position when returning from a child destination.
    repeat(4) {
        try {
            findObject(By.scrollable(true))?.scroll(Direction.UP, 1f)
        } catch (_: androidx.test.uiautomator.StaleObjectException) {}
        settle()
    }
    repeat(24) {
        try {
            val target = findObject(By.text(title))
            if (target != null) {
                target.click()
                settle()
                return
            }
            findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.7f)
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            // Navigation/scrolling can replace accessibility nodes between lookup and action.
        }
        settle()
    }
    val dump =
        java.io.File(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext
                .cacheDir,
            "journey-failure.xml",
        )
    dumpWindowHierarchy(dump)
    error("Destination not found: $title")
}

private fun UiDevice.settle() {
    waitForIdle()
    // Accessibility nodes can disappear before Nav3's outgoing entry stops receiving input.
    android.os.SystemClock.sleep(350)
    waitForIdle()
}

private fun UiDevice.scrollList(required: Boolean = true) {
    // Log batches replace accessibility nodes while a gesture is in progress. Capture only
    // the bounds, then send the gesture through UiDevice instead of holding a UiObject2.
    repeat(5) {
        try {
            val node = wait(Until.findObject(By.scrollable(true)), 5000)
            if (node == null && !required) return
            val bounds = checkNotNull(node).visibleBounds
            swipe(
                bounds.centerX(),
                bounds.bottom - bounds.height() / 8,
                bounds.centerX(),
                bounds.top + bounds.height() / 8,
                20,
            )
            settle()
            return
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            settle()
        }
    }
    error("Scrollable content kept replacing its accessibility node")
}

fun UiDevice.appJourney() {
    openPage("App List")
    checkNotNull(wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)).text =
        "android"
    settle()
    scrollList()
    checkNotNull(wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)).text = ""
    checkNotNull(wait(Until.findObject(By.desc("Back")), 5000)).click()
    settle()
    openPage("Route Config")
    checkNotNull(wait(Until.findObject(By.desc("Add Route")), 5000))
    checkNotNull(wait(Until.findObject(By.text("All traffic")), 5000)).click()
    checkNotNull(wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
    settle()
    checkNotNull(wait(Until.findObject(By.desc("Back")), 5000)).click()
    check(wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
    settle()
    checkNotNull(wait(Until.findObject(By.desc("Add Route")), 5000))
    checkNotNull(wait(Until.findObject(By.desc("Back")), 5000)).click()
    check(wait(Until.gone(By.desc("Back")), 5000))
    openPage("Logcat")
    checkNotNull(wait(Until.findObject(By.text("Search logs")), 5000))
    settle()
    // A quiet release process may have fewer log entries than fit on one screen.
    scrollList(required = false)
}
