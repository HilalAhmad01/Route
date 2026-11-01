package app.metroline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.metroline.R

@Composable
fun StationScreen(stationId: String) {
    val d = deps()
    val s = d.net.station(stationId)
    val walks = d.net.walksFrom[s.index]

    TopBar("", { d.nav.back() })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(s.displayName, style = T.title, modifier = Modifier.padding(horizontal = S.page))
        Text(s.operatorLabel(), style = T.secondary, modifier = Modifier.padding(horizontal = S.page, vertical = 4.dp))

        SectionLabel("Lines")
        for (l in s.lines) Row(
            Modifier.fillMaxWidth().heightIn(min = S.row).press({ d.nav.push(Screen.LineDetail(l.id)) }).padding(horizontal = S.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rails(listOf(l), 24.dp)
            Spacer(Modifier.width(16.dp))
            Text(l.name, style = T.body, modifier = Modifier.weight(1f))
            Ic(R.drawable.ic_chevron_right, C.muted, 16.dp)
        }
        if (s.lines.size > 1) {
            Text(
                "Change between lines · about ${d.net.interchangeMinutes(s)} min",
                style = T.meta, modifier = Modifier.padding(horizontal = S.page, vertical = 8.dp),
            )
        }

        if (walks.isNotEmpty()) {
            SectionLabel("Walk to")
            for (w in walks) {
                val o = d.net.stations[w.b]
                StationRow(o, { d.nav.push(Screen.StationDetail(o.id)) }, meta = "${o.operatorLabel()} · ${w.minutes} min walk")
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(Modifier.padding(horizontal = S.page)) {
            SecondaryButton("From here", { d.nav.setEnd(true, s.id) }, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("To here", { d.nav.setEnd(false, s.id) }) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
