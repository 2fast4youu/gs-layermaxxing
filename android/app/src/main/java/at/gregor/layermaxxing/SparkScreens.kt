package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Freundesfunke: a short, anonymous, one-way kind word.
 *
 * The receiving side of this screen has no sender, no time, no reply, no
 * reaction, no thanks and no points — not because they are hidden here, but
 * because the server never sends them. The only two tools a recipient has are
 * muting whoever sent one spark and reporting it; neither names anybody.
 *
 * The material is warm paper and ink rather than the grey rounded tiles of the
 * rest of the system: a spark is a note, not a record.
 */

private val PAPER = Color(0xFFF6ECD6)
private val PAPER_DEEP = Color(0xFFEADFC2)
private val INK = Color(0xFF3B3122)
private val INK_SOFT = Color(0xFF6C5F49)

@Composable
internal fun SparkRoom(
    inbox: List<SparkItem>,
    sent: List<SparkSent>,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    onBack: () -> Unit,
    onCompose: () -> Unit,
) {
    var infoOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack)
                    .semantics { contentDescription = "Zurück zu Chats"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text("←", fontSize = 22.sp) }
            Text("Funken", Modifier.weight(1f).padding(start = 6.dp), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { infoOpen = !infoOpen }
                    .semantics { contentDescription = "Was ein Funke ist"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text("ⓘ", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Technical and abuse details live on this quiet surface, never in the flow.
        if (infoOpen) Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(SPARK_INFO, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SPARK_ABUSE_INFO, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (inbox.isEmpty() && sent.isEmpty()) item {
                Text(
                    "Noch keine Funken.", Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(inbox, key = { "spark-in-${it.id}" }) { spark ->
                ReceivedSpark(
                    spark = spark,
                    onOpen = { act { api.openSpark(token, spark.id) } },
                    onMute = { act { api.muteSparkSender(token, spark.id) } },
                    onReport = { act { api.reportSpark(token, spark.id) } },
                )
            }
            if (sent.isNotEmpty()) {
                item {
                    Text(
                        "Von dir gesendet", Modifier.padding(top = 10.dp),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
                items(sent, key = { "spark-out-${it.id}" }) { spark -> SentSpark(spark) }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
        Button(
            onClick = onCompose,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text("✨ Funke senden", fontWeight = FontWeight.Bold) }
    }
}

/**
 * One received spark, on paper.
 *
 * Closed it says nothing but [Sparks.COVER]; opening it is the only state change
 * that exists on this side and it happens exactly once. There is deliberately no
 * reply, no reaction and no thanks: a spark ends where it lands.
 */
@Composable
private fun ReceivedSpark(
    spark: SparkItem,
    onOpen: () -> Unit,
    onMute: () -> Unit,
    onReport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp),
        color = if (spark.opened) PAPER_DEEP else PAPER,
        contentColor = INK,
        shadowElevation = if (spark.opened) 0.dp else 3.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Sparks.COVER, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Box {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { menu = true }
                            .semantics { contentDescription = "Funke verwalten"; role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮", fontSize = 20.sp, color = INK_SOFT) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Von dieser Person keine Funken mehr") },
                            onClick = { menu = false; onMute() },
                        )
                        DropdownMenuItem(
                            text = { Text("Melden", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onReport() },
                        )
                    }
                }
            }
            if (spark.opened) Text(
                spark.text ?: "Dieser Funke lässt sich auf diesem Gerät nicht mehr lesen.",
                fontSize = 17.sp,
                fontStyle = if (spark.text == null) FontStyle.Italic else FontStyle.Normal,
            ) else Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = "Funke öffnen"; role = Role.Button }
                    .background(Color(0x22000000)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Öffnen", fontWeight = FontWeight.SemiBold, color = INK)
            }
        }
    }
}

/** One sent spark: the recipient, and the single status bit. Never a time. */
@Composable
private fun SentSpark(spark: SparkSent) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                contentDescription = "Funke an ${spark.recipientName}: ${Sparks.statusLabel(spark.opened)}"
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 18.sp, modifier = Modifier.width(32.dp))
        Text(spark.recipientName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(
            Sparks.statusLabel(spark.opened), fontSize = 13.sp,
            color = if (spark.opened) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Choosing exactly one friend and writing one short line.
 *
 * Only accepted, unblocked friendships appear here; the server checks the same
 * rule again and is the authority. Nothing in this sheet promises a reply.
 */
@Composable
internal fun SparkComposer(
    friends: List<ApiClient.UserSummary>,
    settings: List<ApiClient.FriendshipSettings>,
    onClose: () -> Unit,
    onSend: (Long, String) -> Unit,
) {
    // An active settings row is what makes a friendship confirmed and unblocked.
    val eligible = remember(friends, settings) {
        val active = settings.map { it.friendId }.toSet()
        friends.filter { it.id in active }.sortedBy { it.name.lowercase() }
    }
    var selected by remember { mutableStateOf<Long?>(null) }
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✨ Freundesfunke", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (eligible.isEmpty()) {
            Text(
                "Funken brauchen eine bestätigte Freundschaft.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClose) { Text("Schließen") }
            return@Column
        }
        Text("An wen?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        eligible.forEach { friend ->
            val chosen = selected == friend.id
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { selected = friend.id }
                    .semantics {
                        contentDescription = if (chosen) "${friend.name}, ausgewählt" else friend.name
                        role = Role.Button
                    }
                    .background(if (chosen) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(friend.avatarEmoji, fontSize = 20.sp, modifier = Modifier.width(34.dp))
                Text(friend.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (chosen) Text("✓", fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider()
        OutlinedTextField(
            value = text,
            onValueChange = { text = Sparks.clampText(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ein guter Satz") },
            maxLines = 3,
            supportingText = { Text("${text.length}/${Sparks.MAX_TEXT}", fontSize = 11.sp) },
        )
        Text(SPARK_PROMISE, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { selected?.let { onSend(it, text) } },
                enabled = selected != null && text.isBlank().not(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Senden") }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abbrechen") }
        }
    }
}

/** One line, not a chapter: what the recipient will and will not see. */
internal const val SPARK_PROMISE =
    "Anonym: der Empfänger sieht nur den Satz. Du siehst nur, ob er geöffnet wurde."

internal const val SPARK_INFO =
    "Ein Funke ist einseitig: keine Antwort, keine Reaktion, keine Ebenen-Punkte. " +
        "Du siehst nie, von wem einer kommt."

internal const val SPARK_ABUSE_INFO =
    "Der Server kennt den Absender intern für Missbrauchsschutz und gibt ihn in keiner Antwort an dich heraus. " +
        "„Keine Funken mehr“ blendet künftige Funken derselben Person aus, ohne sie dir zu nennen."
