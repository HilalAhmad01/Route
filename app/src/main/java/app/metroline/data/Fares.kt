package app.metroline.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

enum class FareDay { Weekday, Sunday }

data class FareContext(val day: FareDay, val smartCard: Boolean = false, val offPeak: Boolean = false) {
    companion object {
        fun now(smartCard: Boolean) = at(LocalDateTime.now(ZoneId.of("Asia/Kolkata")), smartCard)

        fun at(time: LocalDateTime, smartCard: Boolean) = FareContext(
            if (time.dayOfWeek == DayOfWeek.SUNDAY) FareDay.Sunday else FareDay.Weekday,
            smartCard,
            isOffPeak(time.toLocalTime()),
        )

        fun isOffPeak(t: LocalTime) =
            t < LocalTime.of(8, 0) || (t >= LocalTime.of(12, 0) && t < LocalTime.of(17, 0)) || t >= LocalTime.of(21, 0)
    }
}

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
    /** Station pairs (smaller index * 4096 + larger) whose DMRC fare isn't the one their distance gives, as a slab. */
    val dmrcOverrides: Map<Int, Int> = emptyMap(),
) {
    companion object {
        fun pairKey(a: Int, b: Int) = minOf(a, b) * 4096 + maxOf(a, b)

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
                (d["overrides"] as? JsonArray).orEmpty().associate {
                    val t = (it as JsonArray).map { n -> n.jsonPrimitive.int }
                    pairKey(t[0], t[1]) to t[2]
                },
            )
        }
    }
}

class FarePart(val system: String, val label: String, val amount: Int?, val verified: Boolean, val paise: Int?)

class FareQuote(val parts: List<FarePart>, val context: FareContext) {
    private val known = parts.filter { it.amount != null }
    val approx = known.any { !it.verified }
    val partial = known.size < parts.size
    val tokenTotal = known.sumOf { it.amount!! }
    val paidPaise = known.sumOf { it.paise!! }
    val smartCardApplied = context.smartCard && known.any { it.paise != it.amount!! * 100 }

    fun headline(): String =
        if (parts.isEmpty()) "₹0" else if (known.isEmpty()) "—" else (if (approx) "≈ " else "") + rupees(paidPaise) + (if (partial) "+" else "")

    fun note(): String? {
        val notes = mutableListOf<String>()
        if (parts.size > 1) notes += known.joinToString(" + ") { "${rupees(it.paise!!)} ${it.label}" }
        parts.filter { it.amount == null }.forEach { notes += "${it.label} fare not included" }
        if (smartCardApplied) {
            notes += (if (context.offPeak) "Smart card, off-peak" else "Smart card") + " · token ₹$tokenTotal"
        }
        if (context.day == FareDay.Sunday && parts.any { it.system == "dmrc" }) notes += "Sunday fare"
        return notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    companion object {
        fun rupees(paise: Int) =
            if (paise % 100 == 0) "₹${paise / 100}" else "₹%d.%02d".format(Locale.ROOT, paise / 100, paise % 100)
    }
}

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

    /** DMRC's own fare where it departs from the distance slab (Magenta Line pairs), otherwise the distance fare. */
    fun dmrc(net: Network, from: Station, to: Station, day: FareDay): Int {
        val slab = net.fares.dmrcOverrides[FareRules.pairKey(from.index, to.index)]
            ?: return dmrc(net.fares, shortestKm(net, from, to), day)
        return (if (day == FareDay.Sunday) net.fares.dmrcSunday else net.fares.dmrcWeekday)[slab]
    }

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

    fun quote(net: Network, journey: Journey, context: FareContext) = FareQuote(forJourney(net, journey, context), context)

    fun forJourney(net: Network, journey: Journey, context: FareContext): List<FarePart> {
        val day = context.day
        val parts = mutableListOf<FarePart>()
        var i = 0
        val legs = journey.legs
        while (i < legs.size) {
            val system = legs[i].line.system
            var j = i
            while (j + 1 < legs.size && legs[j + 1].line.system == system) j++
            val from = legs[i].from
            val to = legs[j].to
            val amount: Int? = when (system) {
                "dmrc" -> dmrc(net, from, to, day)
                "rmgl" -> rapid(net.fares, from, to)
                "nmrc" -> aqua(net.fares, (i..j).sumOf { legs[it].hops })
                else -> null
            }
            val verified = net.fares.verified[system] == true ||
                (system == "rmgl" && amount == net.fares.rapidWithin)
            val percent = if (context.smartCard && system == "dmrc") (if (context.offPeak) 80 else 90) else 100
            parts += FarePart(system, labels.getValue(system), amount, verified, amount?.let { it * percent })
            i = j + 1
        }
        return parts
    }

    fun shortestKm(net: Network, from: Station, to: Station): Double {
        val dist = DoubleArray(net.stations.size) { Double.MAX_VALUE }
        val adj = net.dmrcTrack
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
