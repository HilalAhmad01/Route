package app.metroline.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.metroline.data.Network
import app.metroline.data.Router
import app.metroline.data.Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PickerScreenTest {
    @get:Rule val rule = createComposeRule()
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())

    private fun typing(vararg keys: String, expectTop: String) {
        val deps = AppDeps(net, Router(net), Store(ApplicationProvider.getApplicationContext(), net), Nav())
        rule.setContent { CompositionLocalProvider(LocalDeps provides deps) { PickerScreen(PickTarget.From) } }
        for (k in keys) rule.onNode(hasSetTextAction()).performTextInput(k)
        rule.onNodeWithText(expectTop).assertIsDisplayed()
    }

    // "Adarsh Nagar" heads the unfiltered list and also loosely matches "d" and "r";
    // the list must not follow it down and hide the best matches.
    @Test fun topMatchStaysInViewWhileTypingDw() = typing("d", "w", expectTop = "Dwarka - Kakrola")

    @Test fun topMatchStaysInViewWhileTypingRo() = typing("r", "o", expectTop = "Rohini East")
}
