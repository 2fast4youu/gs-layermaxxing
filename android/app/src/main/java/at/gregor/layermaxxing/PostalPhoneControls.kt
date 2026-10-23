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

/** A bounded paper slip. The full backdrop dismisses; the paper consumes blank-space taps. */
@Composable
internal fun PostalSelector(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x550F0904)).clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss).testTag("post-menu-outside"))
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime).padding(top = 64.dp, bottom = 36.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 440.dp))
                .background(Color(0xFFF0D9A9)).border(2.dp, Color(0xFF392313))
                .clickable(onClickLabel = "Auswahl") { }.padding(12.dp).testTag("post-selector")) {
                // Fixed above the scroll area: dismissal stays reachable even at large font sizes.
                Box(Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    .border(2.dp, Color(0xFF392313))
                    .clickable(onClickLabel = "Auswahl schließen", onClick = onDismiss)
                    .semantics { role = Role.Button }.testTag("post-selector-close")
                    .padding(horizontal = 12.dp, vertical = 10.dp), Alignment.Center) {
                    Text("Schließen ×", fontFamily = Kit.Display, fontSize = 16.sp, color = Color(0xFF27180C))
                }
                val scroll = rememberScrollState()
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(scroll).testTag("post-selector-scroll"),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                if (scroll.maxValue > 0) Text("↕ Für weitere Einträge wischen", fontFamily = Kit.Display, fontSize = 12.sp, color = Color(0xFF27180C), modifier = Modifier.padding(top = 6.dp).testTag("post-selector-scroll-hint"))
            }
        }
    }
}
