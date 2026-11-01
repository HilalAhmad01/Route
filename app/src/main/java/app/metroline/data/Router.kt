package app.metroline.data

import java.util.PriorityQueue
import kotlin.math.roundToInt

enum class RouteMode { Fastest, FewestChanges }

/** One ride on one line, from boarding to getting off. */
class Leg(
    val line: Line,
    val towards: String,
    val stations: List<Station>,
    val minutes: Double,
    val km: Double,
    /** Minutes spent changing onto this leg (0 for the first leg). */
    val changeMinutes: Int,
    /** True when this leg starts at a different station reached on foot. */
    val boardedByWalk: Boolean,
) {
    val from get() = stations.first()
    val to get() = stations.last()
    val hops get() = stations.size - 1
}

/** A walk to or from a nearby station of another operator, at the very start or end of a trip. */
class EndWalk(val station: Station, val minutes: Int)

class Journey(val legs: List<Leg>, val startWalk: EndWalk? = null, val endWalk: EndWalk? = null) {
    val minutes: Int = (legs.sumOf { it.minutes } + legs.sumOf { it.changeMinutes } +
        (startWalk?.minutes ?: 0) + (endWalk?.minutes ?: 0)).roundToInt()
    val changes: Int = legs.size - 1

    /** Stations passed through, counting both ends and each interchange once (as the official app does). */
    val stationCount: Int = legs.sumOf { it.hops } + 1 + legs.count { it.boardedByWalk }
    val from get() = startWalk?.station ?: legs.first().from
    val to get() = endWalk?.station ?: legs.last().to
    val usesAirportExpress get() = legs.any { it.line.system == "dmrc_airport" }
    fun signature() = legs.joinToString("|") { leg -> leg.line.id + ":" + leg.stations.joinToString(",") { it.id } }
}

/**
 * Dijkstra over "on a train of pattern p at stop i" states. Changing trains costs the
 * station's interchange time; in FewestChanges mode every change also carries a large
 * penalty so the route with the fewest changes wins, with time as the tie-breaker.
 */
class Router(private val net: Network) {
    private val offsets = IntArray(net.patterns.size + 1).also { o ->
        net.patterns.forEachIndexed { i, p -> o[i + 1] = o[i] + p.stops.size }
    }
    private val stateCount = offsets.last()
    private fun state(p: Int, i: Int) = offsets[p] + i
    private fun patternOf(state: Int): Int {
        var lo = 0
        var hi = net.patterns.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (offsets[mid] <= state) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun route(from: Station, to: Station, mode: RouteMode, allowAirport: Boolean): Journey? {
        if (from == to) return null
        val changePenalty = if (mode == RouteMode.FewestChanges) 1000.0 else 0.0
        fun allowed(p: Pattern) = allowAirport || p.line.system != "dmrc_airport"
        fun boardable(p: Pattern, i: Int) = allowed(p) && i < p.stops.size - 1

        // One extra slot is the "arrived" state, reached from a train at the destination
        // or from a train at a station within walking distance of it.
        val finish = stateCount
        val cost = DoubleArray(stateCount + 1) { Double.MAX_VALUE }
        val prev = IntArray(stateCount + 1) { -1 }
        // How we entered a state: 0 = rode from previous stop, 1 = changed at same station, 2 = walked,
        // 3 = stayed on the same line where two patterns meet (through trains, loops, branches).
        val how = IntArray(stateCount + 1)
        val changeMin = IntArray(stateCount + 1)
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })

        fun seed(station: Int, walk: Int) {
            for ((p, i) in net.occurrences[station]) {
                val s = state(p, i)
                if (!boardable(net.patterns[p], i) || walk >= cost[s]) continue
                cost[s] = walk.toDouble()
                how[s] = if (walk > 0) 2 else 0
                changeMin[s] = walk
                pq += cost[s] to s
            }
        }
        seed(from.index, 0)
        for (w in net.walksFrom[from.index]) seed(w.b, w.minutes)
        val walkToEnd = net.walksFrom[to.index].associate { it.b to it.minutes }

        while (pq.isNotEmpty()) {
            val (c, s) = pq.poll()!!
            if (c > cost[s]) continue
            if (s == finish) break
            val p = patternOf(s)
            val i = s - offsets[p]
            val pat = net.patterns[p]
            val here = pat.stops[i]
            fun relax(next: Int, add: Double, kind: Int, minutes: Int) {
                val nc = c + add
                if (nc < cost[next]) {
                    cost[next] = nc
                    prev[next] = s
                    how[next] = kind
                    changeMin[next] = minutes
                    pq += nc to next
                }
            }
            // Stay on the train.
            if (i + 1 < pat.stops.size) relax(state(p, i + 1), pat.min[i], 0, 0)
            // Everything below needs us to have ridden to this stop, not just stepped onto a train here.
            val rodeHere = prev[s] != -1 && (how[s] == 0 || how[s] == 3)
            if (!rodeHere) continue
            if (here == to.index) relax(finish, 0.0, 0, 0)
            walkToEnd[here]?.let { relax(finish, it.toDouble(), 2, it) }
            val ic = net.interchangeMinutes(net.stations[here])
            for ((q, j) in net.occurrences[here]) {
                val qp = net.patterns[q]
                if (q == p || !boardable(qp, j)) continue
                if (qp.line == pat.line) relax(state(q, j), 0.0, 3, 0)
                else relax(state(q, j), ic + changePenalty, 1, ic)
            }
            for (w in net.walksFrom[here]) for ((q, j) in net.occurrences[w.b]) {
                if (boardable(net.patterns[q], j)) relax(state(q, j), w.minutes + changePenalty, 2, w.minutes)
            }
        }
        if (prev[finish] == -1) return null
        val endWalk = if (how[finish] == 2) EndWalk(to, changeMin[finish]) else null

        // Walk back from the train we got off and cut the chain into legs at every real change.
        val chain = ArrayList<Int>()
        var s = prev[finish]
        while (s != -1) {
            chain += s
            s = prev[s]
        }
        chain.reverse()
        val startWalk = if (how[chain[0]] == 2) EndWalk(from, changeMin[chain[0]]) else null
        val legs = mutableListOf<Leg>()
        var start = 0
        for (k in 1..chain.size) {
            if (k < chain.size && how[chain[k]] != 1 && how[chain[k]] != 2) continue
            val stations = ArrayList<Station>()
            var minutes = 0.0
            var km = 0.0
            for (m in start until k) {
                val st = chain[m]
                val p = patternOf(st)
                val pos = st - offsets[p]
                if (m > start && how[st] == 0) {
                    minutes += net.patterns[p].min[pos - 1]
                    km += net.patterns[p].km[pos - 1]
                }
                if (m == start || how[st] == 0) stations += net.stations[net.patterns[p].stops[pos]]
            }
            val last = net.patterns[patternOf(chain[k - 1])]
            if (stations.size > 1) {
                val first = chain[start]
                legs += Leg(
                    last.line, last.towards, stations, minutes, km,
                    if (start == 0) 0 else changeMin[first], start > 0 && how[first] == 2,
                )
            }
            start = k
        }
        return Journey(legs, startWalk, endWalk)
    }

    /** Both route options; the second is dropped when it is the same route. */
    fun options(from: Station, to: Station, allowAirport: Boolean): Map<RouteMode, Journey> {
        val fast = route(from, to, RouteMode.Fastest, allowAirport) ?: return emptyMap()
        val few = route(from, to, RouteMode.FewestChanges, allowAirport)
        return buildMap {
            put(RouteMode.Fastest, fast)
            if (few != null && few.signature() != fast.signature()) put(RouteMode.FewestChanges, few)
        }
    }
}
