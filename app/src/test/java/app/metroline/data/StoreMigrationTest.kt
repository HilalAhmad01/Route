package app.metroline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreMigrationTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    // An update that renames a station must not lose the trips and bookmarks saved with the old name.
    @Test fun savedItemsSurviveRenamedStations() {
        prefs.edit()
            .putString("saved", "dmrc:Rohini West>dmrc:Rajiv Chowk\ndmrc:Rajiv Chowk>dmrc:North Pitampura - Prashant Vihar")
            .putString("recent", "dmrc:Rohini East>dmrc:Rohini West")
            .putString("places", "Home\tdmrc:Rohini East\nWork\tdmrc:Rajiv Chowk")
            .commit()

        val store = Store(context, net)

        assertEquals(
            listOf(
                Trip("dmrc:Dr. Baba Saheb Ambedkar Hospital", "dmrc:Rajiv Chowk"),
                Trip("dmrc:Rajiv Chowk", "dmrc:Uttari Pitampura - Prashant Vihar"),
            ),
            store.saved.toList(),
        )
        assertEquals(listOf(Trip("dmrc:Rohini", "dmrc:Dr. Baba Saheb Ambedkar Hospital")), store.recent.toList())
        assertEquals(
            listOf(Place("Home", "dmrc:Rohini"), Place("Work", "dmrc:Rajiv Chowk")),
            store.places.toList(),
        )

        // The new ids are written back, so the next launch reads them as they are.
        assertEquals("Home\tdmrc:Rohini\nWork\tdmrc:Rajiv Chowk", prefs.getString("places", null))
        assertEquals("dmrc:Rohini>dmrc:Dr. Baba Saheb Ambedkar Hospital", prefs.getString("recent", null))
    }

    // Stations renamed to DMRC's own names keep their saved trips and bookmarks too.
    @Test fun stationsRenamedToDmrcNamesSurvive() {
        prefs.edit()
            .putString("saved", "dmrc:Jaffrabad>dmrc:GTB Nagar\nnmrc:Depot>dmrc:Rajiv Chowk")
            .putString("places", "Home\tdmrc:Badarpur")
            .commit()

        val store = Store(context, net)

        assertEquals(
            listOf(Trip("dmrc:Jafrabad", "dmrc:Guru Teg Bahadur Nagar"), Trip("nmrc:Depot Station", "dmrc:Rajiv Chowk")),
            store.saved.toList(),
        )
        assertEquals(listOf(Place("Home", "dmrc:Badarpur Border")), store.places.toList())
    }

    @Test fun theLastFourDmrcNameRenamesSurviveToo() {
        prefs.edit()
            .putString("saved", "dmrc:Brigadier Hoshiyar Singh>dmrc:Ghevra\ndmrc:Jawaharlal Nehru Stadium>dmrc:Rajiv Chowk")
            .putString("places", "Work\tdmrc:Surajmal Stadium")
            .commit()

        val store = Store(context, net)

        assertEquals(
            listOf(Trip("dmrc:Brig. Hoshiar Singh", "dmrc:Ghevra Metro Station"), Trip("dmrc:JLN Stadium", "dmrc:Rajiv Chowk")),
            store.saved.toList(),
        )
        assertEquals(listOf(Place("Work", "dmrc:Maharaja Surajmal Stadium")), store.places.toList())
    }

    @Test fun unknownStationsAreStillDropped() {
        prefs.edit()
            .putString("saved", "dmrc:Nowhere>dmrc:Rajiv Chowk\ndmrc:Rajiv Chowk>dmrc:Kashmere Gate")
            .putString("places", "Gone\tdmrc:Nowhere")
            .commit()

        val store = Store(context, net)

        assertEquals(listOf(Trip("dmrc:Rajiv Chowk", "dmrc:Kashmere Gate")), store.saved.toList())
        assertEquals(emptyList<Place>(), store.places.toList())
    }
}
