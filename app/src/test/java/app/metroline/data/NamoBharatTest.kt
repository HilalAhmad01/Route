package app.metroline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Namo Bharat as the official app lists it (read from the live app on 2026-11-07): 22 stations on the main line
 * (Sarai Kale Khan Z00, A01-A21) plus the Duhai Depot spur (AS2). The minutes below are what the app showed for the
 * same trips, so these tests fail if the line's data drifts away from it.
 */
class NamoBharatTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val router = Router(net)
    private fun trip(a: String, b: String) =
        requireNotNull(router.route(net.station("rrts:$a"), net.station("rrts:$b"), RouteMode.Fastest, allowAirport = false))

    private val added = listOf("Partapur", "Rithani", "Brahampuri", "Meerut Central", "Bhaisali", "MES Colony", "Daurli",
        "Meerut North", "Duhai Depot")

    @Test fun theAppsTwentyThreeStationsAreAllThere() {
        assertEquals(23, net.stations.count { it.system == "rrts" })
        for (n in added) assertNotNull(n, net.stations.firstOrNull { it.id == "rrts:$n" })
    }

    @Test fun wholeLineMatchesTheAppsTotal() {
        val j = trip("Sarai Kale Khan", "Modipuram")
        assertEquals(22, j.stationCount)
        assertEquals(0, j.changes)
        assertEquals(listOf("rrts"), j.legs.map { it.line.id })
        assertTrue("app says 1 h 4 m, got ${j.minutes}", j.minutes in 63..65)
    }

    @Test fun spansMatchWhatTheAppShowed() {
        val cases = listOf(  // from, to, minutes in the app, stations in the app
            Triple("Sarai Kale Khan", "Meerut South", 45) to 11,
            Triple("Modipuram", "Meerut South", 18) to 12,
            Triple("Duhai", "Anand Vihar", 16) to 5,
            Triple("Meerut North", "Partapur", 15) to 10,
            Triple("Anand Vihar", "Sahibabad", 5) to 2,
            Triple("Guldhar", "Ghaziabad", 3) to 2,
            Triple("New Ashok Nagar", "Sarai Kale Khan", 6) to 2,
        )
        for ((t, stations) in cases) {
            val j = trip(t.first, t.second)
            assertEquals("${t.first} -> ${t.second}", stations, j.stationCount)
            assertTrue("${t.first} -> ${t.second}: app ${t.third}, got ${j.minutes}", j.minutes in (t.third - 1)..(t.third + 1))
        }
    }

    @Test fun duhaiDepotIsASpurOffDuhai() {
        val j = trip("Duhai Depot", "Duhai")
        assertEquals(2, j.stationCount)
        assertTrue("app says 4 m, got ${j.minutes}", j.minutes in 3..5)
        assertNotNull(router.route(net.station("rrts:Duhai Depot"), net.station("rrts:Meerut South"), RouteMode.Fastest, allowAirport = false))
    }

    @Test fun oldSpellingsStillFindTheNewStations() {
        fun top(q: String) = StationSearch.search(net, q).first().station.name
        assertEquals("Bhaisali", top("bhainsali"))
        assertEquals("Brahampuri", top("brahmapuri"))
        assertEquals("Meerut Central", top("meerut central"))
        assertEquals("Duhai Depot", top("duhai depot"))
    }

    @Test fun addedStationsLieBetweenTheirNeighboursOnTheLine() {
        val order = listOf("Meerut South", "Partapur", "Rithani", "Shatabdi Nagar", "Brahampuri", "Meerut Central", "Bhaisali",
            "Begumpul", "MES Colony", "Daurli", "Meerut North", "Modipuram")
        val lats = order.map { net.station("rrts:$it").lat }
        assertEquals(lats.sorted(), lats)  // the line runs north, so latitude only increases
    }
}
