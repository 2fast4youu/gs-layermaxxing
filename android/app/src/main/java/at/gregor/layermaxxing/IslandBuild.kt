package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Build mode in the style of base-builder games: a horizontal shelf of painted pieces
 * above the app navigation, placement with grid + ✓/✗, compact action bar for a tapped
 * piece and an explicit demolish confirmation. Uses only the island's painted sprites.
 */
internal object IslandBuild {
    val Wood = Color(0xE6243A3F)
    val Gold = Color(0xFFE6D3A3)
    val Cream = Color(0xFFFFF8E6)
    val Ok = Color(0xFF3E9B63)
    val No = Color(0xFFB3412E)

    fun subtitle(b: IsleBuilding) = when (b) {
        IsleBuilding.HOUSE -> "Profil"
        IsleBuilding.POST -> "Briefe"
        IsleBuilding.LIGHTHOUSE -> "Anfragen"
        IsleBuilding.LIBRARY -> "Begriffe"
        IsleBuilding.CAMPFIRE -> "Treffpunkt"
        IsleBuilding.HARBOUR -> "Quests"
    }

    /** Price line of a building card: free in creative, points + day gate in normal mode. */
    fun price(c: ApiClient.IslandConstruction, b: IsleBuilding): String {
        val level = c.buildings[b.name] ?: 0
        if (level > 0) return "Stufe $level · steht"
        if (c.mode == "creative") return "gratis"
        val cost = c.buildCosts.getOrElse(0) { 0 }; val day = c.buildDays.getOrElse(0) { 0 }
        return if (c.ageDays < day) "$cost P · ab Tag $day" else "$cost Punkte"
    }
    fun buildable(c: ApiClient.IslandConstruction, b: IsleBuilding) = (c.buildings[b.name] ?: 0) > 0 ||
        IslandEvolution.allowed(c, c.buildCosts.getOrElse(0) { 0 }, c.buildDays.getOrElse(0) { 0 })
    fun decorUnlocked(h: ApiClient.HomeIsland, item: String) = h.construction.mode == "creative" ||
        (h.items.firstOrNull { it.key == item }?.unlocked ?: true)
    fun decorPrice(h: ApiClient.HomeIsland, item: String): String = when {
        h.construction.mode == "creative" -> "gratis"
        decorUnlocked(h, item) -> "freigeschaltet"
        else -> "ab ${h.items.firstOrNull { it.key == item }?.unlockAt ?: 0} P"
    }
    fun decorFull(h: ApiClient.HomeIsland) = h.construction.mode != "creative" &&
        h.construction.decor.size >= h.construction.decorLimits.getOrElse(h.construction.land) { 6 }
}

/** One entry of the island's second row (above the unchanged app navigation). */
@Composable
internal fun IslandRowItem(icon: Int, label: String, selected: Boolean = false, badge: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Color(0x40E6D3A3) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick).heightIn(min = 52.dp).widthIn(min = 64.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { if (selected) this.selected = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(icon, null, tint = if (selected) IslandBuild.Gold else IslandBuild.Cream, size = 22.dp, modifier = Modifier.padding(3.dp))
            if (badge > 0) Text("$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-2).dp).background(Isle.Teal, CircleShape).padding(horizontal = 4.dp))
        }
        WorldText(label, 11.sp, plaque = false, fill = if (selected) IslandBuild.Gold else IslandBuild.Cream)
    }
}

@Composable
internal fun IslandActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.shadow(6.dp, RoundedCornerShape(26.dp)).background(IslandBuild.Wood, RoundedCornerShape(26.dp))
            .border(1.dp, Color(0x55E6D3A3), RoundedCornerShape(26.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The shelf: painted pieces side by side, scroll sideways, tap = place on the island. */
@Composable
internal fun IslandBuildBar(
    home: ApiClient.HomeIsland,
    tab: String,
    onTab: (String) -> Unit,
    onBuilding: (IsleBuilding) -> Unit,
    onDecor: (String) -> Unit,
    onLand: () -> Unit,
    onPlaces: () -> Unit,
    onClose: () -> Unit,
) {
    val c = home.construction
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).shadow(10.dp, RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF22B4449), Color(0xF21E3236))), RoundedCornerShape(24.dp))
            .border(1.5.dp, Color(0x88E6D3A3), RoundedCornerShape(24.dp)).padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Gebäude", "Deko", "Land").forEach { t ->
                val on = t == tab
                Text(t, color = if (on) Color(0xFF3B2410) else IslandBuild.Cream, fontFamily = Kit.Display, fontSize = 15.sp,
                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                        .background(if (on) IslandBuild.Gold else Color(0x22FFF8E6))
                        .clickable(onClickLabel = "Reiter $t") { onTab(t) }.padding(horizontal = 14.dp, vertical = 6.dp)
                        .semantics { role = Role.Tab; if (on) selected = true })
            }
            Spacer(Modifier.weight(1f))
            Text(if (c.mode == "creative") "Kreativ · alles gratis" else "${c.available} P", color = IslandBuild.Gold, fontSize = 12.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold)
            Box(Modifier.padding(start = 6.dp).size(40.dp).clip(CircleShape).background(Color(0x33FFF8E6))
                .clickable(onClickLabel = "Bauleiste schließen", onClick = onClose).semantics { contentDescription = "Bauleiste schließen" }, Alignment.Center) {
                AppIcon(R.drawable.ico_close, null, tint = IslandBuild.Cream, size = 20.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                "Deko" -> {
                    val keys = home.items.map { it.key }.ifEmpty { IsleDecor.labels.keys.toList() }
                    keys.forEach { key ->
                        val res = IsleDecor.res(key) ?: return@forEach
                        val ok = IslandBuild.decorUnlocked(home, key) && !IslandBuild.decorFull(home)
                        BuildCard(IsleDecor.labels[key] ?: key, if (IslandBuild.decorFull(home)) "Platz voll" else IslandBuild.decorPrice(home, key), ok,
                            "${IsleDecor.labels[key] ?: key} platzieren", { onDecor(key) }) {
                            Image(painterResource(res), null, Modifier.fillMaxSize(.82f), contentScale = ContentScale.Fit)
                        }
                    }
                }
                "Land" -> {
                    val level = c.land
                    BuildCard("Insel vergrößern", if (level >= 5) "ganz ausgebaut" else if (c.mode == "creative") "gratis" else "${c.landCosts.getOrElse(level) { 0 }} P · ab Tag ${c.landDays.getOrElse(level) { 0 }}",
                        true, "Land erweitern", onLand) {
                        Image(painterResource(if (level >= 2) R.drawable.terrain_home else R.drawable.terrain_sand), null, Modifier.fillMaxSize(.92f), contentScale = ContentScale.Fit)
                    }
                    BuildCard("Lebensort", "eigener Ort", true, "Lebensort bauen", onPlaces) {
                        Image(painterResource(R.drawable.place_home), null, Modifier.fillMaxSize(.85f), contentScale = ContentScale.Fit)
                    }
                }
                else -> IsleBuilding.entries.forEach { b ->
                    val level = c.buildings[b.name] ?: 0
                    val tier = level.coerceAtLeast(1)
                    BuildCard(b.label, IslandBuild.price(c, b), IslandBuild.buildable(c, b),
                        b.label + if (level == 0) " platzieren" else " auswählen", { onBuilding(b) }, built = level > 0) {
                        PaintedIslandPlace(b, tier, IslandArtwork.resource(b, tier), Modifier.fillMaxSize(.9f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildCard(name: String, price: String, enabled: Boolean, description: String, onClick: () -> Unit, built: Boolean = false, art: @Composable () -> Unit) {
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF6E2), Color(0xFFF0E1C0))))
            .border(2.dp, if (built) Isle.Teal else Color(0xFFB9852E), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).semantics { contentDescription = description; role = Role.Button }
            .alpha(if (enabled) 1f else .55f).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(78.dp).clip(RoundedCornerShape(14.dp)).background(Brush.radialGradient(listOf(Color(0x557FA2A3), Color.Transparent))), Alignment.BottomCenter) {
            art()
            if (!enabled) Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(Color(0xCC243A3F), CircleShape), Alignment.Center) {
                AppIcon(R.drawable.ico_lock, null, tint = IslandBuild.Cream, size = 12.dp)
            }
        }
        Text(name, color = Isle.Ink, fontFamily = Kit.Display, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
        Text(price, color = if (price == "gratis") IslandBuild.Ok else Isle.Muted, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1)
    }
}

/** ✓ / ✗ under the placement preview. Round, labelled, large enough for thumbs. */
@Composable
internal fun PlacementControls(title: String, valid: Boolean, busy: Boolean, confirmLabel: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WorldText(title + if (valid) " · Platz frei" else " · hier nicht möglich", 14.sp, display = false,
            fill = if (valid) Color(0xFFBFF0CF) else Color(0xFFFFC9C1))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundChoice("Abbrechen", R.drawable.ico_close, IslandBuild.No, !busy, onCancel)
            RoundChoice(if (busy) "Speichert…" else confirmLabel, R.drawable.ico_check, IslandBuild.Ok, !busy && valid, onConfirm)
        }
    }
}

@Composable
private fun RoundChoice(label: String, icon: Int, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).padding(4.dp)
            .semantics { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).shadow(6.dp, CircleShape).background(if (enabled) color else Color(0xFF7D8A8C), CircleShape)
            .border(3.dp, Color(0xFFFFF3DC), CircleShape), Alignment.Center) {
            AppIcon(icon, null, tint = Color.White, size = 28.dp)
        }
        WorldText(label, 12.sp, display = false, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Compact bar for a tapped building/decoration: thumbnail, name and the few things one can do. */
@Composable
internal fun SelectionBar(
    title: String, subtitle: String, art: @Composable () -> Unit,
    actions: List<Triple<String, Int, () -> Unit>>, onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).shadow(8.dp, RoundedCornerShape(22.dp))
            .background(Isle.Card, RoundedCornerShape(22.dp)).border(1.5.dp, Color(0xFFC9B892), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), Alignment.BottomCenter) { art() }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = Isle.Ink, fontFamily = Kit.Display, fontSize = 17.sp, maxLines = 1)
                Text(subtitle, color = Isle.Muted, fontSize = 12.sp, fontFamily = Kit.Body, maxLines = 1)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Schließen", onClick = onClose).semantics { contentDescription = "Auswahl schließen" }, Alignment.Center) {
                AppIcon(R.drawable.ico_close, null, tint = Isle.Muted, size = 20.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            actions.forEach { (label, icon, run) ->
                val danger = label == "Abreißen" || label == "Entfernen"
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = label, onClick = run).heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { role = Role.Button },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(34.dp).background(if (danger) Color(0x22B3412E) else Color(0x223F7C78), CircleShape), Alignment.Center) {
                        AppIcon(icon, null, tint = if (danger) IslandBuild.No else Isle.TealDark, size = 19.dp)
                    }
                    Text(label, color = if (danger) IslandBuild.No else Isle.Ink, fontSize = 12.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Demolish needs an explicit yes; normal mode states plainly that points are not refunded. */
@Composable
internal fun DemolishDialog(name: String, creative: Boolean, what: String = "Das Gebäude", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Isle.Card,
        icon = { AppIcon(R.drawable.ico_delete, null, tint = IslandBuild.No, size = 28.dp) },
        title = { Text("$name abreißen?", fontFamily = Kit.Display, color = Isle.Ink) },
        text = {
            Text(
                if (creative) "Kreativmodus: Abreißen ist kostenlos. $what verschwindet von deiner Insel, du kannst jederzeit neu bauen."
                else "$what verschwindet von deiner Insel. Bereits ausgegebene Punkte werden nicht erstattet.",
                color = Isle.Muted, fontFamily = Kit.Body,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Abreißen", color = IslandBuild.No, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Behalten", color = Isle.TealDark) } },
    )
}

/** Header of the own island: a painted brass plate, clearly not the overview. */
@Composable
internal fun MyIslandHeader(title: String, subtitle: String, creative: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(240.dp).height(70.dp), Alignment.Center) {
            Image(painterResource(R.drawable.p_plate), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text(title, fontFamily = Kit.Display, fontSize = 24.sp, color = Color(0xFF3B2410), modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = "Ansicht: $title" })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (creative) Text("KREATIV", color = Color(0xFF3B2410), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Kit.Body,
                modifier = Modifier.padding(end = 6.dp).background(IslandBuild.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
            WorldText(subtitle, 11.sp, fill = IslandBuild.Gold, display = false)
        }
    }
}

/** The island menu: every function as a painted piece on harbour paper under a signpost. */
@Composable
internal fun IslandMenuContent(home: ApiClient.HomeIsland?, onOpen: (IsleBuilding) -> Unit, onSettings: () -> Unit, onBuild: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.fief_signpost), null, Modifier.size(64.dp))
            Column(Modifier.padding(start = 8.dp)) {
                Text("Inselmenü", fontFamily = Kit.Display, fontSize = 26.sp, color = Isle.Ink)
                Text("Wohin auf deiner Insel?", color = Isle.Muted, fontSize = 13.sp, fontFamily = Kit.Body)
            }
        }
        Spacer(Modifier.height(10.dp))
        IsleBuilding.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    val level = home?.construction?.buildings?.get(b.name) ?: 0
                    Column(
                        Modifier.weight(1f).shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFF6E2), Color(0xFFEFDDB8))))
                            .border(1.5.dp, Color(0xFFC9A55C), RoundedCornerShape(18.dp))
                            .clickable(onClickLabel = "${b.label} öffnen") { onOpen(b) }
                            .semantics { contentDescription = "${b.label} öffnen"; role = Role.Button }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(70.dp).background(Brush.radialGradient(listOf(Color(0x447FA2A3), Color.Transparent)), CircleShape), Alignment.BottomCenter) {
                            PaintedIslandPlace(b, level, IslandArtwork.resource(b, level), Modifier.fillMaxSize(.9f))
                        }
                        Text(b.label, fontFamily = Kit.Display, fontSize = 15.sp, color = Isle.Ink, maxLines = 1)
                        Text(IslandBuild.subtitle(b), fontSize = 10.5.sp, color = Isle.Muted, fontFamily = Kit.Body, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuPill("Einstellungen", R.drawable.ico_appearance, Modifier.weight(1f), onSettings)
            MenuPill("Bauen", R.drawable.ico_build, Modifier.weight(1f), onBuild)
        }
    }
}

@Composable
private fun MenuPill(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Isle.TealDark).clickable(onClickLabel = label, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp).semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        AppIcon(icon, null, tint = IslandBuild.Cream, size = 18.dp)
        Text(" $label", color = IslandBuild.Cream, fontFamily = Kit.Body, fontWeight = FontWeight.Bold)
    }
}
