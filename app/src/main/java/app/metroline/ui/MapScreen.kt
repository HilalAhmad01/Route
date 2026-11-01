package app.metroline.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import app.metroline.data.Network
import kotlin.math.cos
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import app.metroline.R
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.metroline.data.Station

@Composable
fun MapScreen() {
    val d = deps()
    val net = d.net
    val density = LocalDensity.current
    val ctx = LocalContext.current

    val geo = remember(net) { MapGeometry(net) }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Station?>(null) }

    fun fit() {
        val c = net.station("dmrc:Rajiv Chowk").index
        scale = size.width / 24f
        pan = Offset(size.width / 2f - geo.x[c] * scale, size.height * 0.45f - geo.y[c] * scale)
    }

    fun screen(s: Station) = Offset(pan.x + geo.x[s.index] * scale, pan.y + geo.y[s.index] * scale)

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C.text2.toArgb()
            textSize = with(density) { 11.dp.toPx() }
            typeface = ResourcesCompat.getFont(ctx, R.font.plex_sans)
        }
    }
    val labelWidth = remember(labelPaint) { FloatArray(net.stations.size) { labelPaint.measureText(net.stations[it].name) } }
    val labelBg = remember { C.bg.copy(alpha = 0.85f) }
    val labelOrder = remember { net.stations.sortedByDescending { geo.interchange[it.index] }.map { it.index }.toIntArray() }
    val placed = remember { FloatArray(net.stations.size * 4) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (scale == 0f && it.width > 0) fit()
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, p, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(2f, 400f)
                        val z = ns / scale
                        pan = Offset(centroid.x - (centroid.x - pan.x) * z, centroid.y - (centroid.y - pan.y) * z) + p
                        scale = ns
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val hit = net.stations.minByOrNull { (screen(it) - tap).getDistanceSquared() }
                        selected = hit?.takeIf { (screen(it) - tap).getDistance() < 28.dp.toPx() }
                    }
                },
        ) {
            if (scale == 0f) return@Canvas
            val w = size.width
            val h = size.height
            val px = pan.x
            val py = pan.y
            val k = scale
            val lineW = (k * 0.12f).coerceIn(2.dp.toPx(), 5.dp.toPx())
            val margin = 50f
            fun sx(i: Int) = px + geo.x[i] * k
            fun sy(i: Int) = py + geo.y[i] * k

            for (seg in 0 until geo.segmentCount) {
                val a = geo.segA[seg]
                val b = geo.segB[seg]
                val x1 = sx(a); val y1 = sy(a); val x2 = sx(b); val y2 = sy(b)
                if (maxOf(x1, x2) < -margin || minOf(x1, x2) > w + margin || maxOf(y1, y2) < -margin || minOf(y1, y2) > h + margin) continue
                drawLine(geo.segColor[seg], Offset(x1, y1), Offset(x2, y2), lineW, StrokeCap.Round)
            }

            val showAllLabels = k > 90f
            val showStops = k > 70f
            fun visible(x: Float, y: Float) = x > -margin && y > -margin && x < w + margin && y < h + margin
            for (i in net.stations.indices) {
                val x = sx(i); val y = sy(i)
                if (!visible(x, y)) continue
                if (geo.interchange[i]) {
                    drawCircle(C.bg, lineW * 1.3f, Offset(x, y))
                    drawCircle(C.text, lineW * 1.3f, Offset(x, y), style = Stroke(lineW * 0.55f))
                } else if (showStops) {
                    drawCircle(C.bg, lineW * 0.35f, Offset(x, y))
                }
            }

            if (showAllLabels || k > 40f || selected != null) {
                val pad = 4.dp.toPx()
                val text = labelPaint.textSize
                var count = 0
                val canvas = drawContext.canvas.nativeCanvas
                fun tryLabel(i: Int, force: Boolean) {
                    val x0 = sx(i); val y0 = sy(i)
                    if (!visible(x0, y0)) return
                    val x = x0 + lineW * 2f
                    val y = y0 + text * 0.35f
                    val l = x - pad; val t = y - text - pad; val r = x + labelWidth[i] + pad; val b = y + pad
                    if (!force) for (c in 0 until count) {
                        val o = c * 4
                        if (l < placed[o + 2] && r > placed[o] && t < placed[o + 3] && b > placed[o + 1]) return
                    }
                    if (count < net.stations.size) {
                        val o = count * 4
                        placed[o] = l; placed[o + 1] = t; placed[o + 2] = r; placed[o + 3] = b
                        count++
                    }
                    drawRoundRect(labelBg, Offset(l + pad / 2, t + pad / 2), Size(r - l - pad, b - t - pad), CornerRadius(pad))
                    canvas.drawText(net.stations[i].name, x, y, labelPaint)
                }
                selected?.let { tryLabel(it.index, force = true) }
                for (i in labelOrder) {
                    if (net.stations[i] == selected) continue
                    if (showAllLabels || (geo.interchange[i] && k > 40f)) tryLabel(i, force = false)
                }
            }
            selected?.let {
                drawCircle(C.text, lineW * 2.2f, Offset(sx(it.index), sy(it.index)), style = Stroke(2.dp.toPx()))
            }
        }

        selected?.let { s ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(S.radiusCard))
                    .background(C.card)
                    .border(1.dp, C.hairline, RoundedCornerShape(S.radiusCard))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Rails(s.lines, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).press({ d.nav.push(Screen.StationDetail(s.id)) })) {
                        Text(s.displayName, style = T.bodyMedium)
                        Text(s.lines.joinToString(" · ") { it.name }, style = T.secondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    SecondaryButton("From here", { d.nav.setEnd(true, s.id) }, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { PrimaryButton("To here", { d.nav.setEnd(false, s.id) }) }
                }
            }
        }
    }
}

private class MapGeometry(net: Network) {
    val x: FloatArray
    val y: FloatArray
    val interchange: BooleanArray
    val segA: IntArray
    val segB: IntArray
    val segColor: Array<Color>
    val segmentCount: Int

    init {
        val lat0 = 28.62
        val kx = 111.32 * cos(Math.toRadians(lat0))
        val ky = 110.57
        x = FloatArray(net.stations.size) { ((net.stations[it].lon - 77.2) * kx).toFloat() }
        y = FloatArray(net.stations.size) { (-(net.stations[it].lat - lat0) * ky).toFloat() }
        interchange = BooleanArray(net.stations.size) { net.stations[it].isInterchange || net.walksFrom[it].isNotEmpty() }
        val seen = HashSet<Long>()
        val a = ArrayList<Int>(); val b = ArrayList<Int>(); val c = ArrayList<Color>()
        for (p in net.patterns) for (i in 0 until p.stops.size - 1) {
            val u = minOf(p.stops[i], p.stops[i + 1]); val v = maxOf(p.stops[i], p.stops[i + 1])
            val key = (net.lines.indexOf(p.line).toLong() shl 40) or (u.toLong() shl 20) or v.toLong()
            if (seen.add(key)) { a += u; b += v; c += p.line.c() }
        }
        segA = a.toIntArray(); segB = b.toIntArray(); segColor = c.toTypedArray(); segmentCount = segA.size
    }
}
