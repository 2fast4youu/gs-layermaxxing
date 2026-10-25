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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

/** Three real tray groups, up to three letters per tray on each time page.
 * The representative can change to any selected member without changing the registered geometry. */
internal val PostalTimePageKey = SemanticsPropertyKey<Int>("Actual postal time page")
internal val PostalVisibleMailIdsKey = SemanticsPropertyKey<List<Long>>("Displayed real postal mail IDs")
internal val PostalPageSlideKey = SemanticsPropertyKey<Float>("Postal page slide inside registered face")
internal val PostalMenuMaterialKey = SemanticsPropertyKey<String>("Actual painted postal menu material")

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

/** The same approved painted paper as the scene, without a synthetic white card or stamp frame. */
@Composable
internal fun Modifier.postalPaperSurface(strip: Boolean = false): Modifier {
    val paper = EnvelopeArt.of(LocalContext.current.resources).sheet
    return semantics { this[PostalMenuMaterialKey] = "e_post_0_lay_sheet" }.drawBehind {
        val target = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
        val sourceOffset = if (strip) IntOffset(0, (paper.height * .35f).toInt()) else IntOffset.Zero
        val sourceSize = if (strip) IntSize(paper.width, (paper.height * .16f).toInt()) else IntSize(paper.width, paper.height)
        for (k in 1..3) drawImage(paper, srcOffset = sourceOffset, srcSize = sourceSize,
            dstOffset = IntOffset(k, k * 2), dstSize = target, alpha = .10f, colorFilter = EnvelopeArt.shadow)
        drawImage(paper, srcOffset = sourceOffset, srcSize = sourceSize, dstSize = target, colorFilter = EnvelopeArt.sheetLight)
    }
}

/** Filter sheets keep their quiet layout but use the illustration's own paper and typography. */
@Composable
internal fun PostalSelector(onDismiss: () -> Unit, clean: Boolean = false, title: String = "", content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x550F0904)).clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss).testTag("post-menu-outside"))
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime).padding(top = 64.dp, bottom = 36.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
            val panel = if (clean) Modifier.postalPaperSurface()
                else Modifier.background(Color(0xFFF0D9A9)).border(2.dp, Color(0xFF392313))
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, if (clean) 520.dp else 440.dp))
                .then(panel).clickable(onClickLabel = "Auswahl") { }.padding(12.dp).testTag("post-selector")) {
                if (clean) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontFamily = Kit.Display, fontSize = 21.sp,
                        color = Color(0xFF382616),
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Box(Modifier.size(48.dp).clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss)
                        .semantics { role = Role.Button }.testTag("post-selector-close"), Alignment.Center) {
                        Text("×", fontFamily = Kit.Body, fontSize = 27.sp, color = Color(0xFF745637))
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
    androidx.compose.material3.OutlinedTextField(value, onValueChange, label = { Text(label, fontFamily = Kit.Body) }, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Kit.Body, fontSize = 16.sp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF382616), unfocusedTextColor = Color(0xFF382616), cursorColor = Color(0xFF745637),
            focusedBorderColor = Color(0xFF80613F), unfocusedBorderColor = Color(0xFFA88D66),
            focusedLabelColor = Color(0xFF745637), unfocusedLabelColor = Color(0xFF745637),
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag(tag))
}

/** Uncluttered ink rows on painted paper, without separate cards or thick stamp borders. */
@Composable
internal fun PostalFilterRow(label: String, tag: String, value: String? = null, chosen: Boolean? = null,
                             slot: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .background(if (chosen == true) Color(0x257B482A) else Color.Transparent,
            androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        .clickable(onClickLabel = label, onClick = onClick)
        .semantics { role = if (chosen == null) Role.Button else Role.RadioButton; contentDescription = if (value == null) label else "$label: $value"; chosen?.let { selected = it }; slot?.let { set(SceneSlotKey, it) } }
        .testTag(tag).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Kit.Body, fontSize = 16.sp, lineHeight = 20.sp,
                color = Color(0xFF382616), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            value?.takeIf { chosen == null }?.let { Text(it, fontFamily = Kit.Body, fontSize = 14.sp, lineHeight = 18.sp,
                color = Color(0xFF745637), modifier = Modifier.padding(top = 3.dp)) }
        }
        if (chosen != null && value != null) Text(value, fontFamily = Kit.Body,
            fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF745637), modifier = Modifier.padding(start = 8.dp))
        Text(if (chosen == true) "✓" else if (chosen == false) "" else if (value != null) "⌄" else "›", fontFamily = Kit.Body, fontSize = 20.sp, lineHeight = 24.sp,
            color = if (chosen == true) Color(0xFF7D3828) else Color(0xFF745637), modifier = Modifier.padding(start = 10.dp))
    }
}
