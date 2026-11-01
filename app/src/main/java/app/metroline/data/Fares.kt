package app.metroline.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Which fare table applies on a given day. DMRC charges less on Sundays and national holidays. */
enum class FareDay { Weekday, Sunday }

class FareRules(
    val dmrcSlabsKm: List<Double>,
    val dmrcWeekday: List<Int>,
    val dmrcSunday: List<Int>,
    val rapidWithin: Int,
    val rapidAcross: Int,
    val rapidPhase1: Set<String>,
    val aquaStations: List<Int>,
    val aquaFares: List<Int>,
    val verified: Map<String, Boolean>,
) {
    companion object {
        fun parse(f: Map<String, JsonElement?>): FareRules {
            val d = f.getValue("dmrc") as JsonObject
            val r = f.getValue("rmgl") as JsonObject
            val n = f.getValue("nmrc") as JsonObject
            fun ints(e: JsonElement?) = (e as JsonArray).map { it.jsonPrimitive.int }
            return FareRules(
                (d.getValue("slabs_km") as JsonArray).map { it.jsonPrimitive.double },
                ints(d["weekday"]), ints(d["sunday"]),
                r.getValue("within").jsonPrimitive.int, r.getValue("across").jsonPrimitive.int,
                (r.getValue("phase1") as JsonArray).map { it.jsonPrimitive.content }.toSet(),
                ints(n["stations"]), ints(n["fares"]),
                f.mapValues { (_, v) ->
                    v != null && v !is JsonNull && (v as JsonObject)["verified"]?.jsonPrimitive?.boolean == true
                },
            )
        }
    }
}

/** One ticketed part of a journey: everything ridden on one operator between entering and leaving its gates. */
class FarePart(val system: String, val label: String, val amount: Int?, val verified: Boolean)

object Fares {
    private val labels = mapOf(
        "dmrc" to "Delhi Metro", "dmrc_airport" to "Airport Express", "rmgl" to "Rapid Metro",
        "nmrc" to "Noida Metro", "rrts" to "Namo Bharat",
    )

    fun dmrc(rules: FareRules, km: Double, day: FareDay): Int {
        val table = if (day == FareDay.Sunday) rules.dmrcSunday else rules.dmrcWeekday
        val slab = rules.dmrcSlabsKm.indexOfFirst { km <= it }.let { if (it < 0) rules.dmrcSlabsKm.size else it }
        return table[slab]
    }

    /** Rapid Metro: flat fare within phase 1 (the loop) or within phase 2; Sikanderpur belongs to both. */
    fun rapid(rules: FareRules, from: Station, to: Station): Int {
        fun inPhase1(s: Station) = s.name in rules.rapidPhase1
        fun inPhase2(s: Station) = s.name !in rules.rapidPhase1 || s.name == "Sikanderpur"
        val within = (inPhase1(from) && inPhase1(to)) || (inPhase2(from) && inPhase2(to))
        return if (within) rules.rapidWithin else rules.rapidAcross
    }

    fun aqua(rules: FareRules, stationsTravelled: Int): Int {
        val slab = rules.aquaStations.indexOfFirst { stationsTravelled <= it }
            .let { if (it < 0) rules.aquaStations.size else it }
        return rules.aquaFares[slab]
    }

    /**
     * Split a journey into fare parts. DMRC charges by the shortest network distance between
     * the entry and exit station, not the distance actually ridden, so that is computed separately.
     */
    fun forJourney(net: Network, journey: Journey, day: FareDay): List<FarePart> {
        val parts = mutableListOf<FarePart>()
        var i = 0
        val legs = journey.legs
        while (i < legs.size) {
            val system = legs[i].line.system
            var j = i
            // One ticket per operator: interchanges inside the same system don't start a new fare.
            while (j + 1 < legs.size && legs[j + 1].line.system == system) j++
            val from = legs[i].from
            val to = legs[j].to
            val amount: Int? = when (system) {
                "dmrc" -> dmrc(net.fares, shortestKm(net, from, to), day)
                "rmgl" -> rapid(net.fares, from, to)
                "nmrc" -> aqua(net.fares, (i..j).sumOf { legs[it].hops })
                else -> null
            }
            parts += FarePart(system, labels.getValue(system), amount, net.fares.verified[system] == true)
            i = j + 1
        }
        return parts
    }

    /** Shortest track distance between two DMRC stations over the regular (non Airport Express) lines. */
    fun shortestKm(net: Network, from: Station, to: Station): Double {
        val n = net.stations.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val adj = List(n) { mutableListOf<Pair<Int, Double>>() }
        for (p in net.patterns) {
            if (p.line.system != "dmrc") continue
            for (k in 0 until p.stops.size - 1) adj[p.stops[k]] += p.stops[k + 1] to p.km[k]
        }
        val pq = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        dist[from.index] = 0.0
        pq += 0.0 to from.index
        while (pq.isNotEmpty()) {
            val (d, u) = pq.poll()!!
            if (d > dist[u]) continue
            if (u == to.index) return d
            for ((v, w) in adj[u]) if (d + w < dist[v]) {
                dist[v] = d + w
                pq += dist[v] to v
            }
        }
        return dist[to.index]
    }
}
