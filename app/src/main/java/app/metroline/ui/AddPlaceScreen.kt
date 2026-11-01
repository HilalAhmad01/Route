package app.metroline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun AddPlaceScreen() {
    val d = deps()
    var name by remember { mutableStateOf("") }
    val taken = d.store.places.map { it.label.lowercase() }.toSet()
    val suggestions = BookmarkKinds.map { it.first }.filter { it.lowercase() !in taken }
    fun next() {
        if (name.isNotBlank()) d.nav.push(Screen.Pick(PickTarget.Place(name.trim())))
    }

    TopBar("Add bookmark", { d.nav.back() })
    Hairline()
    Column(Modifier.fillMaxSize().padding(horizontal = S.page)) {
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (s in suggestions) BookmarkIcon(s, { name = s }, selected = name == s)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Name", style = T.label)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(S.radiusRow))
                .background(C.raised)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (name.isEmpty()) Text("Or type your own", style = T.body.copy(color = C.muted))
            BasicTextField(
                name, { name = it.take(24) },
                singleLine = true,
                textStyle = T.body,
                cursorBrush = SolidColor(C.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { next() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        d.store.place(name.trim())?.let {
            Spacer(Modifier.height(8.dp))
            Text("Replaces the station of your ${it.label} bookmark", style = T.meta)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Choose station", ::next, enabled = name.isNotBlank())
    }
}
