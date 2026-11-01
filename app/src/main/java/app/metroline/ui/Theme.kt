package app.metroline.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.metroline.R

object C {
    val bg = Color(0xFF0C0C0D)
    val card = Color(0xFF141416)
    val raised = Color(0xFF1D1D20)
    val pressed = Color(0xFF252528)
    val hairline = Color(0x14FFFFFF)
    val text = Color(0xFFEDEDED)
    val text2 = Color(0xFFA1A1A6)
    val muted = Color(0xFF85858B)
    val onPrimary = Color(0xFF111112)
    val negative = Color(0xFFE5484D)
}

@OptIn(ExperimentalTextApi::class)
private val sansVar = FontFamily(
    Font(R.font.plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
private val mono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

object T {
    val title = TextStyle(fontFamily = sansVar, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, color = C.text)
    val heading = TextStyle(fontFamily = sansVar, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = C.text)
    val body = TextStyle(fontFamily = sansVar, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = C.text)
    val bodyMedium = body.copy(fontWeight = FontWeight.Medium)
    val secondary = TextStyle(fontFamily = sansVar, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = C.text2)
    val label = TextStyle(fontFamily = sansVar, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = C.muted, letterSpacing = 0.1.sp)
    val meta = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = C.muted)
    val figure = TextStyle(fontFamily = mono, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = C.text)
    val figureSmall = TextStyle(fontFamily = mono, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = C.text)
}

object S {
    val page = 20.dp
    val row = 52.dp
    val radiusCard = 16.dp
    val radiusRow = 10.dp
}

val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
fun <T> quick() = tween<T>(160, easing = Ease)
fun <T> enter() = tween<T>(320, easing = Ease)
