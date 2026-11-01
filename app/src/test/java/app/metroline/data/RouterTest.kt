package app.metroline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected values are taken from the official DMRC app (captured 1 Nov 2026, a Sunday). */
class RouterTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val router = Router(net)
    private fun st(id: String) = net.station(id)
    private fun fastest(a: String, b: String) =
        requireNotNull(router.route(st(a), st(b), RouteMode.Fastest, allowAirport = false))

    @Test fun adarshNagarToAkshardham() {
        val j = fastest("dmrc:Adarsh Nagar", "dmrc:Akshardham")
        assertEquals(18, j.stationCount)
        assertEquals(1, j.changes)
        assertEquals("Rajiv Chowk", j.legs[0].to.name)
        assertEquals("Millennium City Centre Gurugram", j.legs[0].towards)
        assertEquals("Noida Electronic City", j.legs[1].towards)
        assertTrue("minutes ${j.minutes}", j.minutes in 39..43) // official: 41
        val fare = Fares.forJourney(net, j, FareDay.Sunday)
        assertEquals(listOf(32), fare.map { it.amount })
    }

    @Test fun adarshNagarToKnowledgePark2() {
        val j = fastest("dmrc:Adarsh Nagar", "nmrc:Knowledge Park II")
        assertEquals(listOf("yellow", "blue", "aqua"), j.legs.map { it.line.id })
        assertEquals("Noida Sector 52", j.legs[1].to.name)
        assertEquals("Noida Sector 51", j.legs[2].from.name)
        assertTrue(j.legs[2].boardedByWalk)
        // Official: 29 stations on the DMRC part.
        assertEquals(29, j.legs[0].stations.size + j.legs[1].stations.size - 1)
        // Official: Sector 51 .. Knowledge Park II is 16 stations.
        assertEquals(16, j.legs[2].stations.size)
    }

    @Test fun adarshNagarToPhase3ViaRapidMetro() {
        val j = fastest("dmrc:Adarsh Nagar", "rmgl:DLF Phase 3")
        assertEquals(35, j.stationCount - 1) // official counts Sikanderpur once: 35
        assertEquals(listOf("yellow", "rapid"), j.legs.map { it.line.id })
        assertEquals(listOf("Sikanderpur", "DLF Phase 2", "Belvedere Towers", "Cyber City", "Moulsari Avenue", "DLF Phase 3"),
            j.legs[1].stations.map { it.name })
        assertTrue("minutes ${j.minutes}", j.minutes in 84..92) // official: 88
        val fare = Fares.forJourney(net, j, FareDay.Sunday)
        assertEquals(74, fare.sumOf { it.amount!! })
    }

    @Test fun everyStationReachableFromRajivChowk() {
        val from = st("dmrc:Rajiv Chowk")
        val missing = net.stations.filter { it != from && router.route(from, it, RouteMode.Fastest, true) == null }
        assertEquals(emptyList<Station>(), missing)
    }

    @Test fun fewestChangesNeverHasMoreChanges() {
        val ids = listOf("dmrc:Rithala", "dmrc:Noida Electronic City", "dmrc:Botanical Garden", "dmrc:Shiv Vihar",
            "nmrc:Depot", "rmgl:Sector 55-56", "dmrc:Dhansa Bus Stand", "dmrc:Raja Nahar Singh", "rrts:Modipuram")
        for (a in ids) for (b in ids) if (a != b) {
            val f = router.route(st(a), st(b), RouteMode.Fastest, false)!!
            val c = router.route(st(a), st(b), RouteMode.FewestChanges, false)!!
            assertTrue("$a -> $b", c.changes <= f.changes)
            assertTrue("$a -> $b", f.minutes <= c.minutes)
        }
    }
}
