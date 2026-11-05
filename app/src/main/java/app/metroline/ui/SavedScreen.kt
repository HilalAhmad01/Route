package app.metroline.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.metroline.R

@Composable
fun SavedScreen() {
    val d = deps()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Saved", style = T.title, modifier = Modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 4.dp))
        SectionLabel("Bookmarks")
        for (p in d.store.places) {
            val s = d.net.station(p.stationId)
            val edit = { d.nav.push(Screen.Pick(PickTarget.Place(p.label))); Unit }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).press(edit).padding(start = S.page, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookmarkIcon(p.label, edit, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = T.bodyMedium)
                    Text(s.displayName, style = T.meta)
                }
                IconButton(R.drawable.ic_x, { d.store.removePlace(p) }, C.muted, "Remove ${p.label} bookmark")
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).press({ d.nav.push(Screen.AddPlace) }).padding(horizontal = S.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.ic_plus, C.text2, 16.dp)
            Spacer(Modifier.width(10.dp))
            Text("Add bookmark", style = T.body.copy(color = C.text2))
        }

        SectionLabel("Trips")
        if (d.store.saved.isEmpty()) Empty("Trips you bookmark on a route appear here")
        for (t in d.store.saved) TripRow(t) { d.nav.open(t) }

        SectionLabel("Settings")
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .press({ d.store.updateSmartCard(!d.store.smartCard) })
                .padding(horizontal = S.page)
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    toggleableState = if (d.store.smartCard) ToggleableState.On else ToggleableState.Off
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("I use a smart card", style = T.body)
                Text("Show card fares: 10% off, 20% off-peak", style = T.meta)
            }
            Spacer(Modifier.width(12.dp))
            Toggle(d.store.smartCard)
        }

        if (d.store.recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = 48.dp).press({ d.store.clearRecent() })
                    .padding(horizontal = S.page),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("Clear recent trips", style = T.body.copy(color = C.text2)) }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).press({ d.nav.push(Screen.About) }).padding(horizontal = S.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("About", style = T.body.copy(color = C.text2), modifier = Modifier.weight(1f))
            Ic(R.drawable.ic_chevron_right, C.muted, 16.dp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun versionName(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName

@Composable
fun AboutScreen() {
    val d = deps()
    val ctx = LocalContext.current
    TopBar("About", { d.nav.back() })
    Hairline()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = S.page)) {
        Spacer(Modifier.height(20.dp))
        Text("Route ${versionName(ctx)}", style = T.heading)
        Spacer(Modifier.height(8.dp))
        Text(
            "Routes for Delhi Metro, Rapid Metro Gurugram, Noida Metro and Namo Bharat. " +
                "Works fully offline and asks for no permissions: no internet, no location, no accounts, " +
                "no ads, no analytics. Bookmarks, recent and saved trips stay on this phone.",
            style = T.secondary,
        )
        Block("Network data", "${d.net.source}. Snapshot ${d.net.version}.")
        Block(
            "Times and fares",
            "Travel times are estimated from track distance, calibrated against the official app. " +
                "Delhi Metro fares follow the published distance slabs, token prices, with the official fare " +
                "where it differs (some Magenta Line trips). " +
                "Fares marked ≈ (Rapid Metro across phases, Noida Metro) are not yet confirmed. " +
                "Airport Express and Namo Bharat fares are not included.",
        )
        Block(
            "Platforms",
            "Platform numbers are shown where the official DMRC app shows them. Some stations, " +
                "Noida Metro and Namo Bharat have none, and where several platforms can serve a train all are listed.",
        )
        Block(
            "Tickets",
            "\"Book ticket on WhatsApp\" opens DMRC's official WhatsApp ticketing (+91 96508 55800) with \"Hi\" typed in. " +
                "Stations and payment are handled there by DMRC; this app never sees them. " +
                "Covers Delhi Metro, Airport Express and Rapid Metro, not Noida Metro or Namo Bharat.",
        )
        Block("Not affiliated", "This app is not made by or affiliated with DMRC, NMRC, NCRTC or Rapid Metro.")
        Block(
            "Credits",
            "Design direction: unslop by Damir Sokolovsky (@heydamir), CC BY 4.0, github.com/heydamir/unslop. " +
                "Icons: Lucide, ISC licence. Type: IBM Plex Sans and Plex Mono, SIL Open Font Licence. " +
                "Map data © OpenStreetMap contributors, ODbL.",
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Block(title: String, body: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = T.label)
    Spacer(Modifier.height(8.dp))
    Text(body, style = T.secondary)
}
