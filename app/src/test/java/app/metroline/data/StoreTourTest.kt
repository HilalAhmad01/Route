package app.metroline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreTourTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    private fun store() = Store(context, net)

    @Test fun newInstallSeesTheTour() {
        assertEquals(Tour.FirstRun, store().tour)
    }

    // Closing the app halfway through the tour brings it back, even though app shortcuts get published meanwhile.
    @Test fun tourComesBackUntilFinished() {
        store()
        context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE).edit().putString("published", "v1|").commit()
        val again = store()
        assertEquals(Tour.FirstRun, again.tour)

        again.finishTour()
        assertEquals(Tour.Off, again.tour)
        assertEquals(Tour.Off, store().tour)
    }

    @Test fun peopleWhoUsedTheAppBeforeSkipIt() {
        for (key in listOf("recent", "saved", "places")) {
            prefs.edit().clear().putString(key, "").commit()
            assertEquals(key, Tour.Off, store().tour)
        }
        prefs.edit().clear().putBoolean("smart_card", false).commit()
        assertEquals(Tour.Off, store().tour)
    }

    // Someone who only opened 1.2 has nothing saved, but launching it published (empty) app shortcuts.
    @Test fun anEarlierLaunchCountsAsUsedBefore() {
        context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE).edit().putString("published", "v1|").commit()
        assertEquals(Tour.Off, store().tour)
    }

    @Test fun replayFromSaved() {
        val s = store()
        s.finishTour()
        s.replayTour()
        assertEquals(Tour.Replay, s.tour)
        s.finishTour()
        assertEquals(Tour.Off, store().tour)
    }

    @Test fun savedTripNoteShowsOnce() {
        assertTrue(store().firstSave())
        assertFalse(store().firstSave())
    }
}
