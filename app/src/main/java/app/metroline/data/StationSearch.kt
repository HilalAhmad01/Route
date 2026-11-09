package app.metroline.data

data class StationMatch(val station: Station, val alias: String? = null)

object StationSearch {
    private val sector = Regex("\\bsector\\b", RegexOption.IGNORE_CASE)

    fun search(net: Network, query: String): List<StationMatch> {
        val q = norm(query)
        if (q.isEmpty()) return net.stations.map { StationMatch(it) }
        val hits = net.stations.mapNotNull { s ->
            val alias = s.searchAliases.firstOrNull { it.first.startsWith(q) }?.second
            when {
                s.searchNames.any { it.startsWith(q) } -> Triple(0, s, null)
                s.searchWords.any { it.startsWith(q) } -> Triple(1, s, null)
                alias != null -> Triple(2, s, alias)
                s.searchNames.any { it.contains(q) } -> Triple(3, s, null)
                else -> null
            }
        }
        return hits.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
            .map { StationMatch(it.second, it.third) }
    }

    /**
     * The ways people write a station's name. "Noida Sector 18" is also typed "Noida Sec 18" and "Noida 18",
     * so every sector station is found both ways without an alias of its own.
     */
    internal fun spellings(name: String): List<String> =
        if (!sector.containsMatchIn(name)) listOf(name)
        else listOf(name, name.replace(sector, "Sec"), name.replace(sector, " "))

    internal fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
