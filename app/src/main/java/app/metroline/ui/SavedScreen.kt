package app.metroline.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.metroline.R

@Composable
fun SavedScreen() {
    val d = deps()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Saved", style = T.title, modifier = Modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 4.dp))
        SectionLabel("Trips")
        if (d.store.saved.isEmpty()) Empty("Trips you bookmark on a route appear here")
        for (t in d.store.saved) TripRow(t) { d.nav.open(t) }

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
        Text("Metro ${versionName(ctx)}", style = T.heading)
        Spacer(Modifier.height(8.dp))
        Text(
            "Routes for Delhi Metro, Rapid Metro Gurugram, Noida Metro and Namo Bharat. " +
                "Works fully offline. No internet permission, no accounts, no ads, no analytics. " +
                "Recent and saved trips stay on this phone.",
            style = T.secondary,
        )
        Block("Network data", "${d.net.source}. Snapshot ${d.net.version}.")
        Block(
            "Times and fares",
            "Travel times are estimated from track distance, calibrated against the official app. " +
                "Delhi Metro fares follow the published distance slabs, token prices. " +
                "Fares marked ≈ (Rapid Metro across phases, Noida Metro) are not yet confirmed. " +
                "Airport Express and Namo Bharat fares are not included.",
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
