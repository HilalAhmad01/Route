package app.metroline

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import app.metroline.data.Network
import app.metroline.data.Place
import app.metroline.ui.bookmarkIcon
import java.util.concurrent.Executors

object Shortcuts {
    const val EXTRA_BOOKMARK = "app.metroline.BOOKMARK"
    private const val BG = 0xFF1D1D20.toInt()
    private const val FG = 0xFFEDEDED.toInt()

    private val worker = Executors.newSingleThreadExecutor()

    fun sync(context: Context, net: Network, places: List<Place>) {
        val app = context.applicationContext
        val list = places.toList()
        val signature = "v1|" + list.joinToString("|") { "${it.label}>${it.stationId}" }
        worker.execute {
            // A launcher that rejects a shortcut must never take the app down: this runs on a background thread.
            runCatching {
                val prefs = app.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
                if (prefs.getString("published", null) == signature) return@runCatching
                publish(app, net, list)
                prefs.edit().putString("published", signature).apply()
            }
        }
    }

    private fun publish(context: Context, net: Network, places: List<Place>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
        val shortcuts = places.take(max).mapIndexed { i, p ->
            ShortcutInfoCompat.Builder(context, "bookmark:${p.label.lowercase()}")
                .setShortLabel("From ${p.label}")
                .setLongLabel("From ${p.label} · ${net.station(p.stationId).name}")
                .setIcon(icon(context, p.label))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_BOOKMARK, p.label),
                )
                .setRank(i)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun icon(context: Context, label: String): IconCompat {
        val size = 216
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)
        val res = bookmarkIcon(label)
        if (res != null) {
            val d = ContextCompat.getDrawable(context, res)!!.mutate()
            d.setTint(FG)
            val glyph = (size * 0.36f).toInt()
            val o = (size - glyph) / 2
            d.setBounds(o, o, o + glyph, o + glyph)
            d.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = FG
                textSize = size * 0.3f
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.plex_sans)
            }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(label.trim().take(1).uppercase(), size / 2f, y, paint)
        }
        return IconCompat.createWithAdaptiveBitmap(bmp)
    }
}
