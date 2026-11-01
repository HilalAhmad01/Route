package app.metroline.data

object ShareText {
    fun of(journey: Journey, fare: FareQuote): String = buildString {
        appendLine("${journey.from.displayName} → ${journey.to.displayName}")
        val changes = if (journey.changes == 1) "1 change" else "${journey.changes} changes"
        appendLine("${journey.minutes} min · ${fare.headline()} · ${journey.stationCount} stations · $changes")
        appendLine()
        if (journey.isWalkOnly) {
            append("Walk from ${journey.from.displayName} to ${journey.to.displayName}")
            return@buildString
        }
        journey.startWalk?.let { appendLine("Walk to ${journey.legs.first().from.displayName} (${it.minutes} min)") }
        journey.legs.forEachIndexed { k, leg ->
            val line = "${leg.line.name} ${leg.direction}"
            val ride = "${stops(leg.hops)} to ${leg.to.displayName}"
            appendLine(
                when {
                    k == 0 -> "${leg.line.name} from ${leg.from.displayName} ${leg.direction}, $ride"
                    leg.isSameStationChange -> "Change to $line (${leg.changeMinutes} min), $ride"
                    else -> "Walk to ${leg.from.displayName} (${leg.changeMinutes} min), then $line, $ride"
                },
            )
        }
        journey.endWalk?.let { appendLine("Walk to ${it.station.displayName} (${it.minutes} min)") }
    }.trimEnd()

    private fun stops(n: Int) = if (n == 1) "1 stop" else "$n stops"
}
