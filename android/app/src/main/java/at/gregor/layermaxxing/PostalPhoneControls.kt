package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

/** Three real tray groups, up to three letters per tray on each time page.
 * The representative can change to any selected member without changing the registered geometry. */
internal val PostalTimePageKey = SemanticsPropertyKey<Int>("Actual postal time page")
internal val PostalVisibleMailIdsKey = SemanticsPropertyKey<List<Long>>("Displayed real postal mail IDs")
internal val PostalPageSlideKey = SemanticsPropertyKey<Float>("Postal page slide inside registered face")

internal object PostalMailGroups {
    fun faces(page: List<ApiClient.Message>, level: Int, selected: Long? = null): List<ApiClient.Message> =
        if (level != 3) page else page.chunked(3).map { group -> group.firstOrNull { it.id == selected } ?: group.first() }
}

/** Observe only taps that children did not use. Scrolling, links, buttons and drags retain ownership. */
internal fun Modifier.postalBackgroundTap(onTap: () -> Unit): Modifier = composedPostalTap(onTap)
private fun Modifier.composedPostalTap(onTap: () -> Unit): Modifier = this.composed {
    val latest = rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var used = down.isConsumed
            var moved = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                used = used || event.changes.any { it.isConsumed }
                moved = moved || event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
            } while (event.changes.any { it.pressed })
            if (!used && !moved) latest.value()
        }
    }
}

/** Filter sheets can opt into a quiet native menu; other paper dialogs keep their original style. */
@Composable
internal fun PostalSelector(onDismiss: () -> Unit, clean: Boolean = false, title: String = "", content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x550F0904)).clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss).testTag("post-menu-outside"))
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime).padding(top = 64.dp, bottom = 36.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
            val panel = if (clean) Modifier.background(Color(0xFFFFFCF6), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                else Modifier.background(Color(0xFFF0D9A9)).border(2.dp, Color(0xFF392313))
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, if (clean) 520.dp else 440.dp))
                .then(panel).clickable(onClickLabel = "Auswahl") { }.padding(12.dp).testTag("post-selector")) {
                if (clean) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = 21.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFF29251F),
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Box(Modifier.size(48.dp).clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss)
                        .semantics { role = Role.Button }.testTag("post-selector-close"), Alignment.Center) {
                        Text("×", fontSize = 27.sp, color = Color(0xFF655F56))
                    }
                } else Box(Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    .border(2.dp, Color(0xFF392313))
                    .clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss)
                    .semantics { role = Role.Button }.testTag("post-selector-close")
                    .padding(horizontal = 12.dp, vertical = 10.dp), Alignment.Center) {
                    Text("Schließen ×", fontFamily = Kit.Display, fontSize = 16.sp, color = Color(0xFF27180C))
                }
                val scroll = rememberScrollState()
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(scroll).testTag("post-selector-scroll"),
                    verticalArrangement = Arrangement.spacedBy(if (clean) 4.dp else 8.dp), content = content)
                if (scroll.maxValue > 0 && !clean) Text("↕ Für weitere Einträge wischen", fontFamily = Kit.Display, fontSize = 12.sp, color = Color(0xFF27180C), modifier = Modifier.padding(top = 6.dp).testTag("post-selector-scroll-hint"))
            }
        }
    }
}

@Composable
internal fun PostalFilterSearch(value: String, onValueChange: (String) -> Unit, label: String, tag: String) {
    androidx.compose.material3.OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = 16.sp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF29251F), unfocusedTextColor = Color(0xFF29251F), cursorColor = Color(0xFF5E594F),
            focusedBorderColor = Color(0xFF827868), unfocusedBorderColor = Color(0xFFD7D1C7),
            focusedLabelColor = Color(0xFF655F56), unfocusedLabelColor = Color(0xFF756E64),
            focusedContainerColor = Color(0xFFFFFCF6), unfocusedContainerColor = Color(0xFFFFFCF6)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag(tag))
}

/** Plain, generously spaced menu rows: no stamp border and no decorative paper. */
@Composable
internal fun PostalFilterRow(label: String, tag: String, value: String? = null, chosen: Boolean? = null,
                             slot: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .background(if (chosen == true) Color(0xFFEDE9E0) else Color.Transparent,
            androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        .clickable(onClickLabel = label, onClick = onClick)
        .semantics { role = if (chosen == null) Role.Button else Role.RadioButton; chosen?.let { selected = it }; slot?.let { set(SceneSlotKey, it) } }
        .testTag(tag).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 20.sp,
                color = Color(0xFF29251F), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            value?.takeIf { chosen == null }?.let { Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp,
                color = Color(0xFF756E64), modifier = Modifier.padding(top = 3.dp)) }
        }
        if (chosen != null && value != null) Text(value, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
            fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF756E64), modifier = Modifier.padding(start = 8.dp))
        Text(if (chosen == true) "✓" else if (chosen == false) "" else if (value != null) "⌄" else "›", fontSize = 20.sp, lineHeight = 24.sp,
            color = Color(0xFF756E64), modifier = Modifier.padding(start = 10.dp))
    }
}
