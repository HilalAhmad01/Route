package app.metroline.data

import java.util.PriorityQueue
import kotlin.math.roundToInt

enum class RouteMode { Fastest, FewestChanges }

class Leg(
    val line: Line,
    val towards: String,
    val stations: List<Station>,
    val minutes: Double,
    val km: Double,
    val changeMinutes: Int,
    val boardedByWalk: Boolean,
    val walkedFrom: Station? = null,
    val platforms: List<Int> = emptyList(),
) {
    val isSameStationChange get() = walkedFrom == null || walkedFrom.name == from.name
    val from get() = stations.first()
    val to get() = stations.last()
    val hops get() = stations.size - 1

    val direction get() = if (towards.isNotEmpty()) "towards $towards" else "next stop ${stations[1].name}"
    /** Worded as the official app does: "Platform No. 2", or "Platform No. 1 & 3" when trains leave from either. */
    val platform get() = platforms.takeIf { it.isNotEmpty() }?.joinToString(" & ", "Platform No. ")
}

class EndWalk(val station: Station, val minutes: Int)

class Journey(
    val from: Station,
    val to: Station,
    val legs: List<Leg>,
    val startWalk: EndWalk? = null,
    val endWalk: EndWalk? = null,
) {
    val minutes: Int = (legs.sumOf { it.minutes } + legs.sumOf { it.changeMinutes } +
        (startWalk?.minutes ?: 0) + (endWalk?.minutes ?: 0)).roundToInt()
    val changes: Int = (legs.size - 1).coerceAtLeast(0)
    val isWalkOnly get() = legs.isEmpty()

    val stationCount: Int =
        if (legs.isEmpty()) 2 else legs.sumOf { it.hops } + 1 + legs.count { !it.isSameStationChange }
    val usesAirportExpress get() = legs.any { it.line.system == "dmrc_airport" }
    fun signature() = legs.joinToString("|") { leg -> leg.line.id + ":" + leg.stations.joinToString(",") { it.id } }
}

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

    fun route(from: Station, to: Station, mode: RouteMode, allowAirport: Boolean, allowNamoBharat: Boolean = true): Journey? {
        if (from == to) return null
        net.walksFrom[from.index].firstOrNull { it.b == to.index }?.let {
            return Journey(from, to, emptyList(), endWalk = EndWalk(to, it.minutes))
        }
        val changePenalty = if (mode == RouteMode.FewestChanges) 1000.0 else 0.0
        fun allowed(p: Pattern) =
            (allowAirport || p.line.system != "dmrc_airport") && (allowNamoBharat || p.line.system != "rrts")
        fun boardable(p: Pattern, i: Int) = allowed(p) && i < p.stops.size - 1

        val finish = stateCount
        val cost = DoubleArray(stateCount + 1) { Double.MAX_VALUE }
        val prev = IntArray(stateCount + 1) { -1 }
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
            if (i + 1 < pat.stops.size) relax(state(p, i + 1), pat.min[i], 0, 0)
            val rodeHere = prev[s] != -1 && (how[s] == 0 || how[s] == 3)
            if (!rodeHere) continue
            if (here == to.index) relax(finish, 0.0, 0, 0)
            walkToEnd[here]?.let { relax(finish, it.toDouble(), 2, it) }
            val ic = net.interchangeMinutes(net.stations[here])
            for ((q, j) in net.occurrences[here]) {
                val qp = net.patterns[q]
                if (q == p || !boardable(qp, j)) continue
                if (qp.line == pat.line && qp.service == pat.service) relax(state(q, j), 0.0, 3, 0)
                else relax(state(q, j), ic + changePenalty, 1, ic)
            }
            for (w in net.walksFrom[here]) for ((q, j) in net.occurrences[w.b]) {
                if (boardable(net.patterns[q], j)) relax(state(q, j), w.minutes + changePenalty, 2, w.minutes)
            }
        }
        if (prev[finish] == -1) return null
        val endWalk = if (how[finish] == 2) EndWalk(to, changeMin[finish]) else null

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
            val lastP = patternOf(chain[k - 1])
            val last = net.patterns[lastP]
            if (stations.size > 1) {
                val first = chain[start]
                val boardP = patternOf(first)
                val boardPos = (start until k).first { patternOf(chain[it]) == lastP }.let { chain[it] - offsets[lastP] }
                val stopNames = last.stops.map { net.stations[it].name }
                val ahead = (boardPos + 1 until last.stops.size).any { stopNames[it] == last.towards }
                val behind = !ahead && last.towards in stopNames
                val terminal = if (behind) stopNames.last() else last.towards
                val boardedAt = first - offsets[boardP]
                // Platforms are read by where the leg gets off, as DMRC's planner does, falling back to the pattern.
                val direct = net.patterns[boardP].platformFor(boardedAt, stations.last())
                val shown = if (direct != null) stations.last().name else terminal
                val towards = if (shown == stations.first().name) "" else shown
                legs += Leg(
                    last.line, towards, stations, minutes, km,
                    if (start == 0) 0 else changeMin[first], start > 0 && how[first] == 2,
                    if (start > 0 && how[first] == 2) legs.lastOrNull()?.to else null,
                    direct?.let { listOf(it.platform) } ?: net.patterns[boardP].platforms.getOrNull(boardedAt).orEmpty(),
                )
            }
            start = k
        }
        return Journey(from, to, legs, startWalk, endWalk)
    }

    /**
     * Namo Bharat has its own ticket and no fare here, so it is only used when the trip starts or ends on it. The
     * official app likewise never sends a Delhi Metro trip over it, even where it would be quicker.
     */
    fun allowsNamoBharat(from: Station, to: Station) = from.system == "rrts" || to.system == "rrts"

    fun options(from: Station, to: Station, allowAirport: Boolean): Map<RouteMode, Journey> {
        val namo = allowsNamoBharat(from, to)
        val fast = route(from, to, RouteMode.Fastest, allowAirport, namo) ?: return emptyMap()
        val few = route(from, to, RouteMode.FewestChanges, allowAirport, namo)
        return buildMap {
            put(RouteMode.Fastest, fast)
            if (few != null && few.signature() != fast.signature()) put(RouteMode.FewestChanges, few)
        }
    }
}
