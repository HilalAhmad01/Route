package app.metroline.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.metroline.R

val BookmarkKinds = listOf(
    "Home" to R.drawable.ic_house,
    "Work" to R.drawable.ic_briefcase,
    "College" to R.drawable.ic_graduation_cap,
    "Gym" to R.drawable.ic_dumbbell,
)

fun bookmarkIcon(label: String): Int? = BookmarkKinds.firstOrNull { it.first.equals(label, ignoreCase = true) }?.second

@Composable
fun BookmarkIcon(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 56.dp,
    selected: Boolean = false,
) = Box(
    Modifier
        .size(size)
        .border(1.dp, if (selected) C.text2 else C.hairline, CircleShape)
        .press(onClick, RoundedCornerShape(percent = 50), if (selected) C.raised else C.card, onLongClick = onLongClick)
        .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
) {
    val icon = bookmarkIcon(label)
    if (icon != null) Ic(icon, C.text, size * 0.4f)
    else Text(label.trim().take(1).uppercase(), style = T.bodyMedium.copy(fontSize = (size.value * 0.34f).sp))
}
