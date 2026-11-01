package app.metroline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.FareContext
import app.metroline.data.FareQuote
import app.metroline.data.Fares
import app.metroline.data.ShareText
import app.metroline.data.Journey
import app.metroline.data.Leg
import app.metroline.data.RouteMode
import app.metroline.data.Station
import app.metroline.data.Trip

private enum class Option(val label: String) { Fastest("Fastest"), Fewer("Fewer changes"), Airport("Airport Express") }

@Composable
fun RouteScreen(trip: Trip) {
    val d = deps()
    val from = d.net.station(trip.from)
    val to = d.net.station(trip.to)
    LaunchedEffect(trip) { d.store.visited(trip) }

    val options = remember(trip) {
        val base = d.router.options(from, to, allowAirport = false).ifEmpty { d.router.options(from, to, allowAirport = true) }
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

    val keys = options.keys.toList()
    val journey = keys.getOrNull(selected)?.let(options::get)
    val fare = remember(journey, d.store.smartCard) {
        journey?.let { Fares.quote(d.net, it, FareContext.now(d.store.smartCard)) }
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pictureTop = rememberGraphicsLayer()
    val pictureBottom = rememberGraphicsLayer()

    TopBar("Route", { d.nav.back() }) {
        if (journey != null && fare != null) IconButton(R.drawable.ic_share_2, {
            scope.launch {
                val text = ShareText.of(journey, fare)
                val picture = runCatching {
                    stack(pictureTop.toImageBitmap().asAndroidBitmap(), pictureBottom.toImageBitmap().asAndroidBitmap())
                }.getOrNull()
                shareRoute(ctx, picture, text)
            }
        }, description = "Share route")
        IconButton(R.drawable.ic_arrow_up_down, { d.nav.open(Trip(trip.to, trip.from)) }, description = "Reverse trip")
        IconButton(R.drawable.ic_bookmark, {
            d.store.toggleSaved(trip)
            saved = !saved
        }, if (saved) C.text else C.muted, if (saved) "Remove saved trip" else "Save trip")
    }
    Hairline()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (journey == null || fare == null) {
            Empty("No route found between these stations")
            return@Column
        }
        if (keys.size > 1) {
            Segmented(keys.map { it.label }, selected, { selected = it }, Modifier.padding(start = S.page, end = S.page, top = 16.dp))
        }
        Column(Modifier.recordInto(pictureTop)) {
            Column(Modifier.padding(start = S.page, end = S.page, top = 20.dp)) {
                Text(from.displayName, style = T.title)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.muted)) { append("to ") }
                        append(to.displayName)
                    },
                    style = T.title,
                )
            }
            Summary(journey, fare)
        }
        if (journey.legs.any { it.line.system in WhatsAppTicket.systems }) BookTicket(journey)
        Column(Modifier.recordInto(pictureBottom).padding(bottom = 24.dp)) {
            Hairline(Modifier.padding(horizontal = S.page))
            Spacer(Modifier.height(12.dp))
            Strip(journey)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Modifier.recordInto(layer: GraphicsLayer) = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}.background(C.bg)

@Composable
private fun BookTicket(j: Journey) {
    val ctx = LocalContext.current
    val elsewhere = j.legs.map { it.line.system }.filter { it !in WhatsAppTicket.systems }.distinct()
    Column(Modifier.padding(start = S.page, end = S.page, bottom = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, C.hairline, RoundedCornerShape(S.radiusRow))
                .press({ WhatsAppTicket.open(ctx) }, rest = C.card)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.ic_ticket, C.text2, 16.dp)
            Spacer(Modifier.width(10.dp))
            Text("Book ticket on WhatsApp", style = T.body, modifier = Modifier.weight(1f))
            Ic(R.drawable.ic_chevron_right, C.muted, 16.dp)
        }
        if (elsewhere.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val names = elsewhere.joinToString(" and ") { if (it == "nmrc") "Noida Metro" else "Namo Bharat" }
            Text("$names tickets are bought separately", style = T.meta)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(j: Journey, fare: FareQuote) {
    Column(Modifier.padding(horizontal = S.page, vertical = 20.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Time", "${j.minutes} min")
            Stat("Fare", fare.headline())
            Stat("Stations", "${j.stationCount}")
            Stat("Changes", "${j.changes}")
        }
        fare.note()?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = T.meta)
        }
    }
}

private val Gutter = 40.dp
private val NodeY = 14.dp

private sealed interface Node {
    data object None : Node
    data class Dot(val color: Color) : Node
    data class RingNode(val color: Color) : Node
    data object Tick : Node
}

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
    if (j.isWalkOnly) {
        StripRow(null, muted, Node.RingNode(muted), dashedBottom = true) { StationName(j.from, primary = true) }
        StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) { WalkText("Walk to ${j.to.displayName}", j.minutes) }
        StripRow(muted, null, Node.Dot(C.text), dashedTop = true) { StationName(j.to, primary = true) }
        return
    }
    j.startWalk?.let { w ->
        StripRow(null, muted, Node.RingNode(muted), dashedBottom = true) { StationName(w.station, primary = true) }
        StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) {
            WalkText("Walk to ${j.legs.first().from.displayName}", w.minutes)
        }
    }
    j.legs.forEachIndexed { k, leg ->
        val color = leg.line.c()
        val prev = j.legs.getOrNull(k - 1)
        val next = j.legs.getOrNull(k + 1)
        val sameStationChange = prev != null && leg.isSameStationChange

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
        if (next == null || !next.isSameStationChange) {
            val last = next == null && j.endWalk == null
            StripRow(
                color, if (last) null else muted,
                if (last) Node.Dot(color) else Node.RingNode(color),
                dashedBottom = true,
            ) {
                Column(Modifier.padding(bottom = if (last) 0.dp else 8.dp)) { StationName(leg.to, primary = true) }
            }
            if (next != null) StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) {
                WalkText("Walk to ${next.from.displayName}", next.changeMinutes)
            }
        }
    }
    j.endWalk?.let { w ->
        StripRow(muted, muted, Node.None, dashedTop = true, dashedBottom = true) { WalkText("Walk to ${w.station.displayName}", w.minutes) }
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
        s.displayName,
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
            if (leg.towards.isNotEmpty()) {
                withStyle(SpanStyle(color = C.muted)) { append("  towards ") }
                append(leg.towards)
            } else {
                withStyle(SpanStyle(color = C.muted)) { append("  next stop ") }
                append(leg.stations[1].name)
            }
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
