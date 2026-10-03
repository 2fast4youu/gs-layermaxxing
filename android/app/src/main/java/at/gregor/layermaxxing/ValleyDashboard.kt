package at.gregor.layermaxxing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VillageDirectory(onOpen: (ValleyDestination) -> Unit, onHome: () -> Unit, onFriend: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { visible = true }, modifier = Modifier.fillMaxWidth()) { Text("Dorf-Orte · Gebäude antippen") }
    if (visible) ModalBottomSheet(onDismissRequest = { visible = false }) {
        LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("Alles hat seinen Ort", style = MaterialTheme.typography.titleLarge) }
            item { ExtensionEntry("🏠 Dein Hof", "Poststelle, Schatzkammer und Ausbau") { visible = false; onHome() } }
            item { ExtensionEntry("🏡 Freund besuchen", "Geteiltes ansehen, kein Zugriff auf dessen Gerät") { visible = false; onFriend() } }
            items(ValleyDestination.entries, key = { it.keyName() }) { d -> ExtensionEntry(d.label, d.detail) { visible = false; onOpen(d) } }
        }
    }
}
private fun ValleyDestination.keyName() = name

/** Screen-fixed controls: never drift off-screen when the illustrated world is panned. */
@Composable
internal fun ValleyDashboard(
    post: PostStatus, openTopics: Int,
    onOverview: () -> Unit, onHomeFocus: () -> Unit, onFriendFocus: () -> Unit,
    onHome: () -> Unit, onChat: () -> Unit, onTopics: () -> Unit,
    guide: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE172C29), contentColor = Color(0xFFF4F4E7),
        tonalElevation = 6.dp, shadowElevation = 8.dp,
    ) {
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DEIN TAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBDD5BB))
            Text(
                "${post.total} Briefe · ${post.urgent} warten auf dich · $openTopics offene Themen",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Übersicht" to onOverview, "Mein Hof" to onHomeFocus, "Freund" to onFriendFocus).forEach { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFF4F4E7))
                    }
                }
            }
            guide()
        }
    }
}
