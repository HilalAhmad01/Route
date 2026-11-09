package app.metroline.ui

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.metroline.R
import app.metroline.data.FareContext
import app.metroline.data.Fares
import app.metroline.data.Journey
import app.metroline.data.Leg
import app.metroline.data.RouteMode
import app.metroline.data.Station
import app.metroline.data.StationMatch
import app.metroline.data.StationSearch
import app.metroline.data.Tour
import app.metroline.data.Trip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The first-launch tour: four screens, each with a small looping demo drawn from the app's own components and real
 * data (search results, routes, fares), so it always matches the app. Only new users see it; Saved can replay it.
 */

private const val PAGES = 4

private val Headline = T.title.copy(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em)
private val Strong = SpanStyle(color = C.text, fontWeight = FontWeight.Medium)

// The example trip used across the screens. "CP" and "Sec 18" are how people type it.
private const val TRIP_FROM = "dmrc:Rajiv Chowk"
private const val TRIP_TO = "dmrc:Noida Sector 18"
private const val HOME_STATION = "dmrc:Saket"
private val RECENT = listOf(Trip("dmrc:Kashmere Gate", "dmrc:Hauz Khas"), Trip("dmrc:New Delhi", "dmrc:IGI Airport"))

/** The example trip, worked out once and shared by the screens. */
private class Example(val from: Station, val to: Station, val journey: Journey?) {
    val trip = Trip(from.id, to.id)
}

/**
 * The words sit just above the dashes and the demo fills everything above them, so a tall phone shows more of the
 * app rather than empty space. The demo is never shorter than [least]; on a phone too short for both, the page scrolls.
 */
@Composable
private fun DemoAboveWords(height: Dp, least: Dp, demo: @Composable () -> Unit, words: @Composable () -> Unit) = Layout(
    contents = listOf(demo, words),
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
) { (demoPart, wordsPart), constraints ->
    val width = constraints.maxWidth
    val w = wordsPart.first().measure(Constraints(minWidth = width, maxWidth = width))
    val available = height.roundToPx()
    val demoHeight = (available - w.height).coerceIn(least.roundToPx(), maxOf(least, 600.dp).roundToPx())
    val dm = demoPart.first().measure(Constraints.fixed(width, demoHeight))
    val total = maxOf(available, demoHeight + w.height)
    layout(width, total) {
        dm.place(0, 0)
        w.place(0, total - w.height)
    }
}

/**
 * A demo's content runs on past its bottom edge and fades into the background there, instead of being cut off or
 * squeezed. The fade is drawn over the content in the page colour, which needs no extra layer.
 */
private fun Modifier.runsOff(edge: Dp = 56.dp) = clipToBounds()
    .drawWithContent {
        drawContent()
        val e = edge.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, C.bg), startY = size.height - e, endY = size.height),
            topLeft = Offset(0f, size.height - e), size = Size(size.width, e),
        )
    }
    .wrapContentHeight(Alignment.Top, unbounded = true)

/** With animations turned off in Android's settings, each demo shows its finished state instead of playing. */
private fun animationsOff(ctx: Context) =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun Onboarding(onDone: () -> Unit, onBackAtStart: () -> Unit, still: Boolean = animationsOff(LocalContext.current)) {
    val d = deps()
    val example = remember {
        val a = d.net.station(TRIP_FROM)
        val b = d.net.station(TRIP_TO)
        Example(a, b, d.router.route(a, b, RouteMode.Fastest, allowAirport = false))
    }
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    // Off once the tour is closed: it stays on screen for a moment while it fades out, and Back then is for the app.
    BackHandler(enabled = d.store.tour != Tour.Off) {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onBackAtStart()
    }

    Column(Modifier.fillMaxSize().background(C.bg).navBars()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = S.page, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = C.text, modifier = Modifier.size(28.dp, 24.dp))
            Spacer(Modifier.width(9.dp))
            Text("Route", style = T.bodyMedium.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
            val skip by animateFloatAsState(if (last) 0f else 1f, quick(), label = "skip")
            Text(
                "Skip",
                style = T.body.copy(color = C.text2),
                modifier = Modifier.alpha(skip).press(onDone, enabled = !last).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val active = pager.settledPage == page
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight && maxHeight < 480.dp
                val compact = maxHeight < 520.dp
                val roomy = maxHeight >= 660.dp
                // The demos only show what the words say, so screen readers read the words. The last one is a real choice.
                val quiet = if (page < PAGES - 1) Modifier.clearAndSetSemantics {} else Modifier
                val demo: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.then(quiet).padding(start = S.page, end = S.page, top = 4.dp)) {
                        when (page) {
                            0 -> SearchDemo(example, active, still)
                            1 -> BookmarksDemo(active, still)
                            2 -> SavedDemo(example, active, still)
                            else -> SmartCardChoice(example, compact, roomy)
                        }
                    }
                }
                if (wide) {
                    // Landscape phone: words beside the demo, so they are on screen without scrolling.
                    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Box(Modifier.weight(1f)) { Words(page) }
                        demo(Modifier.weight(1f).height(280.dp))
                    }
                } else {
                    // The last screen's choices must always show in full, so it never gets less than they need.
                    val least = if (page < PAGES - 1) 240.dp else if (compact) 300.dp else 330.dp
                    DemoAboveWords(maxHeight, least, { demo(Modifier.fillMaxSize()) }, { Words(page) })
                }
            }
        }

        Column(Modifier.padding(start = S.page, end = S.page, top = 8.dp, bottom = 16.dp)) {
            Dashes(pager.currentPage)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (last) "Start planning" else "Next",
                {
                    // A second tap while sliding would otherwise land on "Start planning" and skip the last question.
                    if (!pager.isScrollInProgress) {
                        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun Words(page: Int) {
    val (title, body) = when (page) {
        0 -> "Two stations. One route." to buildAnnotatedString {
            append("Search the way you talk: ")
            withStyle(Strong) { append("CP") }
            append(", ")
            withStyle(Strong) { append("T3") }
            append(", ")
            withStyle(Strong) { append("Sec 18") }
            append(".")
        }
        1 -> "Home in one tap." to buildAnnotatedString {
            append("Tap a bookmark to start your trip there. ")
            withStyle(Strong) { append("Hold it to change the station.") }
        }
        2 -> "Saved trips live in Saved." to buildAnnotatedString {
            append("Tap the bookmark on a route to keep it. It waits for you in the ")
            withStyle(Strong) { append("Saved") }
            append(" tab.")
        }
        else -> "Do you use a smart card?" to AnnotatedString("We'll show the fare you'll actually pay. Change it any time in Saved.")
    }
    Column(Modifier.padding(start = S.page, end = S.page, top = 22.dp, bottom = 16.dp)) {
        Text(title, style = Headline)
        Spacer(Modifier.height(10.dp))
        Text(body, style = T.body.copy(color = C.text2))
    }
}

/** Progress, one dash per screen, coloured like the metro lines. */
@Composable
private fun Dashes(current: Int) {
    val d = deps()
    val colors = remember { listOf("yellow", "blue", "magenta", "pink").map { d.net.line(it).c() } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEachIndexed { i, color ->
            val on = i == current
            val width by animateDpAsState(if (on) 30.dp else 14.dp, enter(), label = "dash")
            val fill by animateColorAsState(if (on) color else C.raised, enter(), label = "dashColor")
            Box(Modifier.size(width, 4.dp).clip(RoundedCornerShape(2.dp)).background(fill))
        }
    }
}

// ---------- demo plumbing ----------

/** Where things are inside a demo, so the pretend finger can move to them. */
private class Stage {
    var coords: LayoutCoordinates? = null
    val spots = HashMap<String, LayoutCoordinates>()
    val finger = Animatable(Offset.Zero, Offset.VectorConverter)
    val fingerAlpha = Animatable(0f)
    var fingerDown by mutableStateOf(false)

    fun center(key: String): Offset {
        val root = coords ?: return Offset.Zero
        val spot = spots[key] ?: return Offset.Zero
        if (!root.isAttached || !spot.isAttached) return Offset.Zero
        return root.localBoundingBoxOf(spot).center
    }

    /** The finger appears a little away from [key] and glides onto it. */
    suspend fun point(key: String, from: Offset) = coroutineScope {
        val target = center(key)
        finger.snapTo(target + from)
        launch { fingerAlpha.animateTo(1f, quick()) }
        finger.animateTo(target, tween(500, easing = Ease))
    }

    suspend fun hide() = fingerAlpha.animateTo(0f, quick())

    suspend fun reset() {
        fingerAlpha.snapTo(0f)
        fingerDown = false
    }
}

private fun Modifier.stage(s: Stage) = onGloballyPositioned { s.coords = it }
private fun Modifier.spot(s: Stage, key: String) = onGloballyPositioned { s.spots[key] = it }

/** Lets a full-width piece (a tab bar, a list row) reach the screen edges past the demo's side margins. */
private fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val extra = x.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun Finger(s: Stage) {
    val scale by animateFloatAsState(if (s.fingerDown) 0.82f else 1f, quick(), label = "finger")
    Box(
        Modifier
            .offset { (s.finger.value - Offset(18.dp.toPx(), 18.dp.toPx())).round() }
            .graphicsLayer {
                alpha = s.fingerAlpha.value
                scaleX = scale
                scaleY = scale
            }
            .size(36.dp)
            .clip(CircleShape)
            .background(C.text.copy(alpha = if (s.fingerDown) 0.32f else 0.22f))
            .border(1.5.dp, C.text.copy(alpha = 0.55f), CircleShape),
    )
}

@Composable
private fun dpOffset(x: Int, y: Int): Offset = with(LocalDensity.current) { Offset(x.dp.toPx(), y.dp.toPx()) }

@Composable
private fun HintPill(text: String, modifier: Modifier = Modifier) = AnimatedContent(
    text,
    modifier,
    transitionSpec = { fadeIn(quick()) togetherWith fadeOut(quick()) },
    label = "hint",
) {
    Text(
        it,
        style = T.meta.copy(color = C.text2),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(C.raised)
            .border(1.dp, C.hairline, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private sealed interface End {
    data object Empty : End
    data class Typing(val text: String) : End
    data class Picked(val station: Station) : End
}

/** The From/To row of the Plan screen, plus the typing state of the station picker. */
@Composable
private fun DemoEnd(label: String, end: End) = Row(
    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
        if (end is End.Picked) Rails(end.station.lines, 24.dp) else Ring(if (end is End.Typing) C.text else C.muted, 10.dp, C.card)
    }
    Spacer(Modifier.width(12.dp))
    Column {
        Text(label, style = T.meta)
        when (end) {
            End.Empty -> Text("Choose station", style = T.body.copy(color = C.muted))
            is End.Typing -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(end.text, style = T.body)
                Caret()
            }
            is End.Picked -> Text(end.station.displayName, style = T.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Caret() {
    val blink = rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500; 0f at 999 }),
        label = "blink",
    )
    // Read while drawing, so the blink redraws the cursor only and doesn't recompose the row every frame.
    Box(Modifier.padding(start = 1.dp).size(1.5.dp, 17.dp).graphicsLayer { alpha = blink.value }.background(C.text))
}

// ---------- shared pieces of the Plan screen ----------

@Composable
private fun DemoBookmarks(home: @Composable () -> Unit = { BookmarkIcon("Home", {}) }) {
    Text("Bookmarks", style = T.label, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        home()
        BookmarkIcon("Work", {})
        BookmarkIcon("College", {})
        Box(Modifier.size(56.dp).border(1.dp, C.hairline, CircleShape), contentAlignment = Alignment.Center) {
            Ic(R.drawable.ic_plus, C.muted, 20.dp)
        }
    }
}

@Composable
private fun DemoRecent() {
    Text("Recent", style = T.label, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    for (t in RECENT) Box(Modifier.bleed(S.page)) { TripRow(t) {} }
}

@Composable
private fun TripStats(example: Example, smartCard: Boolean, modifier: Modifier = Modifier) {
    val d = deps()
    val j = example.journey ?: return
    val fare = remember(j, smartCard) { Fares.quote(d.net, j, FareContext.now(smartCard)) }
    Row(modifier) {
        Stat("Time", "${j.minutes} min", Modifier.weight(1f))
        Stat("Fare", fare.headline(), Modifier.weight(1f))
        Stat("Changes", "${j.changes}", Modifier.weight(1f))
    }
}

// ---------- 1: search ----------

@Composable
private fun SearchDemo(example: Example, active: Boolean, still: Boolean) {
    val d = deps()
    val a = example.from
    val b = example.to
    var from by remember { mutableStateOf<End>(End.Empty) }
    var to by remember { mutableStateOf<End>(End.Empty) }
    var match by remember { mutableStateOf<StationMatch?>(null) }
    var matchShown by remember { mutableStateOf(false) }
    var matchUnderTo by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(false) }

    LaunchedEffect(active, still) {
        fun reset() {
            from = End.Empty; to = End.Empty; matchShown = false; ready = false; result = false
        }
        // Types a query letter by letter, then shows the app's real top search result for it.
        suspend fun type(query: String, set: (End) -> Unit): Station {
            for (i in 1..query.length) {
                set(End.Typing(query.take(i)))
                delay(110)
            }
            match = StationSearch.search(d.net, query).first()
            matchShown = true
            delay(750)
            matchShown = false
            return match!!.station
        }
        if (still) {
            from = End.Picked(a); to = End.Picked(b); ready = true; result = true
            return@LaunchedEffect
        }
        reset()
        if (!active) return@LaunchedEffect
        while (true) {
            reset()
            delay(500)
            matchUnderTo = false
            from = End.Picked(type("CP") { from = it })
            delay(450)
            matchUnderTo = true
            to = End.Picked(type("Sec 18") { to = it })
            ready = true
            delay(640)
            result = true
            delay(2600)
        }
    }

    // The Plan screen, as it is.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().runsOff()) {
            Card {
                DemoEnd("From", from)
                Hairline(Modifier.padding(start = 44.dp, end = 56.dp))
                DemoEnd("To", to)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Show route", {}, enabled = ready)
            // Its space is kept and it only fades in, so nothing below moves and nothing is laid out again per frame.
            val shown by animateFloatAsState(if (result) 1f else 0f, enter(), label = "result")
            TripStats(example, d.store.smartCard, Modifier.padding(top = 16.dp).graphicsLayer { alpha = shown })
            DemoBookmarks()
            DemoRecent()
        }
        AnimatedVisibility(
            matchShown,
            Modifier.offset { IntOffset(0, (if (matchUnderTo) 134.dp else 70.dp).roundToPx()) },
            enter = fadeIn(quick()) + slideInVertically(quick()) { it / 8 },
            exit = fadeOut(quick()),
        ) {
            match?.let { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(S.radiusRow))
                        .background(C.raised)
                        .border(1.dp, C.hairline, RoundedCornerShape(S.radiusRow))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Rails(m.station.lines, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(m.station.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    m.alias?.let { Text(it, style = T.meta) }
                }
            }
        }
    }
}

// ---------- 2: bookmarks ----------

@Composable
private fun BookmarksDemo(active: Boolean, still: Boolean) {
    val d = deps()
    val home = remember { d.net.station(HOME_STATION) }
    val stage = remember { Stage() }
    val hold = remember { Animatable(0f) }
    var from by remember { mutableStateOf<End>(End.Empty) }
    var hint by remember { mutableStateOf("Tap: start from Home") }
    var homeDown by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var launcher by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var iconDown by remember { mutableStateOf(false) }
    val nearHome = dpOffset(30, 40)
    val nearIcon = dpOffset(30, 50)

    LaunchedEffect(active, still) {
        suspend fun reset() {
            from = End.Empty; hint = "Tap: start from Home"; homeDown = false; sheet = false; query = ""
            launcher = false; menu = false; iconDown = false
            hold.snapTo(0f)
            stage.reset()
        }
        reset()
        if (still) {
            from = End.Picked(home); hint = "Tap to start · hold to change"
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        while (true) {
            reset()
            delay(400)
            // Tap: the trip starts from Home.
            stage.point("home", nearHome)
            stage.fingerDown = true; homeDown = true
            delay(140)
            stage.fingerDown = false; homeDown = false
            from = End.Picked(home)
            delay(1500)
            // Hold: change Home's station.
            hint = "Hold: change the station"; from = End.Empty
            delay(500)
            stage.fingerDown = true; homeDown = true
            hold.animateTo(1f, tween(550, easing = LinearEasing))
            stage.fingerDown = false; homeDown = false
            stage.hide()
            sheet = true
            hold.snapTo(0f)
            delay(500)
            for (i in 1..5) {
                query = home.name.take(i)
                delay(90)
            }
            delay(1100)
            // Hold the app icon on the home screen.
            sheet = false; launcher = true; hint = "Hold the app icon: From Home"
            delay(600)
            stage.point("icon", nearIcon)
            stage.fingerDown = true; iconDown = true
            delay(500)
            menu = true; stage.fingerDown = false; iconDown = false
            delay(2200)
            stage.hide()
            delay(300)
        }
    }

    Box(Modifier.fillMaxSize().stage(stage)) {
        Column(Modifier.fillMaxSize().runsOff()) {
            Card { DemoEnd("From", from) }
            DemoBookmarks(home = {
                Box(Modifier.spot(stage, "home")) {
                    BookmarkIcon("Home", {}, selected = homeDown)
                    Canvas(Modifier.size(56.dp)) {
                        val r = 31.dp.toPx()
                        if (hold.value > 0f) drawArc(
                            C.text, -90f, 360f * hold.value, false,
                            topLeft = center - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            })
            DemoRecent()
        }
        AnimatedVisibility(sheet, enter = fadeIn(enter()) + slideInVertically(enter()) { it / 14 }, exit = fadeOut(quick())) {
            ChangeStationSheet(query)
        }
        AnimatedVisibility(launcher, enter = fadeIn(enter()), exit = fadeOut(quick())) {
            HomeScreenIcon(stage, menu, iconDown)
        }
        HintPill(hint, Modifier.align(Alignment.BottomCenter))
        Finger(stage)
    }
}

/** The station picker as it opens from a held bookmark, showing the app's real results for what is typed. */
@Composable
private fun ChangeStationSheet(query: String) {
    val d = deps()
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.ic_arrow_left, C.text)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Home", style = T.bodyMedium)
                Text("Choose a new station", style = T.secondary)
            }
        }
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(S.radiusRow)).background(C.raised)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.ic_search, C.muted)
            Spacer(Modifier.width(10.dp))
            if (query.isEmpty()) Text("Search stations", style = T.body.copy(color = C.muted))
            else {
                Text(query, style = T.body)
                Caret()
            }
        }
        Spacer(Modifier.height(6.dp))
        if (query.isNotEmpty()) for (m in StationSearch.search(d.net, query).take(3)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Rails(m.station.lines)
                Spacer(Modifier.width(12.dp))
                Text(m.station.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The phone's home screen with Route's icon held down, showing the bookmark shortcuts. */
@Composable
private fun HomeScreenIcon(stage: Stage, menu: Boolean, down: Boolean) {
    val menuIn by animateFloatAsState(if (menu) 1f else 0f, enter(), label = "menu")
    val press by animateFloatAsState(if (down) 0.92f else 1f, quick(), label = "icon")
    Box(
        Modifier.fillMaxSize().bleed(S.page).drawBehind {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1A1D24), C.bg), Offset(size.width / 2, size.height), size.height))
        },
    ) {
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier
                    .graphicsLayer {
                        alpha = menuIn
                        val s = lerp(0.92f, 1f, menuIn)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .width(200.dp)
                    .clip(RoundedCornerShape(S.radiusCard))
                    .background(C.raised)
                    .border(1.dp, C.hairline, RoundedCornerShape(S.radiusCard))
                    .padding(6.dp),
            ) {
                for ((i, label) in listOf("Home", "Work").withIndex()) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(S.radiusRow))
                            .background(if (i == 0) C.pressed else Color.Transparent).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ic(bookmarkIcon(label)!!, C.text)
                        Spacer(Modifier.width(12.dp))
                        Text("From $label", style = T.body.copy(fontSize = 14.sp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.spot(stage, "icon").scale(press).size(60.dp).clip(CircleShape).background(C.bg)
                    .border(1.dp, Color(0x1AFFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_logo), null, tint = C.text, modifier = Modifier.size(34.dp, 29.dp)) }
            Spacer(Modifier.height(6.dp))
            Text("Route", style = T.secondary.copy(fontSize = 12.sp))
        }
    }
}

// ---------- 3: saved trips ----------

@Composable
private fun SavedDemo(example: Example, active: Boolean, still: Boolean) {
    val d = deps()
    val a = example.from
    val b = example.to
    val journey = example.journey
    val home = remember { d.net.station(HOME_STATION) }
    val stage = remember { Stage() }
    val flight = remember { Animatable(0f) }
    val ping = remember { Animatable(1f) }
    var saved by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }
    var inSaved by remember { mutableStateOf(false) }
    var savedList by remember { mutableStateOf(false) }
    val nearSave = dpOffset(-20, 60)

    LaunchedEffect(active, still) {
        suspend fun reset() {
            saved = false; notice = false; flying = false; inSaved = false; savedList = false
            flight.snapTo(0f); ping.snapTo(1f)
            stage.reset()
        }
        reset()
        if (still) {
            saved = true; inSaved = true; savedList = true
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        while (true) {
            reset()
            delay(500)
            stage.point("save", nearSave)
            stage.fingerDown = true
            delay(140)
            stage.fingerDown = false; saved = true
            delay(200)
            stage.hide()
            flying = true; notice = true
            delay(700)
            flight.animateTo(1f, tween(600, easing = Ease))
            flying = false; inSaved = true
            ping.snapTo(0f)
            launch { ping.animateTo(1f, tween(800, easing = Ease)) }
            delay(900)
            notice = false; savedList = true
            delay(2400)
        }
    }

    Box(Modifier.fillMaxSize().stage(stage)) {
        // The route screen, with the stops of the trip running down to the tab bar.
        Column(Modifier.fillMaxSize().padding(bottom = 60.dp).runsOff()) {
            Box(Modifier.bleed(12.dp)) {
                TopBar("Route", {}, subtitle = "${a.displayName} → ${b.displayName}") {
                    IconButton(R.drawable.ic_share_2, {}, description = "Share route")
                    IconButton(R.drawable.ic_arrow_up_down, {}, description = "Reverse trip")
                    Box(Modifier.spot(stage, "save")) {
                        IconButton(R.drawable.ic_bookmark, {}, if (saved) C.text else C.muted, "Save trip")
                    }
                }
            }
            Hairline(Modifier.bleed(S.page))
            if (journey != null) {
                TripStats(example, d.store.smartCard, Modifier.padding(top = 16.dp))
                val leg = journey.legs.first()
                Card(Modifier.padding(top = 16.dp)) {
                    Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(leg.line.c()))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(leg.line.name, style = T.bodyMedium)
                            Text("${leg.direction} · ${leg.hops} stops", style = T.meta)
                        }
                    }
                }
                Stops(leg)
            }
        }

        // The Saved tab as it then looks: the bookmark, the trip just saved, and the smart card setting.
        AnimatedVisibility(savedList, Modifier.padding(bottom = 61.dp), enter = fadeIn(enter()), exit = fadeOut(quick())) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The trip just saved is the point; on a short screen the bookmark row makes way for it.
                val room = maxHeight >= 300.dp
                Column(Modifier.fillMaxSize().bleed(S.page).background(C.bg).runsOff()) {
                    Text("Saved", style = T.title, modifier = Modifier.padding(start = S.page, top = 4.dp))
                    if (room) {
                        SectionLabel("Bookmarks")
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = S.page),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BookmarkIcon("Home", {}, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Home", style = T.bodyMedium)
                                Text(home.displayName, style = T.meta)
                            }
                        }
                    }
                    SectionLabel("Trips")
                    TripRow(example.trip) {}
                    SectionLabel("Settings")
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = S.page),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("I use a smart card", style = T.body)
                            Text("Show card fares: 10% off, 20% off-peak", style = T.meta)
                        }
                        Toggle(d.store.smartCard)
                    }
                }
            }
        }

        // The saved trip drops into the Saved tab.
        if (flying) {
            Row(
                Modifier
                    .offset(y = 150.dp)
                    .spot(stage, "fly")
                    .graphicsLayer {
                        // Measured here, after layout, when both positions are known.
                        val target = stage.center("savedTab") - stage.center("fly")
                        val p = flight.value
                        translationX = target.x * p
                        translationY = target.y * p
                        scaleX = lerp(1f, 0.18f, p)
                        scaleY = lerp(1f, 0.18f, p)
                        alpha = 1f - ((p - 0.33f) / 0.67f).coerceIn(0f, 1f)
                    }
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(S.radiusRow))
                    .background(C.raised)
                    .border(1.dp, C.hairline, RoundedCornerShape(S.radiusRow))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Rails(a.lines)
                Spacer(Modifier.width(12.dp))
                Text(a.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Ic(R.drawable.ic_chevron_right, C.muted, 16.dp, Modifier.padding(horizontal = 6.dp))
                Text(b.displayName, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            }
        }

        AnimatedVisibility(
            notice,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp),
            enter = fadeIn(enter()) + slideInVertically(enter()) { it / 2 },
            exit = fadeOut(quick()),
        ) { Notice("Saved · find it in the Saved tab") }

        TabBar(
            if (inSaved) Tab.Saved else Tab.Plan, {},
            Modifier.align(Alignment.BottomCenter).bleed(S.page),
            insets = false,
            tabModifier = { if (it == Tab.Saved) Modifier.spot(stage, "savedTab") else Modifier },
        )
        Canvas(Modifier.fillMaxSize()) {
            val p = ping.value
            if (p < 1f) drawCircle(C.text.copy(alpha = 0.10f * (1f - p)), 22.dp.toPx() * lerp(0.4f, 1.4f, p), stage.center("savedTab"))
        }
        Finger(stage)
    }
}

/** The stations of one leg, down a line in its colour, as on the route screen. */
@Composable
private fun Stops(leg: Leg) {
    val color = leg.line.c()
    Column(Modifier.padding(top = 12.dp, start = 16.dp)) {
        leg.stations.forEachIndexed { i, station ->
            val end = i == 0 || i == leg.stations.lastIndex
            Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(16.dp).fillMaxHeight()) {
                    val x = size.width / 2
                    val y = size.height / 2
                    drawLine(color, Offset(x, if (i == 0) y else 0f), Offset(x, if (i == leg.stations.lastIndex) y else size.height), 4.dp.toPx())
                    if (end) {
                        drawCircle(C.bg, 7.dp.toPx(), Offset(x, y))
                        drawCircle(color, 6.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                    } else drawCircle(C.bg, 1.5.dp.toPx(), Offset(x, y))
                }
                Spacer(Modifier.width(12.dp))
                Text(station.displayName, style = if (end) T.bodyMedium else T.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- 4: smart card ----------

/** Not a demo: picking here switches the app's smart card setting, and the fare shows what that means. */
@Composable
private fun SmartCardChoice(example: Example, compact: Boolean, roomy: Boolean) {
    val d = deps()
    val journey = example.journey
    val card = d.store.smartCard
    val fare = remember(journey, card) { journey?.let { Fares.quote(d.net, it, FareContext.now(card)) } }

    Column {
        if (journey != null && fare != null) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Rails(journey.legs.map { it.line }.distinct())
                Spacer(Modifier.width(12.dp))
                Text(
                    "${example.from.displayName} → ${example.to.displayName}", style = T.body,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text("${journey.minutes} min", style = T.meta)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            Text("Fare", style = T.label)
            AnimatedContent(fare.headline(), transitionSpec = { fadeIn(quick()) togetherWith fadeOut(quick()) }, label = "fare") {
                Text(
                    it,
                    style = if (compact) T.figure else T.figure.copy(fontSize = 40.sp, lineHeight = 44.sp),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(fare.note() ?: "Token", style = T.meta)
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        }
        Choice("Smart card", "10% off, 20% off-peak", card, compact) { d.store.updateSmartCard(true) }
        Spacer(Modifier.height(10.dp))
        Choice("Token", "Full fare", !card, compact) { d.store.updateSmartCard(false) }
        // Only with room for all of it, so it is never cut off.
        if (journey != null && roomy) FareTable(example, card)
    }
}

/** The trip's three prices side by side, on an ordinary weekday, with when off-peak applies. */
@Composable
private fun FareTable(example: Example, card: Boolean) {
    val d = deps()
    val journey = example.journey ?: return
    val prices = remember(journey) {
        val day = LocalDate.now(ZoneId.of("Asia/Kolkata")).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
        fun at(hour: Int, smart: Boolean) = Fares.quote(d.net, journey, FareContext.at(day.atTime(hour, 0), smart)).headline()
        listOf("Token" to at(9, false), "Smart card" to at(9, true), "Smart card, off-peak" to at(14, true))
    }
    Column(Modifier.padding(top = 24.dp)) {
        Text("This trip on a weekday", style = T.label)
        Spacer(Modifier.height(4.dp))
        prices.forEachIndexed { i, (label, price) ->
            val yours = if (card) i > 0 else i == 0
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = if (yours) T.body else T.body.copy(color = C.muted), modifier = Modifier.weight(1f))
                Text(price, style = if (yours) T.figureSmall else T.figureSmall.copy(color = C.muted))
            }
            if (i < prices.lastIndex) Hairline()
        }
        Spacer(Modifier.height(8.dp))
        Text("Off-peak: before 8 am, 12–5 pm and after 9 pm", style = T.meta)
    }
}

@Composable
private fun Choice(title: String, detail: String, on: Boolean, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(S.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .press(onClick, shape, if (on) C.raised else C.card)
            .border(1.dp, if (on) C.text2 else C.hairline, shape)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = on
            }
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyMedium)
            Text(detail, style = T.meta)
        }
        val dot by animateFloatAsState(if (on) 1f else 0f, quick(), label = "radio")
        Box(
            Modifier.size(20.dp).border(2.dp, if (on) C.text else C.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(10.dp).scale(dot).clip(CircleShape).background(C.text)) }
    }
}
