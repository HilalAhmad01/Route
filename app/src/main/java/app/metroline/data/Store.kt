package app.metroline.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit

/** A trip the user looked at or saved, by station id. */
data class Trip(val from: String, val to: String) {
    fun encode() = "$from>$to"

    companion object {
        fun decode(s: String) = s.split('>').takeIf { it.size == 2 }?.let { Trip(it[0], it[1]) }
    }
}

/** Recent and saved trips. Stored only in this app's private preferences on the phone. */
class Store(context: Context, private val net: Network) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    val recent = mutableStateListOf<Trip>().apply { addAll(load("recent")) }
    val saved = mutableStateListOf<Trip>().apply { addAll(load("saved")) }

    private fun load(key: String) = prefs.getString(key, "").orEmpty().split('\n')
        .mapNotNull(Trip::decode)
        // Drop trips whose stations no longer exist after a network update.
        .filter { net.stationOrNull(it.from) != null && net.stationOrNull(it.to) != null }

    private fun persist(key: String, list: List<Trip>) =
        prefs.edit { putString(key, list.joinToString("\n") { it.encode() }) }

    fun visited(trip: Trip) {
        recent.remove(trip)
        recent.add(0, trip)
        while (recent.size > 8) recent.removeAt(recent.lastIndex)
        persist("recent", recent)
    }

    fun isSaved(trip: Trip) = trip in saved

    fun toggleSaved(trip: Trip) {
        if (!saved.remove(trip)) saved.add(0, trip)
        persist("saved", saved)
    }

    fun clearRecent() {
        recent.clear()
        persist("recent", recent)
    }
}
