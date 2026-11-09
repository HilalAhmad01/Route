package app.metroline.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.metroline.data.Network
import app.metroline.data.Store
import app.metroline.data.Tour
import app.metroline.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tour and the saved-trip note, inside the whole app. */
@Config(qualifiers = "w360dp-h740dp")
@RunWith(RobolectricTestRunner::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private lateinit var store: Store
    private val nav = Nav()

    @Before fun setUp() {
        // Animations off in Android's settings: the tour's demos show their end state (and the test clock can settle).
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        store = Store(ctx, net)
    }

    private fun show() = rule.setContent { App(net, store, nav) {} }

    @Test fun newUserGetsTheTourThenPlan() {
        show()
        rule.onNodeWithText("Two stations. One route.").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Show route").assertIsDisplayed()
        assertEquals(Tour.Off, store.tour)
    }

    @Test fun savedTripNoteShowsOnceThenGoes() {
        store.finishTour()
        show()
        rule.runOnUiThread { nav.open(Trip("dmrc:Rajiv Chowk", "dmrc:Noida Sector 18")) }
        rule.onNode(hasContentDescription("Save trip")).performClick()
        rule.onNodeWithText("Saved · find it in the Saved tab").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(3500)
        rule.onNodeWithText("Saved · find it in the Saved tab").assertDoesNotExist()

        // Unsave and save again: said once only.
        rule.onNode(hasContentDescription("Remove saved trip")).performClick()
        rule.onNode(hasContentDescription("Save trip")).performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Saved · find it in the Saved tab").assertDoesNotExist()
    }

    @Test fun unsavingHidesTheNote() {
        store.finishTour()
        show()
        rule.runOnUiThread { nav.open(Trip("dmrc:Rajiv Chowk", "dmrc:Noida Sector 18")) }
        rule.onNode(hasContentDescription("Save trip")).performClick()
        rule.onNode(hasContentDescription("Remove saved trip")).performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Saved · find it in the Saved tab").assertDoesNotExist()
    }

    @Test fun tourCanBeReplayedFromSaved() {
        store.finishTour()
        show()
        rule.runOnUiThread { nav.tab = Tab.Saved }
        rule.onNodeWithText("Show the tour again").performClick()
        rule.onNodeWithText("Two stations. One route.").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Show the tour again").assertIsDisplayed()
        assertEquals(Tour.Off, store.tour)
    }
}
