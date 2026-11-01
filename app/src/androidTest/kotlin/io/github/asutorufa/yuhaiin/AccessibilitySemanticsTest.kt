package io.github.asutorufa.yuhaiin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.asutorufa.yuhaiin.compose.ConnectionCard
import io.github.asutorufa.yuhaiin.compose.ScreenTitle
import io.github.asutorufa.yuhaiin.compose.SectionHeading
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import org.junit.Rule
import org.junit.Test

class AccessibilitySemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun label(id: Int) = compose.activity.getString(id)

    private val polite =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun connectionStatusStaysOneLiveNodeAcrossStateChanges() {
        var state by mutableStateOf(State.DISCONNECTED)
        var error by mutableStateOf<String?>(null)
        compose.setContent { ConnectionCard(state, error, {}, {}, {}) }

        compose.onNodeWithText(label(R.string.status_disconnected)).assert(polite)

        // Not via CONNECTING: it crashes this harness on dispose, though not the app itself.
        compose.runOnIdle {
            state = State.ERROR
            error = "no route"
        }
        val failed = label(R.string.status_error)
        compose
            .onNodeWithText(failed)
            .assert(polite)
            .assert(hasContentDescription("$failed. no route"))
        compose.onNode(hasText(label(R.string.status_disconnected))).assertDoesNotExist()
    }

    @Test
    fun titlesAreHeadings() {
        compose.setContent {
            ScreenTitle("Settings")
            SectionHeading("Network")
        }
        compose.onNodeWithText("Settings").assert(isHeading())
        compose.onNodeWithText("Network").assert(isHeading())
    }
}
