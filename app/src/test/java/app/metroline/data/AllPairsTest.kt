package app.metroline.data

import org.junit.Assert.assertTrue
import org.junit.Test

class AllPairsTest {
    private val net = Network.parse(javaClass.classLoader!!.getResource("network.json")!!.readText())
    private val router = Router(net)

    @Test fun everyTripIsSane() {
        val problems = mutableListOf<String>()
        fun bad(a: Station, b: Station, what: String) {
            if (problems.size < 60) problems += "${a.id} -> ${b.id}: $what"
        }
        val walkPairs = net.walks.flatMap { listOf(it.a to it.b, it.b to it.a) }.toSet()
        var trips = 0
        for (a in net.stations) for (b in net.stations) {
            if (a == b) continue
            val air = router.route(a, b, RouteMode.Fastest, true)
            val needsAir = router.route(a, b, RouteMode.Fastest, false) == null
            val fast = router.route(a, b, RouteMode.Fastest, needsAir)
            val few = router.route(a, b, RouteMode.FewestChanges, needsAir)
            if (fast == null || few == null || air == null) {
                bad(a, b, "no route"); continue
            }
            trips++
            for ((label, j) in listOf("fast" to fast, "few" to few, "air" to air)) {
                if (j.legs.isEmpty() && (j.from.index to j.to.index) !in walkPairs) { bad(a, b, "$label: no legs"); continue }
                if (j.from != a || j.to != b) bad(a, b, "$label: ends ${j.from.id} -> ${j.to.id}")
                if (j.minutes !in 1..400) bad(a, b, "$label: ${j.minutes} min")
                if (j.stationCount < 2) bad(a, b, "$label: ${j.stationCount} stations")
                if (label != "air" && !needsAir && j.usesAirportExpress) bad(a, b, "$label: uses Airport Express")
                j.legs.forEachIndexed { k, leg ->
                    if (leg.stations.size < 2) bad(a, b, "$label leg $k: ${leg.stations.size} stations")
                    if (leg.stations.zipWithNext().any { (x, y) -> x == y }) bad(a, b, "$label leg $k: repeated station")
                    if (leg.towards == leg.from.name) bad(a, b, "$label leg $k: towards its own start ${leg.towards}")
                    if (leg.towards.isNotEmpty() && leg.stations.drop(1).none { it.name == leg.towards } &&
                        net.patterns.none { p -> p.line == leg.line && p.towards == leg.towards }
                    ) bad(a, b, "$label leg $k: towards ${leg.towards} is not on the line")
                    if (leg.minutes <= 0) bad(a, b, "$label leg $k: ${leg.minutes} min")
                    if (k > 0) {
                        val prev = j.legs[k - 1].to
                        val joined = if (leg.boardedByWalk) (prev.index to leg.from.index) in walkPairs else prev == leg.from
                        if (!joined) bad(a, b, "$label leg $k: starts at ${leg.from.id} after ${prev.id}")
                        if (!leg.boardedByWalk && prev.lines.none { it in leg.from.lines }) bad(a, b, "$label leg $k: odd change")
                        // Same-line changes happen only where the app makes you change between a line's services:
                        // Green Line <-> its Inderlok branch at Ashok Park Main, Noida <-> Vaishali trains at Yamuna Bank,
                        // the Pink Line loop <-> its Shiv Vihar side at Maujpur - Babarpur.
                        val greenBranch = (leg.line.id == "green" && leg.from.name == "Ashok Park Main") ||
                            (leg.line.id == "blue" && leg.from.name == "Yamuna Bank") ||
                            (leg.line.id == "pink" && leg.from.name == "Maujpur - Babarpur")
                        if (leg.line == j.legs[k - 1].line && !leg.boardedByWalk && !greenBranch) bad(a, b, "$label leg $k: change to same line")
                    }
                }
                try {
                    for (ctx in listOf(FareContext(FareDay.Weekday), FareContext(FareDay.Sunday, true, true))) {
                        val q = Fares.quote(net, j, ctx)
                        if (j.legs.any { it.line.system == "dmrc" } && q.headline() == "—") bad(a, b, "$label: no fare")
                        q.note()
                    }
                    ShareText.of(j, Fares.quote(net, j, FareContext(FareDay.Weekday)))
                } catch (e: Exception) {
                    bad(a, b, "$label: ${e::class.simpleName} ${e.message}")
                }
            }
            if (few.changes > fast.changes) bad(a, b, "fewer-changes has more changes")
            if (fast.minutes > few.minutes) bad(a, b, "fastest is slower than fewer-changes")
            if (air.minutes > fast.minutes) bad(a, b, "allowing Airport Express made it slower")
        }
        println("checked $trips trips")
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
