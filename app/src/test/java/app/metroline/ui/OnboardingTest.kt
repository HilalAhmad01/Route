package app.metroline.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.metroline.data.Network
import app.metroline.data.Router
import app.metroline.data.Store
import app.metroline.data.Tour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A common phone size; Robolectric's default screen is much smaller than any phone Route runs on.
@Config(qualifiers = "w360dp-h740dp")
@RunWith(RobolectricTestRunner::class)
class OnboardingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var backAtStart = 0
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val store = Store(ApplicationProvider.getApplicationContext(), net)

    private fun show() {
        val deps = AppDeps(net, Router(net), store, Nav())
        // still = true: the demos show their finished state instead of looping, so the test clock can settle.
        rule.setContent {
            CompositionLocalProvider(LocalDeps provides deps) { Onboarding(store::finishTour, { backAtStart++ }, still = true) }
        }
    }

    @Test fun walkThroughAndChooseSmartCard() {
        assertEquals(Tour.FirstRun, store.tour)
        show()
        rule.onNodeWithText("Two stations. One route.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Home in one tap.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Saved trips live in Saved.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Do you use a smart card?").assertIsDisplayed()

        rule.onNodeWithText("Smart card").performClick()
        assertTrue(store.smartCard)
        rule.onNodeWithText("Start planning").performClick()
        assertEquals(Tour.Off, store.tour)
    }

    @Test fun skipEndsTheTour() {
        show()
        rule.onNodeWithText("Skip").performClick()
        assertEquals(Tour.Off, store.tour)
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun backGoesToThePreviousScreenThenLeaves() {
        show()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Home in one tap.").assertIsDisplayed()
        back()
        rule.onNodeWithText("Two stations. One route.").assertIsDisplayed()
        assertEquals(0, backAtStart)
        back()
        assertEquals(1, backAtStart)
    }

    // The tour is still on screen while it fades out; Back then must not count as leaving the tour.
    @Test fun backIgnoredOnceTheTourIsClosed() {
        show()
        rule.runOnUiThread { store.finishTour() }
        rule.waitForIdle() // the next frame, as after tapping Skip
        back()
        assertEquals(0, backAtStart)
    }

    @Test fun smartCardChoiceCanBeUndone() {
        show()
        repeat(3) { rule.onNodeWithText("Next").performClick() }
        rule.onNodeWithText("Smart card").performClick()
        rule.onNodeWithText("Token").performClick()
        assertFalse(store.smartCard)
    }
}
