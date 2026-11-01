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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.StationSearch
import app.metroline.data.Station

private val systemName = mapOf(
    "dmrc" to "Delhi Metro", "rmgl" to "Rapid Metro", "nmrc" to "Noida Metro", "rrts" to "Namo Bharat",
)

fun Station.operatorLabel() = systemName[system] ?: system

@Composable
fun PickerScreen(target: PickTarget) {
    val d = deps()
    var query by remember { mutableStateOf("") }
    val results = remember(query) { StationSearch.search(d.net, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun pick(s: Station): Unit = when (target) {
        PickTarget.From -> d.nav.setEnd(true, s.id)
        PickTarget.To -> d.nav.setEnd(false, s.id)
        is PickTarget.Place -> {
            d.store.setPlace(target.label, s.id)
            d.nav.back()
            if (d.nav.top == Screen.AddPlace) d.nav.back()
            Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(R.drawable.ic_arrow_left, { d.nav.back() }, C.text, "Back")
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
                    if (query.isEmpty()) Text(
                        when (target) {
                            PickTarget.From -> "From"
                            PickTarget.To -> "To"
                            is PickTarget.Place -> "Station for ${target.label}"
                        },
                        style = T.body.copy(color = C.muted),
                    )
                    BasicTextField(
                        query, { query = it },
                        singleLine = true,
                        textStyle = T.body,
                        cursorBrush = SolidColor(C.text),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) results.firstOrNull()?.let { pick(it.station) } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.press({ query = "" }).padding(4.dp).semantics { contentDescription = "Clear search" }) {
                        Ic(R.drawable.ic_x, C.muted, 16.dp)
                    }
                }
            }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.station.id }) { m ->
                val s = m.station
                val meta = listOfNotNull(m.alias, s.operatorLabel().takeIf { s.system != "dmrc" }).joinToString(" · ")
                StationRow(s, { pick(s) }, meta = meta.ifEmpty { null })
            }
            if (results.isEmpty()) item { Empty("No station matches “$query”") }
        }
    }
}
