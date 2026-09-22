package at.gregor.layermaxxing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EpScreen(
    token: String,
    api: ApiClient,
    settings: List<ApiClient.FriendshipSettings>,
    overview: ApiClient.EpOverview?,
    draft: EpPrompt?,
    onDraftConsumed: () -> Unit,
    act: ((suspend () -> Unit) -> Unit),
) {
    val suggestedFriend = draft?.friendId
    val suggestedLetter = draft?.letterId
    val enabled = settings.filter { it.epEnabled }
    var beneficiary by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val linkedLetter = suggestedLetter.takeIf { beneficiary == suggestedFriend }
    LaunchedEffect(suggestedFriend, suggestedLetter) {
        if (suggestedFriend != null) beneficiary = suggestedFriend
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { SocialTitle("EP") }
        item { Text("Ebenen-Punkte (wie Experience Points, nur auf mehreren Ebenen)") }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(overview?.levelName ?: "Ebenen-Neuling", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Gegeben: ${overview?.given ?: 0} EP · Erhalten: ${overview?.received ?: 0} EP")
            }
        } }
        if (enabled.isEmpty()) item { SocialInfo("EP wird erst verfügbar, nachdem beide einer Freundschaftseinstellung mit aktivierten EP zugestimmt haben.") }
        else item { Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("EP vorschlagen", fontWeight = FontWeight.Bold)
            Text("Empfänger (nicht du selbst)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                enabled.forEach { value ->
                    if (beneficiary == value.friendId) Button(onClick = {}) { Text(value.friendName) }
                    else androidx.compose.material3.OutlinedButton(onClick = { beneficiary = value.friendId }) { Text(value.friendName) }
                }
            }
            Text(formatEpPoints(EP_PROPOSAL_POINTS), fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Titel (erforderlich)") })
            OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Beschreibung – optional") }, minLines = 2)
            if (linkedLetter != null) Text("Mit Brief #$linkedLetter verknüpft", fontSize = 12.sp)
            Button(onClick = { act {
                api.proposeEp(token, beneficiary!!, EP_PROPOSAL_POINTS, title.trim(), description.trim().ifBlank { null }, linkedLetter)
                title = ""; description = ""; onDraftConsumed()
            } }, enabled = beneficiary != null && title.isNotBlank()) { Text("EP-Vorschlag senden") }
        } } }
        overview?.incoming?.takeIf { it.isNotEmpty() }?.let { incoming ->
            item { SocialTitle("Eingehende EP") }
            items(incoming, key = { "ep-in-${it.id}" }) { proposal -> EpCard(proposal) {
                Button(onClick = { act { api.respondEp(token, proposal.id, true) } }) { Text("Annehmen") }
                TextButton(onClick = { act { api.respondEp(token, proposal.id, false) } }) { Text("Ablehnen") }
            } }
        }
        overview?.outgoing?.takeIf { it.isNotEmpty() }?.let { outgoing ->
            item { SocialTitle("Ausgehende EP") }
            items(outgoing, key = { "ep-out-${it.id}" }) { proposal -> EpCard(proposal) { Text("wartet") } }
        }
        item { SocialTitle("Angenommene EP-Geschichte") }
        overview?.history?.let { history ->
            if (history.isEmpty()) item { SocialInfo("Noch keine angenommenen EP.") }
            else items(history, key = { "ep-history-${it.id}" }) { proposal -> EpCard(proposal) {
                proposal.letterId?.let { Text("Brief #$it", fontSize = 12.sp) }
            } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EpCard(proposal: ApiClient.EpProposal, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${formatEpPoints(proposal.points)} · ${proposal.title}", fontWeight = FontWeight.Bold)
            Text("${proposal.proposerName} → ${proposal.beneficiaryName}", fontSize = 12.sp)
            proposal.description?.let { Text(it) }
        }
        Column { action() }
    } }
}

@Composable
private fun SocialTitle(text: String) = Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun SocialInfo(text: String) = Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }

internal fun socialDate(epoch: Long): String = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm")
    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(epoch))
