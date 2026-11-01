package app.metroline.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.metroline.R
import app.metroline.data.Line
import app.metroline.data.Station

fun Line.c() = Color(color)

@Composable
fun Ic(@DrawableRes id: Int, tint: Color = C.text2, size: Dp = 18.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))

/** Surface-step press feedback with a tiny scale down; no ripples, no glow. */
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(S.radiusRow),
    rest: Color = Color.Transparent,
    down: Color = C.pressed,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) down else rest, quick(), label = "press")
    return this
        .scale(if (pressed) 0.98f else 1f)
        .clip(shape)
        .background(bg)
        .clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(C.hairline))

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(S.radiusCard))
        .background(C.card)
        .border(1.dp, C.hairline, RoundedCornerShape(S.radiusCard)),
    content = content,
)

/** The signature: a thin rail in each line's colour, like platform signage. */
@Composable
fun Rails(lines: List<Line>, height: Dp = 20.dp, modifier: Modifier = Modifier) = Row(
    modifier.height(height),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
) {
    for (l in lines) Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(l.c()))
}

@Composable
fun LineDot(line: Line, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(line.c()))

/** Hollow ring used for interchange stations on rails and the map. */
@Composable
fun Ring(color: Color, size: Dp = 12.dp, fill: Color = C.bg) = Canvas(Modifier.size(size)) {
    val w = 2.dp.toPx()
    drawCircle(fill, radius = this.size.minDimension / 2 - w / 2)
    drawCircle(color, radius = this.size.minDimension / 2 - w / 2, style = Stroke(w))
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) =
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).press(onBack, RoundedCornerShape(S.radiusRow)), contentAlignment = Alignment.Center) {
            Ic(R.drawable.ic_arrow_left, C.text)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = T.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = T.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }

@Composable
fun IconButton(@DrawableRes icon: Int, onClick: () -> Unit, tint: Color = C.text2) =
    Box(Modifier.size(40.dp).press(onClick), contentAlignment = Alignment.Center) { Ic(icon, tint) }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, style = T.label, modifier = modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 8.dp))

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = Box(
    modifier
        .fillMaxWidth()
        .height(48.dp)
        .press(onClick, RoundedCornerShape(S.radiusRow), if (enabled) C.text else C.raised, Color(0xFFD6D6D6), enabled),
    contentAlignment = Alignment.Center,
) { Text(text, style = T.bodyMedium.copy(color = if (enabled) C.onPrimary else C.muted)) }

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = Box(
    modifier
        .height(48.dp)
        .border(1.dp, C.hairline, RoundedCornerShape(S.radiusRow))
        .press(onClick, RoundedCornerShape(S.radiusRow), C.raised),
    contentAlignment = Alignment.Center,
) { Text(text, style = T.bodyMedium) }

/** Segmented control: the selected option is one surface step up with a hairline. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) = Row(
    modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(S.radiusRow))
        .background(C.card)
        .border(1.dp, C.hairline, RoundedCornerShape(S.radiusRow))
        .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    options.forEachIndexed { i, o ->
        val on = i == selected
        val bg by animateColorAsState(if (on) C.raised else Color.Transparent, quick(), label = "seg")
        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .then(if (on) Modifier.border(1.dp, C.hairline, RoundedCornerShape(6.dp)) else Modifier)
                .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
            contentAlignment = Alignment.Center,
        ) { Text(o, style = T.secondary.copy(color = if (on) C.text else C.muted), maxLines = 1) }
    }
}

/** A station row: rails for its lines, name, and an optional trailing slot. */
@Composable
fun StationRow(
    station: Station,
    onClick: () -> Unit,
    meta: String? = null,
    trailing: @Composable BoxScope.() -> Unit = {},
) = Row(
    Modifier.fillMaxWidth().heightIn(min = S.row).press(onClick).padding(horizontal = S.page),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.width(20.dp)) { Rails(station.lines) }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
        Text(station.name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = T.meta)
    }
    Box(content = trailing)
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(label, style = T.label)
    Spacer(Modifier.height(4.dp))
    Text(value, style = T.figureSmall)
}

@Composable
fun Empty(text: String) = Text(text, style = T.secondary, modifier = Modifier.padding(horizontal = S.page, vertical = 12.dp))
