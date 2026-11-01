package app.metroline.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.Line
import app.metroline.data.Station

private val groups = listOf(
    "Delhi Metro" to setOf("dmrc", "dmrc_airport"),
    "Gurugram" to setOf("rmgl"),
    "Noida" to setOf("nmrc"),
    "Namo Bharat" to setOf("rrts"),
)

@Composable
fun LinesScreen() {
    val d = deps()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Lines", style = T.title, modifier = Modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 4.dp)) }
        for ((label, systems) in groups) {
            item(key = label) { SectionLabel(label) }
            items(d.net.lines.filter { it.system in systems }, key = { it.id }) { line -> LineRow(line) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LineRow(line: Line) {
    val d = deps()
    val sections = remember(line) { d.net.stationsOf(line) }
    val main = sections.first()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).press({ d.nav.push(Screen.LineDetail(line.id)) }).padding(horizontal = S.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rails(listOf(line), 32.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(line.name, style = T.bodyMedium)
            Text("${main.first().name} – ${main.last().name}", style = T.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text("${uniqueCount(sections)}", style = T.meta)
        Ic(R.drawable.ic_chevron_right, C.muted, 16.dp, Modifier.padding(start = 8.dp))
    }
}

private fun uniqueCount(sections: List<List<Station>>) = sections.flatten().distinct().size

@Composable
fun LineScreen(lineId: String) {
    val d = deps()
    val line = d.net.line(lineId)
    val sections = remember(line) { d.net.stationsOf(line) }
    TopBar(line.name, { d.nav.back() }, subtitle = "${uniqueCount(sections)} stations")
    Hairline()
    LazyColumn(Modifier.fillMaxSize()) {
        sections.forEachIndexed { k, section ->
            if (k > 0) item(key = "branch$k") {
                SectionLabel(if (line.id == "rapid" && k == 1) "Sector 55-56 branch" else "Branch")
            }
            items(section.size, key = { "$k-$it" }) { i ->
                LineStationRow(line, section[i], first = i == 0, last = i == section.lastIndex)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A station on a line: one continuous rail in the line colour, rings at interchanges. */
@Composable
private fun LineStationRow(line: Line, s: Station, first: Boolean, last: Boolean) {
    val d = deps()
    val color = line.c()
    val others = s.lines.filter { it != line }
    val walks = d.net.walksFrom[s.index].map { d.net.stations[it.b] }
    val interchange = others.isNotEmpty() || walks.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 48.dp)
            .press({ d.nav.push(Screen.StationDetail(s.id)) })
            .padding(end = S.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(S.page + 24.dp).fillMaxHeight().drawBehind {
                val x = S.page.toPx() + 6.dp.toPx()
                val y = size.height / 2
                val w = 4.dp.toPx()
                drawLine(color, Offset(x, if (first) y else 0f), Offset(x, if (last) y else size.height), w)
                if (interchange) {
                    drawCircle(C.bg, 7.dp.toPx(), Offset(x, y))
                    drawCircle(C.text, 6.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                } else {
                    drawCircle(C.bg, 2.dp.toPx(), Offset(x, y))
                }
            },
        )
        Text(
            s.name,
            style = if (interchange) T.bodyMedium else T.body,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (o in others) Box(Modifier.padding(start = 6.dp)) { LineDot(o) }
            for (w in walks) for (l in w.lines) Box(Modifier.padding(start = 6.dp)) { Ring(l.c(), 10.dp) }
        }
    }
}
