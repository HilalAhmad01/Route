package app.metroline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class FeaturesTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val router = Router(net)
    private fun route(a: String, b: String) =
        requireNotNull(router.route(net.station(a), net.station(b), RouteMode.Fastest, allowAirport = false))

    private fun top(q: String) = StationSearch.search(net, q).first()

    @Test fun nicknamesFindTheirStation() {
        val cases = mapOf(
            "CP" to "Rajiv Chowk",
            "connaught" to "Rajiv Chowk",
            "HUDA City" to "Millennium City Centre Gurugram",
            "Race Course" to "Lok Kalyan Marg",
            "NSP" to "Netaji Subhash Place",
            "jmi" to "Jamia Millia Islamia",
            "INA" to "Dilli Haat - INA",
            "udyog bhawan" to "Seva Teerth",
            "pragati maidan" to "Supreme Court",
            "T3" to "IGI Airport",
            "nizamuddin" to "Sarai Kale Khan - Nizamuddin",
        )
        for ((q, name) in cases) assertEquals(q, name, top(q).station.name)
    }

    @Test fun nicknameMatchReportsTheAlias() {
        assertEquals("CP", top("cp").alias)
        assertEquals("HUDA City Centre", top("huda").alias)
    }

    @Test fun realNamesStillRankFirst() {
        assertEquals(StationMatch(net.station("dmrc:Rajiv Chowk")), top("rajiv"))
        assertEquals("AIIMS", top("a.i.i.m.s").station.name)
        assertEquals("Mayur Vihar - I", top("mayur vihar i").station.name)
    }

    @Test fun legsKnowTheirBoardingPlatform() {
        val j = route("dmrc:Rajiv Chowk", "dmrc:Pul Bangash")
        assertEquals(listOf("yellow" to "2", "red" to "3"), j.legs.map { it.line.id to it.platform })
        val back = route("dmrc:Pul Bangash", "dmrc:Rajiv Chowk")
        assertTrue(back.legs.all { it.platform != null })
    }

    @Test fun noPlatformAtATerminus() {
        assertNull(route("dmrc:Rithala", "dmrc:Rohini West").legs.single().platform)
    }

    @Test fun offPeakWindows() {
        fun op(h: Int, m: Int) = FareContext.isOffPeak(LocalTime.of(h, m))
        assertTrue(op(7, 59)); assertFalse(op(8, 0))
        assertFalse(op(11, 59)); assertTrue(op(12, 0))
        assertTrue(op(16, 59)); assertFalse(op(17, 0))
        assertFalse(op(20, 59)); assertTrue(op(21, 0))
    }

    @Test fun smartCardFares() {
        val j = route("dmrc:Adarsh Nagar", "dmrc:Akshardham")
        fun q(day: FareDay, card: Boolean, offPeak: Boolean) = Fares.quote(net, j, FareContext(day, card, offPeak))
        assertEquals("₹43", q(FareDay.Weekday, false, false).headline())
        assertEquals("₹38.70", q(FareDay.Weekday, true, false).headline())
        assertEquals("₹34.40", q(FareDay.Weekday, true, true).headline())
        assertEquals("₹28.80", q(FareDay.Sunday, true, false).headline())
        assertEquals("Smart card, off-peak · token ₹43", q(FareDay.Weekday, true, true).note())
        assertEquals("₹43", q(FareDay.Weekday, false, true).headline())
    }

    @Test fun smartCardOnlyDiscountsDelhiMetro() {
        val j = route("dmrc:Adarsh Nagar", "rmgl:DLF Phase 3")
        val quote = Fares.quote(net, j, FareContext(FareDay.Sunday, smartCard = true))
        assertEquals("₹68.60", quote.headline())
        assertEquals(74, quote.tokenTotal)
    }

    @Test fun shareTextReadsLikeDirections() {
        val j = route("dmrc:Adarsh Nagar", "dmrc:Akshardham")
        val text = ShareText.of(j, Fares.quote(net, j, FareContext(FareDay.Weekday)))
        assertEquals(
            """
            Adarsh Nagar → Akshardham
            ${j.minutes} min · ₹43 · 18 stations · 1 change

            Yellow Line from Adarsh Nagar towards Millennium City Centre Gurugram · platform 1, 11 stops to Rajiv Chowk
            Change to Blue Line towards Noida Electronic City · platform 3 (5 min), 6 stops to Akshardham
            """.trimIndent(),
            text,
        )
    }

    @Test fun shareTextHandlesWalksBetweenOperators() {
        val noida = route("dmrc:Adarsh Nagar", "nmrc:Knowledge Park II")
        val t1 = ShareText.of(noida, Fares.quote(net, noida, FareContext(FareDay.Weekday)))
        assertTrue(t1, "Walk to Noida Sector 51 (10 min), then Aqua Line towards Depot, 15 stops to Knowledge Park II" in t1)

        val rapid = route("dmrc:Adarsh Nagar", "rmgl:DLF Phase 3")
        val t2 = ShareText.of(rapid, Fares.quote(net, rapid, FareContext(FareDay.Weekday)))
        assertTrue(t2, "Change to Rapid Metro towards DLF Phase 3 (10 min), 5 stops to DLF Phase 3" in t2)
        assertTrue(t2, "1 change" in t2)
    }
}
