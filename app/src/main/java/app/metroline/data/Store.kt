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

enum class Tour { Off, FirstRun, Replay }

/**
 * Stations renamed in an update, as old id to new id. Saved trips and bookmarks store the id, so without this an
 * update would silently drop any that used a renamed station. Add every rename here and keep the old ones.
 */
internal val RENAMED_STATIONS = mapOf(
    "dmrc:Rohini West" to "dmrc:Dr. Baba Saheb Ambedkar Hospital",
    "dmrc:Rohini East" to "dmrc:Rohini",
    "dmrc:North Pitampura - Prashant Vihar" to "dmrc:Uttari Pitampura - Prashant Vihar",
    "dmrc:Badarpur" to "dmrc:Badarpur Border",
    "dmrc:Badhkal Mor" to "dmrc:Badkal Mor",
    "dmrc:GTB Nagar" to "dmrc:Guru Teg Bahadur Nagar",
    "dmrc:Jaffrabad" to "dmrc:Jafrabad",
    "dmrc:Barakhambha Road" to "dmrc:Barakhamba Road",
    "dmrc:Rajendra Nagar" to "dmrc:Major Mohit Sharma Rajendra Nagar",
    "dmrc:Tughlakabad" to "dmrc:Tughlakabad Station",
    "nmrc:Depot" to "nmrc:Depot Station",
    "dmrc:Brigadier Hoshiyar Singh" to "dmrc:Brig. Hoshiar Singh",
    "dmrc:Ghevra" to "dmrc:Ghevra Metro Station",
    "dmrc:Jawaharlal Nehru Stadium" to "dmrc:JLN Stadium",
    "dmrc:Surajmal Stadium" to "dmrc:Maharaja Surajmal Stadium",
)

class Store(context: Context, private val net: Network, private val onPlacesChanged: (List<Place>) -> Unit = {}) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    private var migrated = false

    /** The current id for a stored one, following a chain of renames. */
    private fun current(id: String): String {
        var now = id
        repeat(RENAMED_STATIONS.size) { RENAMED_STATIONS[now]?.let { now = it } }
        if (now != id) migrated = true
        return now
    }

    val recent = mutableStateListOf<Trip>().apply { addAll(load("recent")) }
    val saved = mutableStateListOf<Trip>().apply { addAll(load("saved")) }
    val places = mutableStateListOf<Place>().apply {
        addAll(
            prefs.getString("places", "").orEmpty().split('\n').mapNotNull { line ->
                line.split('\t').takeIf { it.size == 2 }?.let { Place(it[0], current(it[1])) }
                    ?.takeIf { net.stationOrNull(it.stationId) != null }
            },
        )
    }

    init {
        // Write migrated ids back once, so the old ones are not carried around.
        if (migrated) {
            persist("recent", recent)
            persist("saved", saved)
            writePlaces()
        }
    }

    var smartCard by mutableStateOf(prefs.getBoolean("smart_card", false))
        private set

    /** Whether the first-launch tour is showing. Only new users get it; it can be replayed from Saved. */
    var tour by mutableStateOf(if (tourPending(context)) Tour.FirstRun else Tour.Off)
        private set

    /**
     * Decided once, on the first launch of a version that has the tour, and remembered, so closing the app halfway
     * through brings the tour back. Anyone who used the app before has something stored, or app shortcuts published
     * on an earlier launch, and is not new.
     */
    private fun tourPending(context: Context): Boolean {
        prefs.getString("tour", null)?.let { return it == "pending" }
        val usedBefore = listOf("recent", "saved", "places", "smart_card").any(prefs::contains) ||
            context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE).contains("published")
        prefs.edit { putString("tour", if (usedBefore) "done" else "pending") }
        return !usedBefore
    }

    fun finishTour() {
        tour = Tour.Off
        prefs.edit { putString("tour", "done") }
    }

    fun replayTour() {
        tour = Tour.Replay
    }

    /** True only the first time a trip is saved, to say once where saved trips go. */
    fun firstSave(): Boolean {
        if (prefs.getBoolean("saved_note_shown", false)) return false
        prefs.edit { putBoolean("saved_note_shown", true) }
        return true
    }

    fun updateSmartCard(on: Boolean) {
        smartCard = on
        prefs.edit { putBoolean("smart_card", on) }
    }

    private fun load(key: String) = prefs.getString(key, "").orEmpty().split('\n')
        .mapNotNull(Trip::decode)
        .map { Trip(current(it.from), current(it.to)) }
        .filter { net.stationOrNull(it.from) != null && net.stationOrNull(it.to) != null }
        .distinct()

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

    private fun writePlaces() =
        prefs.edit { putString("places", places.joinToString("\n") { "${it.label}\t${it.stationId}" }) }

    private fun persistPlaces() {
        writePlaces()
        onPlacesChanged(places.toList())
    }

    fun place(label: String) = places.firstOrNull { it.label.equals(label, ignoreCase = true) }

    fun clearRecent() {
        recent.clear()
        persist("recent", recent)
    }
}
