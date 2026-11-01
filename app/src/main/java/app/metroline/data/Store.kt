package app.metroline.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

data class Trip(val from: String, val to: String) {
    fun encode() = "$from>$to"

    companion object {
        fun decode(s: String) = s.split('>').takeIf { it.size == 2 }?.let { Trip(it[0], it[1]) }
    }
}

data class Place(val label: String, val stationId: String)

class Store(context: Context, private val net: Network, private val onPlacesChanged: (List<Place>) -> Unit = {}) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    val recent = mutableStateListOf<Trip>().apply { addAll(load("recent")) }
    val saved = mutableStateListOf<Trip>().apply { addAll(load("saved")) }
    val places = mutableStateListOf<Place>().apply {
        addAll(
            prefs.getString("places", "").orEmpty().split('\n').mapNotNull { line ->
                line.split('\t').takeIf { it.size == 2 && net.stationOrNull(it[1]) != null }?.let { Place(it[0], it[1]) }
            },
        )
    }

    var smartCard by mutableStateOf(prefs.getBoolean("smart_card", false))
        private set

    fun updateSmartCard(on: Boolean) {
        smartCard = on
        prefs.edit { putBoolean("smart_card", on) }
    }

    private fun load(key: String) = prefs.getString(key, "").orEmpty().split('\n')
        .mapNotNull(Trip::decode)
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

    fun setPlace(rawLabel: String, stationId: String) {
        val label = rawLabel.replace(Regex("\\s+"), " ").trim().ifEmpty { return }
        val i = places.indexOfFirst { it.label.equals(label, ignoreCase = true) }
        if (i >= 0) places[i] = Place(places[i].label, stationId) else places.add(Place(label, stationId))
        persistPlaces()
    }

    fun removePlace(place: Place) {
        places.remove(place)
        persistPlaces()
    }

    private fun persistPlaces() {
        prefs.edit { putString("places", places.joinToString("\n") { "${it.label}\t${it.stationId}" }) }
        onPlacesChanged(places.toList())
    }

    fun place(label: String) = places.firstOrNull { it.label.equals(label, ignoreCase = true) }

    fun clearRecent() {
        recent.clear()
        persist("recent", recent)
    }
}
