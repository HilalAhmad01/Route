package app.metroline.data

data class StationMatch(val station: Station, val alias: String? = null)

object StationSearch {
    fun search(net: Network, query: String): List<StationMatch> {
        val q = norm(query)
        if (q.isEmpty()) return net.stations.map { StationMatch(it) }
        val hits = net.stations.mapNotNull { s ->
            val alias = s.searchAliases.firstOrNull { it.first.startsWith(q) }?.second
            when {
                s.searchName.startsWith(q) -> Triple(0, s, null)
                s.searchWords.any { it.startsWith(q) } -> Triple(1, s, null)
                alias != null -> Triple(2, s, alias)
                s.searchName.contains(q) -> Triple(3, s, null)
                else -> null
            }
        }
        return hits.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
            .map { StationMatch(it.second, it.third) }
    }

    internal fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
