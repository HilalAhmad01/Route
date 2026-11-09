package app.metroline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "rohini west" to "Dr. Baba Saheb Ambedkar Hospital",
            "rohini east" to "Rohini",
            "ambedkar hospital" to "Dr. Baba Saheb Ambedkar Hospital",
            "uttari pitampura" to "Uttari Pitampura - Prashant Vihar",
            "prashant vihar" to "Uttari Pitampura - Prashant Vihar",
            "north pitampura" to "Uttari Pitampura - Prashant Vihar",
            "badarpur border" to "Badarpur Border",
            "badarpur" to "Badarpur Border",
            "badkal mor" to "Badkal Mor",
            "badhkal mor" to "Badkal Mor",
            "guru teg bahadur nagar" to "Guru Teg Bahadur Nagar",
            "gtb nagar" to "Guru Teg Bahadur Nagar",
            "jafrabad" to "Jafrabad",
            "jaffrabad" to "Jafrabad",
            "barakhamba road" to "Barakhamba Road",
            "barakhambha road" to "Barakhamba Road",
            "major mohit sharma rajendra nagar" to "Major Mohit Sharma Rajendra Nagar",
            "rajendra nagar" to "Major Mohit Sharma Rajendra Nagar",
            "tughlakabad station" to "Tughlakabad Station",
            "tughlakabad" to "Tughlakabad Station",
            "depot station" to "Depot Station",
            "depot" to "Depot Station",
            "brig. hoshiar singh" to "Brig. Hoshiar Singh",
            "brigadier hoshiyar singh" to "Brig. Hoshiar Singh",
            "ghevra metro station" to "Ghevra Metro Station",
            "ghevra" to "Ghevra Metro Station",
            "jln stadium" to "JLN Stadium",
            "jawaharlal nehru stadium" to "JLN Stadium",
            "maharaja surajmal stadium" to "Maharaja Surajmal Stadium",
            "surajmal stadium" to "Maharaja Surajmal Stadium",
        )
        for ((q, name) in cases) assertEquals(q, name, top(q).station.name)
    }

    @Test fun sectorStationsFoundTheWayPeopleTypeThem() {
        val cases = mapOf(
            "Sec 18" to "Noida Sector 18",
            "sector 18" to "Noida Sector 18",
            "Noida 18" to "Noida Sector 18",
            "noida sec 18" to "Noida Sector 18",
            "noida sec-18" to "Noida Sector 18",
            "Dwarka 21" to "Dwarka Sector 21",
            "Dwarka Sec 21" to "Dwarka Sector 21",
            "dwarka 10" to "Dwarka Sector 10",
            "noida 62" to "Noida Sector 62",
            "sec 55" to "Sector 55-56",
            "rohini 18" to "Rohini Sector 18, 19",
        )
        for ((q, name) in cases) assertEquals(q, name, top(q).station.name)
        // Ordinary names are unaffected.
        assertEquals("Dwarka Mor", top("dwarka mor").station.name)
        assertEquals("Noida City Centre", top("noida city").station.name)
    }

    @Test fun moreNicknames() {
        assertEquals("Greater Kailash", top("GK").station.name)
        assertEquals("Cyber City", top("cyber hub").station.name)
        assertEquals("Millennium City Centre Gurugram", top("Gurgaon").station.name)
        assertEquals("Millennium City Centre Gurugram", top("gurga").station.name)
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

            Yellow Line from Adarsh Nagar towards Millennium City Centre Gurugram, Platform No. 1, 11 stops to Rajiv Chowk
            Change to Blue Line towards Noida Electronic City, Platform No. 3 (5 min), 6 stops to Akshardham
            """.trimIndent(),
            text,
        )
    }

    @Test fun shareTextHandlesWalksBetweenOperators() {
        val noida = route("dmrc:Adarsh Nagar", "nmrc:Knowledge Park II")
        val t1 = ShareText.of(noida, Fares.quote(net, noida, FareContext(FareDay.Weekday)))
        assertTrue(t1, "Walk to Noida Sector 51 (10 min), then Aqua Line towards Depot Station, 15 stops to Knowledge Park II" in t1)

        val rapid = route("dmrc:Adarsh Nagar", "rmgl:DLF Phase 3")
        val t2 = ShareText.of(rapid, Fares.quote(net, rapid, FareContext(FareDay.Weekday)))
        assertTrue(t2, "Change to Rapid Metro towards DLF Phase 3, Platform No. 2 (10 min), 5 stops to DLF Phase 3" in t2)
        assertTrue(t2, "1 change" in t2)
    }

    @Test fun platformFollowsWhereTheLegGetsOff() {
        // Both shown by the official app from Yamuna Bank: Vaishali trains leave platform 3, Noida trains platform 1.
        val toVaishali = route("dmrc:Yamuna Bank", "dmrc:Vaishali").legs.single()
        assertEquals("Platform No. 3", toVaishali.platform)
        assertEquals("Vaishali", toVaishali.towards)

        val toNoida = route("dmrc:Yamuna Bank", "dmrc:Noida Electronic City").legs.single()
        assertEquals("Platform No. 1", toNoida.platform)
        assertEquals("Noida Electronic City", toNoida.towards)
    }

    @Test fun legsShowPlatforms() {
        val j = route("dmrc:Adarsh Nagar", "nmrc:Knowledge Park II")
        assertEquals(listOf("Platform No. 1", "Platform No. 3", null), j.legs.map { it.platform })

        // Westbound from Yamuna Bank the official app shows platform 2 only.
        val westFromYamunaBank = route("dmrc:Yamuna Bank", "dmrc:Mandi House")
        assertEquals(listOf(2), westFromYamunaBank.legs.single().platforms)
        assertEquals("Platform No. 2", westFromYamunaBank.legs.single().platform)
    }

    // Checked against the official app: the platform table had these directions swapped or missing.
    @Test fun magentaPlatformsAtJanakpuriWestAndSukhdevVihar() {
        // Botanical Garden is also a direct Blue Line ride from Janakpuri West, so go to a Magenta-only stop on the same side.
        assertEquals("Platform No. 3", route("dmrc:Janakpuri West", "dmrc:Dabri Mor - Janakpuri South").legs.single().platform)
        assertEquals("Platform No. 4", route("dmrc:Janakpuri West", "dmrc:Krishna Park Extension").legs.single().platform)
        assertEquals("Platform No. 1", route("dmrc:Sukhdev Vihar", "dmrc:Botanical Garden").legs.single().platform)
        assertEquals("Platform No. 2", route("dmrc:Sukhdev Vihar", "dmrc:Janakpuri West").legs.single().platform)
    }

    @Test fun welcomeHasBothPinkLinePlatforms() {
        assertEquals("Platform No. 3", route("dmrc:Welcome", "dmrc:Maujpur - Babarpur").legs.single().platform)
        assertEquals("Platform No. 4", route("dmrc:Welcome", "dmrc:East Azad Nagar").legs.single().platform)
    }

    // Shown by the official app. Yamuna Vihar and Majlis Park are reached from Jafrabad via Maujpur on the
    // eastbound platform, although the table also names them for the platform facing the other way round the ring.
    @Test fun pinkLinePlatformFollowsTheDirectionOfTravel() {
        assertEquals("Platform No. 1", route("dmrc:Jafrabad", "dmrc:Maujpur - Babarpur").legs.single().platform)
        assertEquals("Platform No. 1", route("dmrc:Jafrabad", "dmrc:Yamuna Vihar").legs.single().platform)
        assertEquals("Platform No. 1", route("dmrc:Jafrabad", "dmrc:Majlis Park").legs.single().platform)
        assertEquals("Platform No. 4", route("dmrc:Azadpur", "dmrc:Burari").legs.single().platform)
        assertEquals("Platform No. 3", route("dmrc:Maujpur - Babarpur", "dmrc:Shiv Vihar").legs.single().platform)
    }

    // Every platform below is what the official app shows for that trip.
    @Test fun platformsMatchTheOfficialApp() {
        fun platform(a: String, b: String, airport: Boolean = false) =
            requireNotNull(router.route(net.station(a), net.station(b), RouteMode.Fastest, airport)).legs.first().platform
        assertEquals("Platform No. 1 & 3", platform("dmrc:Dwarka - Kakrola", "dmrc:Rajouri Garden"))
        assertEquals("Platform No. 1", platform("dmrc:Dwarka Sector 21", "dmrc:Janakpuri West"))
        assertEquals("Platform No. 3", platform("dmrc:Dwarka Sector 21", "dmrc:Delhi Aerocity", airport = true))
        assertEquals("Platform No. 1", platform("dmrc:New Delhi", "dmrc:Delhi Aerocity", airport = true))
        assertEquals("Platform No. 2", platform("dmrc:Yamuna Bank", "dmrc:Rajiv Chowk"))
        assertEquals("Platform No. 2", platform("dmrc:Mundka", "dmrc:Nangloi"))
        assertEquals("Platform No. 3", platform("dmrc:Inderlok", "dmrc:Ashok Park Main"))
        assertEquals("Platform No. 3", platform("dmrc:Kirti Nagar", "dmrc:Inderlok"))
        assertEquals("Platform No. 1", platform("dmrc:Jasola Vihar Shaheen Bagh", "dmrc:Kalindi Kunj"))
        assertEquals("Platform No. 1", platform("dmrc:Old Faridabad", "dmrc:Bata Chowk"))
    }

    // Every fare below is what the official app charges. Magenta Line fares don't follow the route distance
    // (Vasant Vihar to Botanical Garden is 20.7 km but costs the 21 to 32 km fare), and Rithala to Haiderpur Badli Mor
    // is cheaper than the older fare table says because the Magenta extension now shortens it.
    @Test fun delhiMetroFaresMatchTheOfficialApp() {
        fun fare(a: String, b: String) =
            Fares.quote(net, route("dmrc:$a", "dmrc:$b"), FareContext(FareDay.Weekday)).tokenTotal
        assertEquals(54, fare("Vasant Vihar", "Botanical Garden"))
        assertEquals(54, fare("Chirag Delhi", "IP Extension"))
        assertEquals(54, fare("Dashrath Puri", "Nehru Place"))
        assertEquals(64, fare("Jasola Apollo", "Dwarka Mor"))
        assertEquals(64, fare("Dwarka Sector 10", "Malviya Nagar"))
        assertEquals(64, fare("Mewala Maharajpur", "Guru Dronacharya"))
        assertEquals(32, fare("Rithala", "Haiderpur Badli Mor"))
    }

    // The official app treats Ghaziabad (Namo Bharat) and Shaheed Sthal (New Bus Adda) as one station.
    // Namo Bharat has its own ticket and no fare here, so a Delhi Metro trip is never sent over it, even where it is quicker.
    @Test fun delhiMetroTripsStayOffNamoBharat() {
        for ((a, b) in listOf("dmrc:AIIMS" to "dmrc:Shaheed Sthal", "dmrc:Anand Vihar" to "dmrc:Shaheed Sthal", "dmrc:Akshardham" to "dmrc:Arthala")) {
            val options = router.options(net.station(a), net.station(b), allowAirport = false)
            assertTrue("$a -> $b", options.isNotEmpty())
            for ((_, j) in options) assertTrue("$a -> $b uses Namo Bharat", j.legs.none { it.line.system == "rrts" })
        }
        // A trip that starts or ends on Namo Bharat still uses it, through the Ghaziabad link.
        val toDelhi = router.options(net.station("rrts:Sahibabad"), net.station("dmrc:Dilshad Garden"), allowAirport = false)
        assertEquals(listOf("rrts", "red"), toDelhi.getValue(RouteMode.Fastest).legs.map { it.line.id })
    }

    @Test fun ghaziabadConnectsToTheRedLine() {
        val j = route("rrts:Sahibabad", "dmrc:Dilshad Garden")
        assertEquals(listOf("rrts", "red"), j.legs.map { it.line.id })
        assertEquals("Ghaziabad", j.legs[0].to.name)
        assertEquals("Shaheed Sthal", j.legs[1].from.name)
        assertTrue(j.legs[1].boardedByWalk)
        assertEquals(listOf("rrts", "red"), route("dmrc:Dilshad Garden", "rrts:Murad Nagar").legs.map { it.line.id }.reversed())
    }

    // Shown by the official app. The leg starts on the Kirti Nagar branch and carries on to the main line.
    @Test fun kirtiNagarGreenLinePlatform() {
        assertEquals("Platform No. 3", route("dmrc:Kirti Nagar", "dmrc:Brig. Hoshiar Singh").legs.single().platform)
        // Every Green Line train leaves Kirti Nagar the same way, so the platform is the same for stops along the way.
        assertEquals("Platform No. 3", route("dmrc:Kirti Nagar", "dmrc:Nangloi").legs.single().platform)
        assertEquals("Platform No. 3", route("dmrc:Kirti Nagar", "dmrc:Mundka").legs.single().platform)
    }
}
