package app.metroline.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlin.math.cos
import kotlin.math.hypot

class NearbyStation(val station: Station, val meters: Int)

object Nearby {
    sealed interface Result {
        data class Found(val stations: List<NearbyStation>) : Result
        data object LocationOff : Result
        data object NoFix : Result
    }

    fun closest(net: Network, lat: Double, lon: Double, count: Int = 3): List<NearbyStation> =
        net.stations.map { NearbyStation(it, distanceMeters(lat, lon, it.lat, it.lon)) }
            .sortedBy { it.meters }
            .take(count)

    private fun distanceMeters(la1: Double, lo1: Double, la2: Double, lo2: Double): Int {
        val k = 111_320.0
        return hypot((la2 - la1) * k, (lo2 - lo1) * k * cos(Math.toRadians(la1))).toInt()
    }

    /**
     * One-shot location from the phone's own providers (no Google Play services). Only called
     * after the user taps "Nearest station" and grants approximate location.
     */
    @SuppressLint("MissingPermission")
    fun locate(context: Context, net: Network, done: (Result) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(lm)) return done(Result.LocationOff)
        val enabled = lm.getProviders(true)
        val provider = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER,
        ).firstOrNull { it in enabled } ?: return done(Result.LocationOff)

        fun deliver(loc: Location?) {
            val fix = loc ?: enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            done(if (fix == null) Result.NoFix else Result.Found(closest(net, fix.latitude, fix.longitude)))
        }
        LocationManagerCompat.getCurrentLocation(
            lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(context), ::deliver,
        )
    }
}
