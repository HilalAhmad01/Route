package app.metroline.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.Station
import app.metroline.data.Trip

@Composable
fun PlanScreen() {
    val d = deps()
    val nav = d.nav
    val from = nav.from?.let(d.net::stationOrNull)
    val to = nav.to?.let(d.net::stationOrNull)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = S.page, end = 8.dp, top = 24.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plan", style = T.title, modifier = Modifier.weight(1f))
            if (from != null || to != null) {
                Text(
                    "Clear",
                    style = T.body.copy(color = C.text2),
                    modifier = Modifier
                        .press({
                            nav.from = null
                            nav.to = null
                        })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Box(Modifier.padding(horizontal = S.page)) {
            Card {
                EndRow("From", from) { nav.push(Screen.Pick(PickTarget.From)) }
                Hairline(Modifier.padding(start = 44.dp, end = 56.dp))
                EndRow("To", to) { nav.push(Screen.Pick(PickTarget.To)) }
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                IconButton(R.drawable.ic_arrow_up_down, {
                    val f = nav.from
                    nav.from = nav.to
                    nav.to = f
                }, description = "Swap from and to")
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Show route",
            { if (from != null && to != null) nav.open(Trip(from.id, to.id)) },
            Modifier.padding(horizontal = S.page),
            enabled = from != null && to != null,
        )

        SectionLabel("Bookmarks")
        BookmarksRow()

        val recent = d.store.recent
        if (recent.isNotEmpty()) {
            SectionLabel("Recent", "Clear") { d.store.clearRecent() }
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
            station?.displayName ?: "Choose station",
            style = if (station != null) T.bodyMedium else T.body.copy(color = C.muted),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarksRow() {
    val d = deps()
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = S.page),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (place in d.store.places) {
            BookmarkIcon(
                place.label,
                onClick = { d.nav.startFrom(place.stationId) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    d.nav.push(Screen.Pick(PickTarget.Place(place.label)))
                },
            )
        }
        Box(
            Modifier
                .size(56.dp)
                .border(1.dp, C.hairline, CircleShape)
                .press({ d.nav.push(Screen.AddPlace) }, RoundedCornerShape(percent = 50))
                .semantics { contentDescription = "Add bookmark" },
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.ic_plus, C.muted, 20.dp) }
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
        Text(a.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        Ic(R.drawable.ic_chevron_right, C.muted, 16.dp, Modifier.padding(horizontal = 6.dp))
        Text(b.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
    }
}
