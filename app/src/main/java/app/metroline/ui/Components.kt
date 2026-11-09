package app.metroline.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(S.radiusRow),
    rest: Color = Color.Transparent,
    down: Color = C.pressed,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) down else rest, quick(), label = "press")
    return this
        .scale(if (pressed) 0.98f else 1f)
        .clip(shape)
        .background(bg)
        .combinedClickable(source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
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

@Composable
fun Rails(lines: List<Line>, height: Dp = 20.dp, modifier: Modifier = Modifier) = Row(
    modifier.height(height),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
) {
    for (l in lines) Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(l.c()))
}

@Composable
fun LineDot(line: Line, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(line.c()))

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
        IconButton(R.drawable.ic_arrow_left, onBack, C.text, "Back")
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = T.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = T.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }

@Composable
fun IconButton(@DrawableRes icon: Int, onClick: () -> Unit, tint: Color = C.text2, description: String) = Box(
    Modifier.size(40.dp).press(onClick).semantics {
        contentDescription = description
        role = Role.Button
    },
    contentAlignment = Alignment.Center,
) { Ic(icon, tint) }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, style = T.label, modifier = modifier.padding(start = S.page, end = S.page, top = 24.dp, bottom = 8.dp))

@Composable
fun SectionLabel(text: String, action: String, onAction: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(start = S.page, end = 8.dp, top = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, style = T.label, modifier = Modifier.weight(1f))
    Text(
        action,
        style = T.label.copy(color = C.text2),
        modifier = Modifier.press(onAction).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

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

@Composable
fun Toggle(on: Boolean) {
    val track by animateColorAsState(if (on) C.text else C.raised, quick(), label = "track")
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 18.dp else 2.dp, quick(), label = "thumb")
    Box(
        Modifier
            .width(40.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(track)
            .border(1.dp, C.hairline, RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .padding(start = x, top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) C.onPrimary else C.muted),
        )
    }
}

/** A short message over the screen, such as the one shown the first time a trip is saved. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) = Text(
    text,
    style = T.secondary.copy(color = C.onPrimary, fontWeight = FontWeight.Medium),
    modifier = modifier
        .clip(RoundedCornerShape(S.radiusRow))
        .background(C.text)
        .semantics { liveRegion = LiveRegionMode.Polite }
        .padding(horizontal = 14.dp, vertical = 9.dp),
)
