package app.metroline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Values read from the official DMRC app on 2026-11-07 (see dist/phone_results.txt in the project handoff).
 * Each test pins one thing the app showed, so a rebuild of network.json that loses it fails here.
 */
class AppCheckedDataTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val router = Router(net)
    private fun route(a: String, b: String, airport: Boolean = false) =
        requireNotNull(router.route(net.station(a), net.station(b), RouteMode.Fastest, allowAirport = airport)) { "$a -> $b" }

    private fun weekdayFare(a: String, b: String) =
        Fares.forJourney(net, route(a, b), FareContext(FareDay.Weekday)).mapNotNull { it.amount }.sum()

    @Test fun majlisParkHopsCostTwentyOneNotEleven() {
        val hops = listOf("Burari" to "Majlis Park", "Majlis Park" to "Bhalaswa", "Bhalaswa" to "Haiderpur Badli Mor")
        for ((a, b) in hops) {
            assertEquals("$a -> $b", 21, weekdayFare("dmrc:$a", "dmrc:$b"))
            assertEquals("$b -> $a", 21, weekdayFare("dmrc:$b", "dmrc:$a"))
        }
        assertEquals(21, weekdayFare("dmrc:Bhalaswa", "dmrc:Majlis Park"))
    }

    @Test fun otherShortExtensionHopsStayAtEleven() {  // these matched the app at Rs 11 (Burari - Jharoda Majra, Khajuri Khas - Nanaksar...)
        assertEquals(11, weekdayFare("dmrc:Burari", "dmrc:Jharoda Majra"))
        assertEquals(11, weekdayFare("dmrc:Khajuri Khas", "dmrc:Nanaksar - Sonia Vihar"))
        assertEquals(11, weekdayFare("dmrc:Uttari Pitampura - Prashant Vihar", "dmrc:Madhuban Chowk"))
    }

    @Test fun satguruRamsinghMargHasItsPlatformTowardsAshokParkMain() {
        assertEquals(listOf(1), route("dmrc:Satguru Ramsingh Marg", "dmrc:Ashok Park Main").legs.single().platforms)
        assertEquals(listOf(2), route("dmrc:Satguru Ramsingh Marg", "dmrc:Kirti Nagar").legs.single().platforms)
    }

    @Test fun dwarkaSector21AirportPlatformTowardsYashobhoomi() {
        val leg = route("dmrc:Dwarka Sector 21", "dmrc:Yashobhoomi Dwarka Sector 25", airport = true).legs.single()
        assertEquals("airport", leg.line.id)
        assertEquals(listOf(2), leg.platforms)
    }

    @Test fun longerTripsThroughMajlisParkCostWhatTheAppCharges() {
        // checked on the phone 2026-11-07: the app priced these a slab above the distance fare
        assertEquals(43, weekdayFare("dmrc:Ashok Park Main", "dmrc:Bhalaswa"))
        assertEquals(43, weekdayFare("dmrc:Burari", "dmrc:Ashok Park Main"))
        assertEquals(54, weekdayFare("dmrc:Bhalaswa", "dmrc:Jor Bagh"))
        assertEquals(54, weekdayFare("dmrc:Krishna Park Extension", "dmrc:Burari"))
        // and these matched without any override
        assertEquals(32, weekdayFare("dmrc:Khajuri Khas", "dmrc:Majlis Park"))
        assertEquals(32, weekdayFare("dmrc:Jharoda Majra", "dmrc:Madhuban Chowk"))
        assertEquals(43, weekdayFare("dmrc:Janpath", "dmrc:Jharoda Majra"))
    }

    @Test fun interchangeMinutesAreTheAppsFiveOrTen() {
        for (n in listOf("Azadpur", "Hauz Khas", "Rajouri Garden", "Netaji Subhash Place", "Kashmere Gate", "Janakpuri West",
            "Kalkaji Mandir", "Dilli Haat - INA")) assertEquals(n, 10, net.interchangeMinutes(net.station("dmrc:$n")))
        for (n in listOf("Welcome", "Lajpat Nagar", "Punjabi Bagh West", "Mandi House", "Central Secretariat", "Kirti Nagar",
            "Anand Vihar", "Dwarka Sector 21")) assertEquals(n, 5, net.interchangeMinutes(net.station("dmrc:$n")))
    }

    @Test fun interchangeMinutesMatchTheApp() {
        assertEquals(5, net.interchangeMinutes(net.station("dmrc:Ashok Park Main")))
        assertEquals(5, net.interchangeMinutes(net.station("dmrc:Inderlok")))
    }

    @Test fun greenLineBranchToInderlokNeedsAChangeAtAshokParkMain() {
        // Checked in the app 2026-11-07: Punjabi Bagh / Kirti Nagar / Satguru / Mundka -> Inderlok are 10 / 14 / 12 / 31 min
        // with 1 change at Ashok Park Main (5 min), and the same going the other way.
        for ((a, app) in listOf("Punjabi Bagh" to 10, "Kirti Nagar" to 14, "Satguru Ramsingh Marg" to 12, "Mundka" to 31)) {
            for (j in listOf(route("dmrc:$a", "dmrc:Inderlok"), route("dmrc:Inderlok", "dmrc:$a"))) {
                assertEquals(a, 1, j.changes)
                assertEquals(a, "Ashok Park Main", j.legs[1].from.name)
                assertEquals(a, 5, j.legs[1].changeMinutes)
                assertTrue("$a: app $app min, got ${j.minutes}", j.minutes in (app - 4)..(app + 1))
            }
        }
        assertEquals(2, route("dmrc:Punjabi Bagh", "dmrc:Kanhaiya Nagar").changes)
    }

    @Test fun noidaToVaishaliSideChangesAtYamunaBank() {
        // the app and the DMRC website, 2026-11-07: 17 + 5 (change) + 3 min, Platform 2 then Platform 3 towards Vaishali
        val j = route("dmrc:Noida Sector 18", "dmrc:Laxmi Nagar")
        assertEquals(1, j.changes)
        assertEquals("Yamuna Bank", j.legs[1].from.name)
        assertEquals(5, j.legs[1].changeMinutes)
        assertEquals(listOf(2), j.legs[0].platforms)
        assertEquals(listOf(3), j.legs[1].platforms)
        assertTrue("app 24, website 25, got ${j.minutes}", j.minutes in 21..26)
        // Vaishali trains run from Dwarka, so the Delhi side rides straight through
        assertEquals(0, route("dmrc:Indraprastha", "dmrc:Laxmi Nagar").changes)
        assertEquals(0, route("dmrc:Rajiv Chowk", "dmrc:Vaishali").changes)
        assertEquals(0, route("dmrc:Rajiv Chowk", "dmrc:Noida Electronic City").changes)
    }

    @Test fun shivViharSideOfThePinkLineChangesAtMaujpur() {
        // DMRC website 2026-11-07: Jafrabad -> Gokulpuri is 2 min (Platform 1) + change + 2 min (Platform 3 towards Shiv
        // Vihar); Gokulpuri -> Yamuna Vihar changes there too; Jafrabad -> Yamuna Vihar rides straight through.
        val j = route("dmrc:Jafrabad", "dmrc:Gokulpuri")
        assertEquals(1, j.changes)
        assertEquals("Maujpur - Babarpur", j.legs[1].from.name)
        assertEquals(5, j.legs[1].changeMinutes)
        assertEquals(listOf(1), j.legs[0].platforms)
        assertEquals(listOf(3), j.legs[1].platforms)
        assertEquals(1, route("dmrc:Gokulpuri", "dmrc:Yamuna Vihar").changes)
        assertEquals(0, route("dmrc:Jafrabad", "dmrc:Yamuna Vihar").changes)
        assertEquals(0, route("dmrc:Gokulpuri", "dmrc:Shiv Vihar").changes)
    }

    @Test fun rapidMetroTimesAndFaresMatchTheApp() {
        // 11 app trips, 2026-11-07: (from, to, app minutes, app fare). The Sector 55-56 line runs at 38 km/h, the loop 25.
        val trips = listOf(
            Triple("Sikanderpur", "Sector 55-56", 11 to 20), Triple("DLF Phase 1", "Sector 54 Chowk", 7 to 20),
            Triple("Sector 55-56", "Sikanderpur", 11 to 20), Triple("Sikanderpur", "Moulsari Avenue", 7 to 20),
            Triple("Cyber City", "Sikanderpur", 8 to 20), Triple("Belvedere Towers", "DLF Phase 3", 6 to 20),
            Triple("DLF Phase 2", "Cyber City", 4 to 20), Triple("Moulsari Avenue", "Sector 55-56", 17 to 35),
            Triple("Sector 53-54", "DLF Phase 2", 9 to 20), Triple("Sector 55-56", "Cyber City", 16 to 35),
        )
        for ((a, b, app) in trips) {
            val j = route("rmgl:$a", "rmgl:$b")
            assertEquals("$a -> $b", 0, j.changes)
            assertTrue("$a -> $b: app ${app.first} min, got ${j.minutes}", j.minutes in (app.first - 1)..(app.first + 1))
            assertEquals("$a -> $b", app.second, weekdayFare("rmgl:$a", "rmgl:$b"))
        }
    }

    @Test fun rapidMetroFareIsThirtyFiveOnlyBetweenTheFarEnds() {
        // all 18 app fares from 2026-11-07, including the 8 above
        val app = mapOf(
            ("Sector 42-43" to "Sector 53-54") to 20, ("DLF Phase 1" to "Sector 54 Chowk") to 20,
            ("Sector 53-54" to "DLF Phase 2") to 20, ("Sector 42-43" to "Moulsari Avenue") to 20,
            ("Sikanderpur" to "Sector 55-56") to 20, ("Sector 55-56" to "DLF Phase 2") to 20,
            ("Sector 42-43" to "Belvedere Towers") to 20, ("Cyber City" to "Sikanderpur") to 20,
            ("Sector 53-54" to "Cyber City") to 35, ("Sector 53-54" to "Moulsari Avenue") to 35,
            ("Sector 54 Chowk" to "Belvedere Towers") to 35, ("Sector 55-56" to "Belvedere Towers") to 35,
            ("Moulsari Avenue" to "Sector 55-56") to 35, ("Sector 55-56" to "DLF Phase 3") to 35,
        )
        for ((pair, fare) in app) assertEquals("${pair.first} -> ${pair.second}", fare, weekdayFare("rmgl:${pair.first}", "rmgl:${pair.second}"))
    }

    @Test fun throughRunningGreenLineNeedsNoChange() {
        assertEquals(0, route("dmrc:Brig. Hoshiar Singh", "dmrc:Kirti Nagar").changes)
        assertEquals(0, route("dmrc:Kirti Nagar", "dmrc:Punjabi Bagh").changes)
        assertEquals(0, route("dmrc:Ashok Park Main", "dmrc:Punjabi Bagh").changes)
        assertEquals(listOf(3), route("dmrc:Kirti Nagar", "dmrc:Punjabi Bagh").legs.single().platforms)
        assertEquals(listOf(2), route("dmrc:Ashok Park Main", "dmrc:Inderlok").legs.single().platforms)
        assertEquals(listOf(3), route("dmrc:Inderlok", "dmrc:Ashok Park Main").legs.single().platforms)
    }

    @Test fun dhaulaKuanFootbridgeLinkIsTwentyFiveMinutes() {
        val walk = net.walks.single { it.minutes == 25 }
        assertEquals(setOf("Dhaula Kuan", "Durgabai Deshmukh South Campus"),
            setOf(net.stations[walk.a].name, net.stations[walk.b].name))
        // the app showed 34 min for Delhi Aerocity -> Moti Bagh: 6 + 5 + 15 + 5 + 2 (rounded)
        val j = route("dmrc:Delhi Aerocity", "dmrc:Sir M. Vishweshwaraiah Moti Bagh", airport = true)
        assertTrue("app says 34, got ${j.minutes}", j.minutes in 33..35)
        assertNotNull(j.legs.firstOrNull { it.line.id == "airport" })
    }
}
