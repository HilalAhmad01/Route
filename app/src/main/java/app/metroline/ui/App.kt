package app.metroline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.Network
import app.metroline.data.Router
import app.metroline.data.Store
import app.metroline.data.Trip

/** Everything a screen needs, provided once at the root. */
class AppDeps(val net: Network, val router: Router, val store: Store, val nav: Nav)

val LocalDeps = staticCompositionLocalOf<AppDeps> { error("no deps") }

sealed interface Screen {
    data object Home : Screen
    data class Route(val trip: Trip) : Screen
    data class Pick(val forFrom: Boolean) : Screen
    data class LineDetail(val lineId: String) : Screen
    data class StationDetail(val stationId: String) : Screen
    data object About : Screen
}

enum class Tab(val label: String, val icon: Int) {
    Plan("Plan", R.drawable.ic_route),
    Map("Map", R.drawable.ic_map),
    Lines("Lines", R.drawable.ic_list_tree),
    Saved("Saved", R.drawable.ic_bookmark),
}

/** A tiny back stack. The plan (from/to) lives here so every screen can set it. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableStateOf(Tab.Plan)
    var from by mutableStateOf<String?>(null)
    var to by mutableStateOf<String?>(null)

    val top get() = stack.last()
    fun push(s: Screen) = stack.add(s)
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (tab != Tab.Plan) {
            tab = Tab.Plan
            return true
        }
        return false
    }

    /** Set one end of the trip and jump to the route once both ends are known. */
    fun setEnd(fromEnd: Boolean, stationId: String) {
        if (fromEnd) from = stationId else to = stationId
        if (from == to) if (fromEnd) to = null else from = null
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = Tab.Plan
        val f = from
        val t = to
        if (f != null && t != null) push(Screen.Route(Trip(f, t)))
    }

    fun open(trip: Trip) {
        from = trip.from
        to = trip.to
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = Tab.Plan
        push(Screen.Route(trip))
    }
}

@Composable
fun App(net: Network, store: Store, finish: () -> Unit) {
    val nav = remember { Nav() }
    val deps = remember { AppDeps(net, Router(net), store, nav) }
    BackHandler { if (!nav.back()) finish() }

    CompositionLocalProvider(LocalDeps provides deps) {
        Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().imePadding()) {
            AnimatedContent(
                targetState = nav.top to nav.tab,
                transitionSpec = {
                    (fadeIn(enter()) + slideInVertically(enter()) { 24 }) togetherWith fadeOut(quick())
                },
                label = "screen",
            ) { (screen, tab) ->
                when (screen) {
                    Screen.Home -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Plan -> PlanScreen()
                                Tab.Map -> MapScreen()
                                Tab.Lines -> LinesScreen()
                                Tab.Saved -> SavedScreen()
                            }
                        }
                        TabBar(tab) { nav.tab = it }
                    }
                    is Screen.Route -> Column(Modifier.fillMaxSize().navBars()) { RouteScreen(screen.trip) }
                    is Screen.Pick -> Column(Modifier.fillMaxSize().navBars()) { PickerScreen(screen.forFrom) }
                    is Screen.LineDetail -> Column(Modifier.fillMaxSize().navBars()) { LineScreen(screen.lineId) }
                    is Screen.StationDetail -> Column(Modifier.fillMaxSize().navBars()) { StationScreen(screen.stationId) }
                    Screen.About -> Column(Modifier.fillMaxSize().navBars()) { AboutScreen() }
                }
            }
        }
    }
}

@Composable
fun Modifier.navBars() = windowInsetsPadding(WindowInsets.navigationBars)

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) = Column(Modifier.background(C.bg).navBars()) {
    Hairline()
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (t in Tab.entries) {
            val on = t == current
            Column(
                Modifier.weight(1f).height(52.dp).press({ onSelect(t) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Ic(t.icon, if (on) C.text else C.muted)
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = T.meta.copy(color = if (on) C.text else C.muted))
            }
        }
    }
}

@Composable
fun deps() = LocalDeps.current
