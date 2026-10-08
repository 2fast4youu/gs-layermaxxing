package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The neutral friend page: everything about one friendship without the island.
 *
 * In Messenger mode this is where nickname, quests, letters, topics and rules
 * live (in island mode the friend sheet on the map offers the same). It is the
 * "Hafenpost" look of the chat list, so it fits both modes. Opened from the
 * chat head.
 */
@Composable
internal fun FriendPage(
    friend: ApiClient.UserSummary,
    settings: ApiClient.FriendshipSettings?,
    label: String?,
    quests: List<ApiClient.Quest>,
    lettersCount: Int,
    openTopics: Int,
    epLine: String,
    onBack: () -> Unit,
    onLabel: (String?) -> Unit,
    onNewQuest: () -> Unit,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
    onLetters: () -> Unit,
    onTopics: () -> Unit,
    onRules: () -> Unit,
) {
    val p = Harbour.palette()
    var naming by remember { mutableStateOf(false) }
    if (naming) LabelDialog(friend.name, label, onDismiss = { naming = false }) { onLabel(it); naming = false }
    Column(Modifier.fillMaxSize().harbourPaper(p)) {
        Row(
            Modifier.fillMaxWidth().background(p.head).height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack)
                    .semantics { contentDescription = "Zurück zum Chat"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { AppIcon(R.drawable.ico_back, null, tint = p.onHead, size = 26.dp) }
            Text(friend.name, style = Harbour.Title.copy(fontSize = 19.sp), color = p.onHead, maxLines = 1)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IslandAvatar(friend.avatarEmoji, profileColor(friend.displayColor), 64.dp, p)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = Harbour.Title, color = p.ink)
                        Text(label ?: "Kein eigener Name", color = p.inkSoft, fontSize = 13.sp)
                    }
                }
            }
            item {
                PageRow(R.drawable.ico_edit, if (label == null) "Wie nennst du ${friend.name}?" else "Namen ändern", "Nur du siehst diesen Namen", p) { naming = true }
            }
            item { PageHead("Quests", p, action = "+ Neue Quest" to onNewQuest) }
            if (quests.isEmpty()) item {
                Text("Noch keine gemeinsame Quest – plant etwas Echtes zusammen.", color = p.inkSoft, fontSize = 13.sp)
            }
            items(quests.sortedBy { it.completedAt != null }.take(8), key = { "fq${it.id}" }) { q -> PageQuest(q, p, onQuestDone) }
            item { PageHead("Gemeinsam", p) }
            item { PageRow(R.drawable.ico_letter, "Briefe", if (lettersCount > 0) "$lettersCount Briefe" else "Noch keine Briefe", p, onLetters) }
            item { PageRow(R.drawable.ico_topics, "Themen", if (openTopics > 0) "$openTopics offen" else "Für das nächste Gespräch", p, onTopics) }
            item {
                PageRow(R.drawable.ico_rules, "Freundschaftsregeln",
                    settings?.let {
                        listOfNotNull("Briefe".takeIf { _ -> it.lettersEnabled }, "Chats".takeIf { _ -> it.chatsEnabled }, "Punkte".takeIf { _ -> it.epEnabled })
                            .joinToString(" · ").ifBlank { "Alles aus" }
                    } ?: "Nicht mehr aktiv",
                    p, onRules,
                )
            }
            item { Text(epLine, color = p.inkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun PageHead(text: String, p: HarbourPalette, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.ExtraBold, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        action?.let { (l, f) ->
            Text(
                l, color = p.seaDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = f).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PageRow(icon: Int, title: String, subtitle: String, p: HarbourPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(p.mine), contentAlignment = Alignment.Center) { AppIcon(icon, null, tint = p.onMine, size = 20.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = p.inkSoft, maxLines = 1)
        }
        AppIcon(R.drawable.ico_chevron, null, tint = p.inkSoft, size = 24.dp)
    }
}

@Composable
private fun PageQuest(q: ApiClient.Quest, p: HarbourPalette, onDone: (ApiClient.Quest, Boolean) -> Unit) {
    val done = q.completedAt != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (done) p.paperDeep else p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.questIcon(q.icon), AppIcons.questLabels[q.icon], tint = p.sea, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(q.title, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (done) "erledigt${q.completedByName?.let { " von $it" } ?: ""}" else "${q.points} Punkte", fontSize = 12.sp, color = if (done) p.inkSoft else p.gold)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (done) p.sea else p.paper).border(1.5.dp, p.sea, CircleShape)
                .clickable { onDone(q, !done) }
                .semantics { contentDescription = if (done) "Als offen markieren" else "Als erledigt markieren"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { if (done) AppIcon(R.drawable.ico_check, null, tint = Color.White, size = 16.dp) }
    }
}
