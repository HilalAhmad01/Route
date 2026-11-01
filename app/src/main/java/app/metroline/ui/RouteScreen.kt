package app.metroline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.FareDay
import app.metroline.data.FarePart
import app.metroline.data.Fares
import app.metroline.data.Journey
import app.metroline.data.Leg
import app.metroline.data.RouteMode
import app.metroline.data.Station
import app.metroline.data.Trip
import java.time.DayOfWeek
import java.time.LocalDate

private enum class Option(val label: String) { Fastest("Fastest"), Fewer("Fewer changes"), Airport("Airport Express") }

@Composable
fun RouteScreen(trip: Trip) {
    val d = deps()
    val from = d.net.station(trip.from)
    val to = d.net.station(trip.to)
    LaunchedEffect(trip) { d.store.visited(trip) }

    val options = remember(trip) {
        val base = d.router.options(from, to, allowAirport = false)
        buildMap {
            base[RouteMode.Fastest]?.let { put(Option.Fastest, it) }
            base[RouteMode.FewestChanges]?.let { put(Option.Fewer, it) }
            val air = d.router.route(from, to, RouteMode.Fastest, allowAirport = true)
            val fast = base[RouteMode.Fastest]
            if (air != null && air.usesAirportExpress && (fast == null || air.minutes < fast.minutes)) put(Option.Airport, air)
        }
    }
    var selected by remember(trip) { mutableIntStateOf(0) }
    var saved by remember(trip) { mutableStateOf(d.store.isSaved(trip)) }

    TopBar(from.name, { d.nav.back() }, subtitle = "to ${to.name}") {
        IconButton(R.drawable.ic_arrow_up_down, { d.nav.open(Trip(trip.to, trip.from)) })
        IconButton(R.drawable.ic_bookmark, {
            d.store.toggleSaved(trip)
            saved = !saved
        }, if (saved) C.text else C.muted)
    }
    Hairline()

    val keys = options.keys.toList()
    val journey = keys.getOrNull(selected)?.let(options::get)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (journey == null) {
            Empty("No route found between these stations")
            return@Column
        }
        if (keys.size > 1) {
            Segmented(keys.map { it.label }, selected, { selected = it }, Modifier.padding(start = S.page, end = S.page, top = 16.dp))
        }
        Summary(journey)
        Hairline(Modifier.padding(horizontal = S.page))
        Spacer(Modifier.height(12.dp))
        Strip(journey)
        Spacer(Modifier.height(32.dp))
    }
}

private fun today() = if (LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY) FareDay.Sunday else FareDay.Weekday

@Composable
private fun Summary(j: Journey) {
    val d = deps()
    val day = today()
    val parts = remember(j, day) { Fares.forJourney(d.net, j, day) }
    Column(Modifier.padding(horizontal = S.page, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${j.minutes}", style = T.figure)
            Text(" min", style = T.secondary, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Stat("Fare", fareText(parts))
            Stat("Stations", "${j.stationCount}")
            Stat("Changes", "${j.changes}")
        }
        fareNote(parts, day)?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = T.meta)
        }
    }
}

private fun fareText(parts: List<FarePart>): String {
    val known = parts.mapNotNull { it.amount }
    if (known.isEmpty()) return "—"
    val approx = parts.any { it.amount != null && !it.verified }
    val partial = known.size < parts.size
    return (if (approx) "≈ " else "") + "₹${known.sum()}" + (if (partial) "+" else "")
}

private fun fareNote(parts: List<FarePart>, day: FareDay): String? {
    val notes = mutableListOf<String>()
    if (parts.size > 1) notes += parts.filter { it.amount != null }.joinToString(" + ") { "₹${it.amount} ${it.label}" }
    parts.filter { it.amount == null }.forEach { notes += "${it.label} fare not included" }
    if (day == FareDay.Sunday && parts.any { it.system == "dmrc" }) notes += "Sunday fare"
    return notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// ---- Journey strip -------------------------------------------------------------------------

private val Gutter = 40.dp
private val NodeY = 14.dp

private sealed interface Node {
    data object None : Node
    data class Dot(val color: Color) : Node
    data class RingNode(val color: Color) : Node
    data object Tick : Node
}

/**
 * One row of the strip. The gutter draws the incoming rail above the node and the outgoing
 * rail below it, so rows stack into one continuous line. null = no rail, dashed = walking.
 */
@Composable
private fun StripRow(
    top: Color?,
    bottom: Color?,
    node: Node,
    dashedTop: Boolean = false,
    dashedBottom: Boolean = false,
    minHeight: Dp = 0.dp,
    content: @Composable () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .heightIn(min = minHeight)
        .padding(end = S.page),
) {
    Box(
        Modifier.width(Gutter).fillMaxHeight().drawBehind {
            val x = S.page.toPx() + 4.dp.toPx()
            val y = NodeY.toPx()
            val w = 4.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
            if (top != null) drawLine(
                top, Offset(x, 0f), Offset(x, y), if (dashedTop) 2.dp.toPx() else w,
                if (dashedTop) StrokeCap.Round else StrokeCap.Butt, if (dashedTop) dash else null,
            )
            if (bottom != null) drawLine(
                bottom, Offset(x, y), Offset(x, size.height), if (dashedBottom) 2.dp.toPx() else w,
                if (dashedBottom) StrokeCap.Round else StrokeCap.Butt, if (dashedBottom) dash else null,
            )
            when (node) {
                is Node.Dot -> {
                    drawCircle(node.color, 7.dp.toPx(), Offset(x, y))
                    drawCircle(C.bg, 2.5.dp.toPx(), Offset(x, y))
                }
                is Node.RingNode -> {
                    drawCircle(C.bg, 7.dp.toPx(), Offset(x, y))
                    drawCircle(node.color, 6.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                }
                Node.Tick -> drawLine(C.bg, Offset(x - 2.dp.toPx(), y), Offset(x + 2.dp.toPx(), y), 1.5.dp.toPx())
                Node.None -> Unit
            }
        },
    )
    Spacer(Modifier.width(S.page - 4.dp))
    Box(Modifier.weight(1f)) { content() }
}

@Composable
private fun Strip(j: Journey) {
    val muted = C.muted
    j.startWalk?.let { w ->
        StripRow(null, muted, Node.RingNode(muted), dashedBottom = true) { StationName(w.station, primary = true) }
        StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) {
            WalkText("Walk to ${j.legs.first().from.name}", w.minutes)
        }
    }
    j.legs.forEachIndexed { k, leg ->
        val color = leg.line.c()
        val prev = j.legs.getOrNull(k - 1)
        val next = j.legs.getOrNull(k + 1)
        val sameStationChange = prev != null && !leg.boardedByWalk

        // Boarding (merged with the alighting row when changing at the same station).
        val topColor = when {
            k == 0 -> if (j.startWalk != null) muted else null
            sameStationChange -> prev!!.line.c()
            else -> muted
        }
        StripRow(
            topColor, color,
            if (k == 0 && j.startWalk == null) Node.Dot(color) else Node.RingNode(if (sameStationChange) C.text else color),
            dashedTop = k == 0 || !sameStationChange,
        ) {
            Column(Modifier.padding(bottom = 12.dp)) {
                StationName(leg.from, primary = true)
                if (sameStationChange) {
                    Spacer(Modifier.height(4.dp))
                    ChangeText(leg)
                }
                Spacer(Modifier.height(4.dp))
                LineText(leg)
            }
        }
        RideRows(leg)
        // Alighting: shown only at the end or before a walk; same-station changes merge into the next boarding row.
        if (next == null || next.boardedByWalk) {
            val last = next == null && j.endWalk == null
            StripRow(
                color, if (last) null else muted,
                if (last) Node.Dot(color) else Node.RingNode(color),
                dashedBottom = true,
            ) {
                Column(Modifier.padding(bottom = if (last) 0.dp else 8.dp)) { StationName(leg.to, primary = true) }
            }
            if (next != null) StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) {
                WalkText("Walk to ${next.from.name}", next.changeMinutes)
            }
        }
    }
    j.endWalk?.let { w ->
        StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) { WalkText("Walk to ${w.station.name}", w.minutes) }
        StripRow(muted, null, Node.Dot(C.text), dashedTop = true) { StationName(w.station, primary = true) }
    }
}

@Composable
private fun RideRows(leg: Leg) {
    val color = leg.line.c()
    var open by remember(leg) { mutableStateOf(false) }
    val between = leg.stations.subList(1, leg.stations.size - 1)
    StripRow(color, color, Node.None) {
        Row(
            Modifier.padding(bottom = 4.dp).press({ open = !open }).padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${leg.hops} ${if (leg.hops == 1) "stop" else "stops"} · ${Math.round(leg.minutes)} min", style = T.meta)
            if (between.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Ic(R.drawable.ic_chevron_right, C.muted, 14.dp, if (open) Modifier.rotate(90f) else Modifier)
            }
        }
    }
    AnimatedVisibility(open, enter = expandVertically(enter()) + fadeIn(enter()), exit = shrinkVertically(quick()) + fadeOut(quick())) {
        Column {
            for (s in between) StripRow(color, color, Node.Tick, minHeight = 28.dp) {
                Text(s.name, style = T.secondary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            }
            Spacer(Modifier.height(8.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun StationName(s: Station, primary: Boolean) {
    val d = deps()
    Text(
        s.name,
        style = if (primary) T.bodyMedium else T.body,
        modifier = Modifier.press({ d.nav.push(Screen.StationDetail(s.id)) }, rest = Color.Transparent)
            .padding(top = 2.dp),
    )
}

@Composable
private fun LineText(leg: Leg) = Row(verticalAlignment = Alignment.CenterVertically) {
    LineDot(leg.line)
    Spacer(Modifier.width(8.dp))
    Text(
        buildAnnotatedString {
            append(leg.line.name)
            withStyle(SpanStyle(color = C.muted)) { append("  towards ") }
            append(leg.towards)
        },
        style = T.secondary,
    )
}

@Composable
private fun ChangeText(leg: Leg) = Row(verticalAlignment = Alignment.CenterVertically) {
    Ic(R.drawable.ic_repeat_2, C.muted, 14.dp)
    Spacer(Modifier.width(8.dp))
    Text("Change · ${leg.changeMinutes} min", style = T.meta)
}

@Composable
private fun WalkText(text: String, minutes: Int) = Row(
    Modifier.padding(vertical = 8.dp, horizontal = 0.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Ic(R.drawable.ic_footprints, C.muted, 14.dp)
    Spacer(Modifier.width(8.dp))
    Text("$text · $minutes min", style = T.meta)
}
