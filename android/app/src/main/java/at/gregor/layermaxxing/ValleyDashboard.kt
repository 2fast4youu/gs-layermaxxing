package at.gregor.layermaxxing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Hof & Post" to onHome, "Themen" to onTopics, "Chat" to onChat).forEach { (label, action) ->
                    FilledTonalButton(onClick = action, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            guide()
        }
    }
}
