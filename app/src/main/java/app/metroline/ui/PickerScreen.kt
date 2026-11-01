package app.metroline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.Network
import app.metroline.data.Station

private val systemName = mapOf(
    "dmrc" to "Delhi Metro", "rmgl" to "Rapid Metro", "nmrc" to "Noida Metro", "rrts" to "Namo Bharat",
)

fun Station.operatorLabel() = systemName[system] ?: system

/** Prefix matches first, then word starts, then anywhere; ignores spaces, dots and hyphens. */
fun search(net: Network, query: String): List<Station> {
    val q = norm(query)
    if (q.isEmpty()) return net.stations
    val scored = net.stations.mapNotNull { s ->
        val n = norm(s.name)
        val words = s.name.lowercase().split(' ', '-', '(').map(::norm)
        when {
            n.startsWith(q) -> 0
            words.any { it.startsWith(q) } -> 1
            n.contains(q) -> 2
            else -> null
        }?.let { it to s }
    }
    return scored.sortedWith(compareBy({ it.first }, { it.second.name })).map { it.second }
}

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

@Composable
fun PickerScreen(forFrom: Boolean) {
    val d = deps()
    var query by remember { mutableStateOf("") }
    val results = remember(query) { search(d.net, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun pick(s: Station) = d.nav.setEnd(forFrom, s.id)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(R.drawable.ic_arrow_left, { d.nav.back() }, C.text)
            Row(
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(S.radiusRow))
                    .background(C.raised)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.ic_search, C.muted, 16.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (forFrom) "From" else "To", style = T.body.copy(color = C.muted))
                    BasicTextField(
                        query, { query = it },
                        singleLine = true,
                        textStyle = T.body,
                        cursorBrush = SolidColor(C.text),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(::pick) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Box(Modifier.press({ query = "" }).padding(4.dp)) { Ic(R.drawable.ic_x, C.muted, 16.dp) }
            }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { s ->
                StationRow(s, { pick(s) }, meta = if (s.system != "dmrc") s.operatorLabel() else null)
            }
            if (results.isEmpty()) item { Empty("No station matches “$query”") }
        }
    }
}
