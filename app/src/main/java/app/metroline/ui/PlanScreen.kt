package app.metroline.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.metroline.R
import app.metroline.data.Nearby
import app.metroline.data.Station
import app.metroline.data.Trip

@Composable
fun PlanScreen() {
    val d = deps()
    val nav = d.nav
    val from = nav.from?.let(d.net::stationOrNull)
    val to = nav.to?.let(d.net::stationOrNull)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Plan", style = T.title, modifier = Modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 20.dp))

        Box(Modifier.padding(horizontal = S.page)) {
            Card {
                EndRow("From", from) { nav.push(Screen.Pick(forFrom = true)) }
                Hairline(Modifier.padding(start = 44.dp, end = 56.dp))
                EndRow("To", to) { nav.push(Screen.Pick(forFrom = false)) }
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                IconButton(R.drawable.ic_arrow_up_down, {
                    val f = nav.from
                    nav.from = nav.to
                    nav.to = f
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Show route",
            { if (from != null && to != null) nav.open(Trip(from.id, to.id)) },
            Modifier.padding(horizontal = S.page),
            enabled = from != null && to != null,
        )

        NearestRow()

        val recent = d.store.recent
        if (recent.isNotEmpty()) {
            SectionLabel("Recent")
            for (t in recent) TripRow(t) { nav.open(t) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EndRow(label: String, station: Station?, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 64.dp).press(onClick, rest = C.card).padding(start = 16.dp, end = 56.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
        if (station != null) Rails(station.lines, 24.dp) else Ring(C.muted, 10.dp, C.card)
    }
    Spacer(Modifier.width(12.dp))
    Column {
        Text(label, style = T.meta)
        Text(
            station?.name ?: "Choose station",
            style = if (station != null) T.bodyMedium else T.body.copy(color = C.muted),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Nearest station": asks for approximate location only when tapped, then fills From. */
@Composable
private fun NearestRow() {
    val d = deps()
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    fun locate() {
        status = "Locating"
        Nearby.locate(ctx, d.net) { r ->
            status = when (r) {
                is Nearby.Result.Found -> {
                    val n = r.stations.first()
                    d.nav.from = n.station.id
                    "${n.station.name} · ${formatMeters(n.meters)}"
                }
                Nearby.Result.LocationOff -> "Location is off"
                Nearby.Result.NoFix -> "No location yet, try again outside"
            }
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locate() else status = "Location permission denied"
    }
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .press({
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) locate() else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.ic_locate)
        Spacer(Modifier.width(10.dp))
        Text("Nearest station", style = T.body.copy(color = C.text2), modifier = Modifier.weight(1f))
        status?.let { Text(it, style = T.meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun TripRow(trip: Trip, onClick: () -> Unit) {
    val d = deps()
    val a = d.net.station(trip.from)
    val b = d.net.station(trip.to)
    Row(
        Modifier.fillMaxWidth().heightIn(min = S.row).press(onClick).padding(horizontal = S.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp)) { Rails(a.lines) }
        Spacer(Modifier.width(12.dp))
        Text(a.name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        Ic(R.drawable.ic_chevron_right, C.muted, 16.dp, Modifier.padding(horizontal = 6.dp))
        Text(b.name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        Spacer(Modifier.weight(0.0001f))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.CenterEnd) { Rails(b.lines) }
    }
}

fun formatMeters(m: Int) = if (m < 1000) "$m m" else "%.1f km".format(m / 1000.0)
