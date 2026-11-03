package app.metroline.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class LineDto(val id: String, val name: String, val short: String, val color: String, val system: String)

@Serializable
data class StationDto(
    val id: String,
    val name: String,
    val system: String,
    val lines: List<String>,
    val lat: Double,
    val lon: Double,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class PatternDto(
    val line: String,
    val stops: List<String>,
    val towards: String,
    val km: List<Double>,
    val min: List<Double>,
    val platforms: List<List<Int>> = emptyList(),
    val dirs: List<List<PlatformDirDto>> = emptyList(),
)

@Serializable
data class PlatformDirDto(val p: Int, val t: List<String>)

@Serializable
data class WalkDto(val a: String, val b: String, val min: Int)

@Serializable
data class InterchangeDto(val default: Int, val at: Map<String, Int>)

@Serializable
data class NetworkDto(
    val version: String,
    val source: String,
    val lines: List<LineDto>,
    val stations: List<StationDto>,
    val patterns: List<PatternDto>,
    val walks: List<WalkDto>,
    val interchange: InterchangeDto,
    val fares: Map<String, JsonElement?>,
)

class Line(val id: String, val name: String, val short: String, val color: Long, val system: String)

class Station(
    val index: Int,
    val id: String,
    val name: String,
    val system: String,
    val lines: List<Line>,
    val lat: Double,
    val lon: Double,
    val aliases: List<String>,
) {
    val isInterchange get() = lines.size > 1

    var displayName: String = name
        internal set

    internal val searchName by lazy { StationSearch.norm(name) }
    internal val searchWords by lazy { name.lowercase().split(' ', '-', '(').map(StationSearch::norm) }
    internal val searchAliases by lazy { aliases.map { StationSearch.norm(it) to it } }
    override fun toString() = name
}

class Pattern(
    val index: Int,
    val line: Line,
    val stops: IntArray,
    val towards: String,
    val km: DoubleArray,
    val min: DoubleArray,
    /** Platforms trains leave from at each stop; empty where unknown. */
    val platforms: List<List<Int>>,
    /** Per stop, each platform with the stations its trains go towards. */
    val dirs: List<List<PlatformDir>>,
) {
    /**
     * The platform whose trains serve [exit], when exactly one does, preferring platforms that name
     * fewer destinations. Null when none or several tie, so the caller falls back to [platforms].
     */
    fun platformFor(pos: Int, exit: Station): PlatformDir? {
        val exact = dirs[pos].filter { exit.index in it.towards }
        val fewest = exact.minOfOrNull { it.towards.size } ?: return null
        return exact.singleOrNull { it.towards.size == fewest }
    }
}

class PlatformDir(val platform: Int, val towards: Set<Int>)

class Walk(val a: Int, val b: Int, val minutes: Int)

class Network(dto: NetworkDto) {
    val version = dto.version
    val source = dto.source
    val lines: List<Line> = dto.lines.map {
        Line(it.id, it.name, it.short, 0xFF000000 or it.color.removePrefix("#").toLong(16), it.system)
    }
    private val lineById = lines.associateBy { it.id }
    fun line(id: String) = lineById.getValue(id)

    val stations: List<Station> = dto.stations.mapIndexed { i, s ->
        Station(i, s.id, s.name, s.system, s.lines.map(::line).sortedBy(lines::indexOf), s.lat, s.lon, s.aliases)
    }
    init {
        val shared = stations.groupBy { it.name }.filterValues { it.size > 1 }
        for (group in shared.values) for (s in group) if (s.system != "dmrc") s.displayName = "${s.name} (${operatorName(s.system)})"
    }

    private val stationIndex = stations.associate { it.id to it.index }
    fun station(id: String) = stations[stationIndex.getValue(id)]
    fun stationOrNull(id: String) = stationIndex[id]?.let { stations[it] }

    val patterns: List<Pattern> = dto.patterns.mapIndexed { i, p ->
        Pattern(
            i, line(p.line), p.stops.map(stationIndex::getValue).toIntArray(), p.towards,
            p.km.toDoubleArray(), p.min.toDoubleArray(), p.platforms,
            p.dirs.ifEmpty { List(p.stops.size) { emptyList() } }
                .map { stops -> stops.map { PlatformDir(it.p, it.t.map { t -> stationIndex.getValue(t) }.toSet()) } },
        )
    }
    val walks: List<Walk> = dto.walks.map { Walk(stationIndex.getValue(it.a), stationIndex.getValue(it.b), it.min) }
    private val interchangeDefault = dto.interchange.default
    private val interchangeAt = dto.interchange.at
    fun interchangeMinutes(s: Station) = interchangeAt[s.name] ?: interchangeDefault

    val fares = FareRules.parse(dto.fares)

    val occurrences: List<List<Pair<Int, Int>>> = run {
        val occ = List(stations.size) { mutableListOf<Pair<Int, Int>>() }
        for (p in patterns) p.stops.forEachIndexed { i, s -> occ[s] += p.index to i }
        occ
    }

    internal val dmrcTrack: List<List<Pair<Int, Double>>> by lazy {
        val adj = List(stations.size) { mutableListOf<Pair<Int, Double>>() }
        for (p in patterns) {
            if (p.line.system != "dmrc") continue
            for (k in 0 until p.stops.size - 1) adj[p.stops[k]] += p.stops[k + 1] to p.km[k]
        }
        adj
    }

    val walksFrom: List<List<Walk>> = run {
        val w = List(stations.size) { mutableListOf<Walk>() }
        for (k in walks) {
            w[k.a] += k
            w[k.b] += Walk(k.b, k.a, k.minutes)
        }
        w
    }

    fun stationsOf(line: Line): List<List<Station>> {
        val ps = patterns.filter { it.line == line }
        val seen = HashSet<Int>()
        val sections = mutableListOf<List<Station>>()
        for (p in ps.sortedByDescending { it.stops.distinct().size }) {
            if (p.stops.all { it in seen }) continue
            sections += p.stops.distinct().map { stations[it] }
            seen += p.stops.toList()
        }
        return sections
    }

    companion object {
        fun operatorName(system: String) = when (system) {
            "dmrc", "dmrc_airport" -> "Delhi Metro"
            "rmgl" -> "Rapid Metro"
            "nmrc" -> "Noida Metro"
            "rrts" -> "Namo Bharat"
            else -> system
        }

        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String) = Network(json.decodeFromString(NetworkDto.serializer(), text))
    }
}
