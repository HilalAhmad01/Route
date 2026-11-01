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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import kotlin.math.cos

/**
 * The network drawn from real station coordinates (no map tiles, nothing downloaded).
 * Pinch to zoom, drag to pan, tap a station to plan from or to it.
 */
@Composable
fun MapScreen() {
    val d = deps()
    val net = d.net
    val density = LocalDensity.current
    val ctx = LocalContext.current

    // Equirectangular projection around Delhi, in km.
    val lat0 = 28.62
    val kx = 111.32 * cos(Math.toRadians(lat0))
    val ky = 110.57
    fun px(s: Station) = ((s.lon - 77.2) * kx).toFloat()
    fun py(s: Station) = (-(s.lat - lat0) * ky).toFloat()

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) } // pixels per km; 0 = not fitted yet
    var pan by remember { mutableStateOf(Offset.Zero) } // screen position of projection origin
    var selected by remember { mutableStateOf<Station?>(null) }

    fun fit() {
        // Frame the dense metro area; Namo Bharat runs far east and is reached by panning.
        val core = net.stations.filter { it.system != "rrts" }
        val xs = core.map(::px)
        val ys = core.map(::py)
        val w = xs.max() - xs.min()
        val h = ys.max() - ys.min()
        scale = minOf(size.width / w, size.height / h) * 0.92f
        pan = Offset(size.width / 2f - (xs.max() + xs.min()) / 2 * scale, size.height / 2f - (ys.max() + ys.min()) / 2 * scale)
    }

    fun screen(s: Station) = Offset(pan.x + px(s) * scale, pan.y + py(s) * scale)

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C.text2.toArgb()
            textSize = with(density) { 11.dp.toPx() }
            typeface = ResourcesCompat.getFont(ctx, R.font.plex_sans)
        }
    }

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
                        val hit = net.stations.minByOrNull { (screen(it) - tap).getDistance() }
                        selected = hit?.takeIf { (screen(it) - tap).getDistance() < 28.dp.toPx() }
                    }
                },
        ) {
            if (scale == 0f) return@Canvas
            val lineW = (scale * 0.12f).coerceIn(2.dp.toPx(), 5.dp.toPx())
            for (p in net.patterns) {
                val path = Path()
                p.stops.forEachIndexed { i, s ->
                    val o = screen(net.stations[s])
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, p.line.c(), style = Stroke(lineW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val showAllLabels = scale > 40f
            val showStops = scale > 14f
            for (s in net.stations) {
                val o = screen(s)
                if (o.x < -50 || o.y < -50 || o.x > size.width + 50 || o.y > size.height + 50) continue
                val inter = s.isInterchange || net.walksFrom[s.index].isNotEmpty()
                if (inter) {
                    drawCircle(C.bg, lineW * 1.3f, o)
                    drawCircle(C.text, lineW * 1.3f, o, style = Stroke(lineW * 0.55f))
                } else if (showStops) {
                    drawCircle(C.bg, lineW * 0.35f, o)
                }
                if (showAllLabels || (inter && scale > 22f) || s == selected) {
                    drawIntoCanvas { it.nativeCanvas.drawText(s.name, o.x + lineW * 2f, o.y + lineW * 0.6f, labelPaint) }
                }
            }
            selected?.let {
                val o = screen(it)
                drawCircle(C.text, lineW * 2.2f, o, style = Stroke(2.dp.toPx()))
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
                        Text(s.name, style = T.bodyMedium)
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
