package io.github.asutorufa.yuhaiin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs on real Android APIs; JVM navigation tests cannot catch List.removeLast linkage errors. */
class AndroidFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int, vararg arguments: Any) = compose.activity.getString(id, *arguments)

    private fun open(id: Int) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label(id)))
        compose.onNodeWithText(label(id)).performClick()
    }

    @Test
    fun childPageReturnsOnApi34WithoutCrashing() {
        open(R.string.route_config_title)
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        // Returning preserves the home scroll position; expose the status card explicitly.
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText(label(R.string.status_disconnected)).assertIsDisplayed()
    }

    @Test
    fun routeDraftSurvivesRotationAndShowsDiscardConfirmation() {
        open(R.string.route_config_title)
        compose.onNodeWithText(label(R.string.route_all_label)).performClick()
        val editor = hasSetTextAction()
        compose.onNode(editor).performTextReplacement("10.0.0.0/8\nINVALID_DRAFT")
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithText(label(R.string.route_invalid_line, 2), substring = false)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNode(editor).assertTextContains("INVALID_DRAFT", substring = true)
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        compose.onNodeWithText(label(R.string.unsaved_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.discard)).performClick()
    }

    @Test
    fun applicationSelectionIsPersistedBeforeLeavingScreen() {
        open(R.string.adv_app_list_title)
        compose.onNode(hasSetTextAction()).performTextReplacement(BuildConfig.APPLICATION_ID)
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(hasText(BuildConfig.APPLICATION_ID) and isToggleable())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNode(hasText(BuildConfig.APPLICATION_ID) and isToggleable()).performClick()
        val chosen = MainApplication.settings.snapshot.value[Constants.APP_LIST_KEY] as Set<*>
        assertTrue(BuildConfig.APPLICATION_ID in chosen)
        runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
        assertTrue(
            BuildConfig.APPLICATION_ID in MainApplication.store.getStringSet(Constants.APP_LIST_KEY)
        )
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        open(R.string.adv_app_list_title)
        compose.onNode(hasSetTextAction()).performTextReplacement(BuildConfig.APPLICATION_ID)
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(hasText(BuildConfig.APPLICATION_ID) and isToggleable())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNode(hasText(BuildConfig.APPLICATION_ID) and isToggleable()).assertIsDisplayed()
        compose
            .onNode(hasText(BuildConfig.APPLICATION_ID) and isToggleable())
            .performClick() // restore selection
    }
}
