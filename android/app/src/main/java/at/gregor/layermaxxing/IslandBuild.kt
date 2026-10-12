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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val BrassLight = Color(0xFFEBCB82)
    val BrassDark = Color(0xFFB5863D)

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
    /** What the next upgrade costs, for the info bar: "gratis" in creative mode, points otherwise. */
    fun upgradeCost(c: ApiClient.IslandConstruction, level: Int): String? = when {
        level >= 3 -> null
        c.mode == "creative" -> "gratis"
        else -> "${c.buildCosts.getOrElse(level) { 0 }} P"
    }
    /** The line under the building's name: next stage, price, day gate, missing points. */
    fun upgradeLine(c: ApiClient.IslandConstruction, level: Int): String {
        if (level >= 3) return "Meisterbau erreicht · voll ausgebaut"
        val cost = c.buildCosts.getOrElse(level) { 0 }; val day = c.buildDays.getOrElse(level) { 0 }
        if (c.mode == "creative") return "Nächste Stufe ${level + 1}: gratis"
        return when {
            c.ageDays < day -> "Stufe ${level + 1}: $cost Punkte · ab Tag $day"
            c.available < cost -> "Stufe ${level + 1}: $cost Punkte · es fehlen ${cost - c.available}"
            else -> "Stufe ${level + 1}: $cost Punkte"
        }
    }
    /** The line on the Bauen button: "gratis" in creative mode, otherwise what the cheapest next step costs. */
    fun shopLine(c: ApiClient.IslandConstruction): String = when {
        c.mode == "creative" -> "gratis"
        IsleBuilding.entries.any { (c.buildings[it.name] ?: 0) == 0 } -> "ab ${c.buildCosts.getOrElse(0) { 0 }} P"
        c.land < 5 -> "ab ${c.landCosts.getOrElse(c.land) { 0 }} P"
        else -> "${c.available} P"
    }
    fun decorFull(h: ApiClient.HomeIsland) = h.construction.mode != "creative" &&
        h.construction.decor.size >= h.construction.decorLimits.getOrElse(h.construction.land) { 6 }
}

/**
 * One entry of the island's second row, Clash-of-Clans style: a round painted button with a label
 * under it (above the unchanged app navigation).
 */
@Composable
internal fun IslandRowItem(icon: Int, label: String, selected: Boolean = false, badge: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClickLabel = label, onClick = onClick).widthIn(min = 62.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { if (selected) this.selected = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(46.dp).shadow(5.dp, CircleShape)
                    .background(Brush.verticalGradient(if (selected) listOf(Color(0xFFF6E3AE), Color(0xFFD9B05A)) else listOf(Color(0xFF45666B), Color(0xFF253D42))), CircleShape)
                    .border(2.dp, if (selected) Color(0xFFFFF3DC) else Color(0xAAE6D3A3), CircleShape),
                Alignment.Center,
            ) { AppIcon(icon, null, tint = if (selected) Color(0xFF3B2410) else IslandBuild.Cream, size = 23.dp) }
            if (badge > 0) Text("$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).background(Isle.Teal, CircleShape).padding(horizontal = 4.dp))
        }
        WorldText(label, 11.sp, plaque = false, display = false, fill = if (selected) IslandBuild.Gold else IslandBuild.Cream)
    }
}

@Composable
internal fun IslandActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
    ) { content() }
}

/**
 * The round "Bauen" button at the bottom right (Clash of Clans' shop), in the painted island family since
 * 7.9.7: teal like the island's other buttons, a brass ring like the "Meine Insel" plate, a quiet hammer
 * icon and the price of the cheapest next step on a small brass chip ("gratis" in creative mode).
 * Always there outside placement. No yellow disc any more.
 */
@Composable
internal fun ShopButton(line: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(26.dp)).clickable(onClickLabel = "Bauleiste öffnen", onClick = onClick).padding(2.dp)
            .semantics { contentDescription = "Bauen – Bauleiste öffnen"; role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.padding(bottom = 8.dp).size(58.dp).shadow(8.dp, CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF3E7C76), Color(0xFF1E4744))), CircleShape)
                    .border(3.dp, Brush.verticalGradient(listOf(IslandBuild.BrassLight, IslandBuild.BrassDark)), CircleShape),
                Alignment.Center,
            ) { AppIcon(R.drawable.ico_build, null, tint = IslandBuild.Cream, size = 27.dp) }
            PriceChip(line)
        }
        WorldText("Bauen", 12.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

/** Small brass price chip (shop button, build cards): the island's plate colours, never a loud colour. */
@Composable
internal fun PriceChip(text: String, modifier: Modifier = Modifier) {
    val free = text == "gratis"
    Text(text, color = if (free) IslandBuild.Cream else Color(0xFF3B2410), fontSize = 10.sp, fontFamily = Kit.Body, fontWeight = FontWeight.ExtraBold,
        maxLines = 1, softWrap = false,
        modifier = modifier.semantics { contentDescription = "Preis: $text" }
            .background(if (free) Brush.verticalGradient(listOf(Color(0xFF4F8F66), Color(0xFF356B4A)))
                else Brush.verticalGradient(listOf(IslandBuild.BrassLight, IslandBuild.BrassDark)), RoundedCornerShape(50))
            .border(1.dp, Color(0xFF5A3A1A).copy(alpha = .55f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
}

/** A small round side button (Bearbeiten, Menü) with its name on a little plaque under it. */
@Composable
internal fun IslandSideButton(icon: Int, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = label, onClick = onClick).padding(2.dp)
            .semantics { role = Role.Button; if (selected) this.selected = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).shadow(5.dp, CircleShape)
                .background(Brush.verticalGradient(if (selected) listOf(Color(0xFFF6E3AE), Color(0xFFD9B05A)) else listOf(Color(0xFF45666B), Color(0xFF253D42))), CircleShape)
                .border(2.dp, if (selected) Color(0xFFFFF3DC) else Color(0xAAE6D3A3), CircleShape),
            Alignment.Center,
        ) { AppIcon(icon, null, tint = if (selected) Color(0xFF3B2410) else IslandBuild.Cream, size = 22.dp) }
        WorldText(label, 10.5.sp, display = false, fill = if (selected) IslandBuild.Gold else IslandBuild.Cream, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The island's resting controls: one narrow column at the right edge above the app navigation, Menü and
 * the bigger Bauen button (7.9.7: no "Bearbeiten" any more; moving is a long press or "Verschieben" in a
 * piece's actions). The way back to the overview is the pill at the top left.
 */
@Composable
internal fun IslandSideActions(shopLine: String, onBuild: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { contentDescription = "Aktionsgruppe" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IslandSideButton(R.drawable.ico_menu, "Menü", onClick = onMenu)
        ShopButton(shopLine, onBuild)
    }
}

/**
 * The tapped piece's actions, hanging right at it (Clash of Clans): its name on a small sign and one
 * pill of round icon buttons (Öffnen, Ausbauen with the price on the button, Verschieben, Abreißen).
 * Compact on purpose: no bottom card, nothing stacked.
 */
@Composable
internal fun PieceActionBar(title: String, subtitle: String?, actions: List<SelectionAction>) {
    Column(Modifier.semantics { contentDescription = "Gebäudeaktionen" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.shadow(4.dp, RoundedCornerShape(50)).background(Color(0xF2243A3F), RoundedCornerShape(50))
                .border(1.dp, Color(0x99E6D3A3), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = IslandBuild.Cream, fontFamily = Kit.Display, fontSize = 15.sp, maxLines = 1, softWrap = false)
            if (subtitle != null) Text(" · $subtitle", color = IslandBuild.Gold, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.shadow(8.dp, RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF22B4449), Color(0xF21E3236))), RoundedCornerShape(28.dp))
                .border(1.5.dp, Color(0x88E6D3A3), RoundedCornerShape(28.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEach { a ->
                Column(
                    // wide enough for every label ("Verschieben" was cut off at a fixed 60 dp)
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = a.label, onClick = a.onClick)
                        .widthIn(min = 58.dp).padding(horizontal = 3.dp, vertical = 2.dp).semantics { role = Role.Button },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.padding(bottom = if (a.detail != null) 7.dp else 0.dp).size(42.dp).shadow(4.dp, CircleShape)
                                .background(Brush.verticalGradient(listOf(a.tone.copy(alpha = .92f), a.tone)), CircleShape)
                                .border(2.dp, Color(0xFFFFF3DC), CircleShape),
                            Alignment.Center,
                        ) { AppIcon(a.icon, null, tint = if (a.dark) Color(0xFF3B2410) else Color.White, size = 22.dp) }
                        // The price sits on the button itself, like the cost on a base-builder's upgrade button.
                        if (a.detail != null) Text(a.detail, color = Color(0xFF3B2410), fontSize = 9.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            softWrap = false, modifier = Modifier.background(Color(0xFFFFF3DC), RoundedCornerShape(50)).padding(horizontal = 6.dp))
                    }
                    Text(a.label, color = IslandBuild.Cream, fontSize = 10.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** The short placement hint at the top: shown when a piece is picked up, then it fades away. */
@Composable
internal fun PlacementHint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = IslandBuild.Cream, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false,
        modifier = modifier.shadow(6.dp, RoundedCornerShape(50)).background(Color(0xF2243A3F), RoundedCornerShape(50))
            .border(1.dp, Color(0x99E6D3A3), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp))
}

/** One-time note after the creative mode was switched on in the island menu. */
@Composable
internal fun CreativeHintCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 18.dp).shadow(10.dp, RoundedCornerShape(20.dp)).background(Isle.Card, RoundedCornerShape(20.dp))
            .border(1.5.dp, Color(0xFFC9A55C), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "Hinweis Kreativmodus" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ico_build, null, tint = Color(0xFFB9852E), size = 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Kreativmodus ist an", fontFamily = Kit.Display, fontSize = 16.sp, color = Isle.Ink)
            Text("Frei bauen, ausbauen, abreißen – kostenlos. Deine Punkte bleiben; beim Ausschalten bleibt alles stehen.",
                fontFamily = Kit.Body, fontSize = 12.sp, color = Isle.Muted)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Verstanden", color = Isle.TealDark, fontWeight = FontWeight.Bold) }
    }
}

/** The four sections of the build shelf, in strip order (7.9.7: one continuous strip). */
internal object BuildShelf {
    const val BUILDINGS = 0
    const val DECOR = 1
    const val SPECIAL = 2
    const val LAND = 3
    val titles = listOf("Gebäude", "Deko", "Besondere Deko", "Land")
    /** First strip index of every section: each section starts with its own heading item. */
    fun starts(counts: List<Int>): List<Int> = counts.runningFold(0) { acc, n -> acc + n + 1 }.dropLast(1)
    /** Section of the strip item at [index]. */
    fun sectionAt(index: Int, starts: List<Int>): Int = starts.indexOfLast { it <= index }.coerceAtLeast(0)
    fun decorKeys(home: ApiClient.HomeIsland) = home.items.map { it.key }.ifEmpty { IsleDecor.labels.keys.toList() }.filter { IsleDecor.res(it) != null && !SpecialDecor.isSpecial(it) }
    fun counts(home: ApiClient.HomeIsland) = listOf(IsleBuilding.entries.size, decorKeys(home).size, SpecialDecor.items.size, 1)
}

/**
 * The shelf: Gebäude · Deko · Besondere Deko · Land as ONE continuous strip. Sideways scrolling runs from
 * one section into the next (each starts with a small heading); the section chips above follow the scroll
 * and a tap on a chip scrolls there animated. Tap a card = place it on the island.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun IslandBuildBar(
    home: ApiClient.HomeIsland,
    onBuilding: (IsleBuilding) -> Unit,
    onDecor: (String) -> Unit,
    onLand: () -> Unit,
    onClose: () -> Unit,
    startSection: Int = BuildShelf.BUILDINGS,
) {
    val c = home.construction
    val decorKeys = BuildShelf.decorKeys(home)
    val starts = BuildShelf.starts(BuildShelf.counts(home))
    val density = androidx.compose.ui.platform.LocalDensity.current
    var target by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val state = rememberScrollState()
    fun offset(index: Int) = (0 until index).sumOf { if (it in starts) 66 else 104 }
    val sectionOffsets = starts.map { with(density) { offset(it).dp.roundToPx() } }
    val following by androidx.compose.runtime.remember(sectionOffsets) {
        androidx.compose.runtime.derivedStateOf(androidx.compose.runtime.structuralEqualityPolicy()) {
            if (state.value == state.maxValue && state.value > 0) BuildShelf.LAND
            else BuildShelf.sectionAt(state.value, sectionOffsets)
        }
    }
    androidx.compose.runtime.LaunchedEffect(startSection) { state.scrollTo(sectionOffsets[startSection]) }
    androidx.compose.runtime.LaunchedEffect(target) {
        val section = target ?: return@LaunchedEffect
        try { state.animateScrollTo(sectionOffsets[section], androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        finally { if (target == section) target = null }
    }
    val current = target ?: following
    val specialCount = SpecialDecor.count(c)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { contentDescription = "Bauleiste" }.shadow(10.dp, RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF22B4449), Color(0xF21E3236))), RoundedCornerShape(24.dp))
            .border(1.5.dp, Color(0x88E6D3A3), RoundedCornerShape(24.dp)).padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BuildShelf.titles.forEachIndexed { i, t ->
                    val on = i == current
                    val label = if (i == BuildShelf.SPECIAL) "$t $specialCount/${c.specialLimit}" else t
                    Text(label, color = if (on) Color(0xFF3B2410) else IslandBuild.Cream, fontFamily = Kit.Display, fontSize = 13.5.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) Brush.verticalGradient(listOf(IslandBuild.BrassLight, IslandBuild.BrassDark)) else Brush.verticalGradient(listOf(Color(0x22FFF8E6), Color(0x22FFF8E6))))
                            .clickable(onClickLabel = "Zu $t springen") {
                                target = i
                            }.padding(horizontal = 11.dp, vertical = 5.dp)
                            .semantics { role = Role.Tab; contentDescription = "Reiter $t"; if (on) selected = true })
                }
            }
            Text(if (c.mode == "creative") "gratis" else "${c.available} P", color = IslandBuild.Gold, fontSize = 12.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp))
            Box(Modifier.padding(start = 6.dp).size(40.dp).clip(CircleShape).background(Color(0x33FFF8E6))
                .clickable(onClickLabel = "Bauleiste schließen", onClick = onClose).semantics { contentDescription = "Bauleiste schließen" }, Alignment.Center) {
                AppIcon(R.drawable.ico_close, null, tint = IslandBuild.Cream, size = 20.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(state).semantics { contentDescription = "Bauleiste Streifen" }.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShelfHeading(BuildShelf.titles[0], null)
            IsleBuilding.entries.forEach { b ->
                val level = c.buildings[b.name] ?: 0
                val stage = IslandStages.of(b, level.coerceAtLeast(1))
                BuildCard(stage.name, IslandBuild.price(c, b), IslandBuild.buildable(c, b),
                    b.label + if (level == 0) " platzieren" else " auswählen", { onBuilding(b) }, built = level > 0) {
                    PaintedIslandPlace(b, stage.level, stage.res, Modifier.fillMaxSize(.9f))
                }
            }
            ShelfHeading(BuildShelf.titles[1], null)
            decorKeys.forEach { key ->
                val ok = IslandBuild.decorUnlocked(home, key) && !IslandBuild.decorFull(home)
                BuildCard(IsleDecor.labels[key] ?: key, if (IslandBuild.decorFull(home)) "Platz voll" else IslandBuild.decorPrice(home, key), ok,
                    "${IsleDecor.labels[key] ?: key} platzieren", { onDecor(key) }) {
                    Image(painterResource(IsleDecor.res(key)!!), null, Modifier.fillMaxSize(.82f), contentScale = ContentScale.Fit)
                }
            }
            ShelfHeading(BuildShelf.titles[2], "$specialCount/${c.specialLimit}")
            SpecialDecor.items.forEach { sp ->
                val full = SpecialDecor.full(c)
                val price = if (c.mode == "creative") "gratis" else "${SpecialDecor.cost(c, sp.key)} P"
                BuildCard(sp.label, if (full) "${c.specialLimit}/${c.specialLimit} voll" else price, !full && SpecialDecor.affordable(c, sp.key),
                    "Besondere Deko ${sp.label} platzieren", { onDecor(sp.key) }, special = true) {
                    Image(painterResource(sp.res), null, Modifier.fillMaxSize(.95f), contentScale = ContentScale.Fit)
                }
            }
            ShelfHeading(BuildShelf.titles[3], null)
            val level = c.land
            BuildCard("Insel vergrößern", if (level >= 5) "ganz ausgebaut" else if (c.mode == "creative") "gratis" else "${c.landCosts.getOrElse(level) { 0 }} P · ab Tag ${c.landDays.getOrElse(level) { 0 }}",
                level < 5, "Land erweitern", onLand) {
                    Image(painterResource(if (level >= 2) R.drawable.terrain_home else R.drawable.terrain_sand), null, Modifier.fillMaxSize(.92f), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** A section heading inside the strip: a slim brass-edged plate, so the strip reads as one shelf with dividers. */
@Composable
private fun ShelfHeading(title: String, count: String?) {
    Column(
        Modifier.height(122.dp).width(58.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0x33FFF8E6), Color(0x14FFF8E6))))
            .border(1.dp, IslandBuild.BrassDark.copy(alpha = .7f), RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Abschnitt $title" }.padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        title.split(" ").forEach { word ->
            Text(word, color = IslandBuild.Gold, fontFamily = Kit.Display, fontSize = if (word.length > 7) 9.sp else 12.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
        }
        if (count != null) Text(count, color = Color(0xFF3B2410), fontFamily = Kit.Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp).background(Brush.verticalGradient(listOf(IslandBuild.BrassLight, IslandBuild.BrassDark)), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
private fun BuildCard(name: String, price: String, enabled: Boolean, description: String, onClick: () -> Unit, built: Boolean = false, special: Boolean = false, art: @Composable () -> Unit) {
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF6E2), Color(0xFFF0E1C0))))
            .border(if (special) 2.5.dp else 2.dp, if (built) Isle.Teal else if (special) IslandBuild.BrassDark else Color(0xFFB9852E), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).semantics { contentDescription = description; role = Role.Button; if (!enabled) stateDescription = "nicht verfügbar" }
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
        Text(price, color = if (price == "gratis") IslandBuild.Ok else Isle.Muted, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
    }
}

/**
 * Round ✗ / ✓ bubbles that hang right at the held piece and follow it (Clash of Clans). ✓ saves,
 * ✗ discards; on an impossible spot ✓ is greyed out.
 */
@Composable
internal fun PlacementBubbleRow(valid: Boolean, busy: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PlacementBubble("Abbrechen", R.drawable.ico_close, IslandBuild.No, !busy, null, onCancel)
        PlacementBubble("Bestätigen", R.drawable.ico_check, IslandBuild.Ok, !busy && valid, if (busy) "Speichert…" else if (!valid) "Hier nicht möglich" else null, onConfirm)
    }
}

@Composable
private fun PlacementBubble(label: String, icon: Int, color: Color, enabled: Boolean, state: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(PlacementBubbleSize).shadow(7.dp, CircleShape).clip(CircleShape)
            .background(if (enabled) color else Color(0xFF8B9799), CircleShape)
            .border(3.dp, Color(0xFFFFF3DC), CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button; if (state != null) stateDescription = state },
        Alignment.Center,
    ) { AppIcon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else .75f), size = 28.dp) }
}

internal val PlacementBubbleSize = 52.dp

/** One big round action of the info bar. */
internal data class SelectionAction(val label: String, val icon: Int, val detail: String? = null, val tone: Color = Color(0xFF3F7C78), val dark: Boolean = false, val onClick: () -> Unit)

/**
 * The info bar of a tapped building/decoration (Clash of Clans): who it is, what it can become, and
 * big round buttons for the few things one can do. A tap on empty ground closes it.
 */
@Composable
internal fun SelectionBar(
    title: String, subtitle: String, art: @Composable () -> Unit,
    actions: List<SelectionAction>, onClose: () -> Unit, info: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).shadow(10.dp, RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF22B4449), Color(0xF21E3236))), RoundedCornerShape(24.dp))
            .border(1.5.dp, Color(0x88E6D3A3), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp).semantics { contentDescription = "Info-Leiste" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Brush.radialGradient(listOf(Color(0x66FFF8E6), Color.Transparent)), CircleShape), Alignment.BottomCenter) { art() }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = IslandBuild.Cream, fontFamily = Kit.Display, fontSize = 19.sp, maxLines = 1)
                Text(subtitle, color = IslandBuild.Gold, fontSize = 12.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, maxLines = 1)
                if (info != null) Text(info, color = Color(0xCCFFF8E6), fontSize = 11.5.sp, fontFamily = Kit.Body, maxLines = 2)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x33FFF8E6)).clickable(onClickLabel = "Schließen", onClick = onClose)
                .semantics { contentDescription = "Auswahl schließen" }, Alignment.Center) {
                AppIcon(R.drawable.ico_close, null, tint = IslandBuild.Cream, size = 20.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            actions.forEach { a ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = a.label, onClick = a.onClick).widthIn(min = 72.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp).semantics { role = Role.Button },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.size(46.dp).shadow(5.dp, CircleShape).background(Brush.verticalGradient(listOf(a.tone.copy(alpha = .92f), a.tone)), CircleShape)
                                .border(2.dp, Color(0xFFFFF3DC), CircleShape),
                            Alignment.Center,
                        ) { AppIcon(a.icon, null, tint = if (a.dark) Color(0xFF3B2410) else Color.White, size = 24.dp) }
                        // The price sits on the button itself, like the cost on a base-builder's upgrade button.
                        if (a.detail != null) Text(a.detail, color = Color(0xFF3B2410), fontSize = 10.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            modifier = Modifier.offset(y = 11.dp).background(Color(0xFFFFF3DC), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                    Text(a.label, color = IslandBuild.Cream, fontSize = 12.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.padding(top = if (a.detail != null) 12.dp else 3.dp))
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

/**
 * Header of the own island: a painted brass plate, clearly not the overview. Under it either one quiet
 * line or, in creative mode, exactly one chip "Kreativmodus · alles gratis". The line has a fixed height,
 * so switching modes never moves the island.
 */
@Composable
internal fun MyIslandHeader(title: String, subtitle: String, creative: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(240.dp).height(70.dp), Alignment.Center) {
            Image(painterResource(R.drawable.p_plate), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text(title, fontFamily = Kit.Display, fontSize = 24.sp, color = Color(0xFF3B2410), modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = "Ansicht: $title" })
        }
        Box(Modifier.height(22.dp), Alignment.Center) {
            if (creative) Text("Kreativmodus · alles gratis", color = Color(0xFF3B2410), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Kit.Body, maxLines = 1,
                modifier = Modifier.background(IslandBuild.Gold, RoundedCornerShape(50)).border(1.dp, Color(0xFFFFF3DC), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp))
            else WorldText(subtitle, 11.sp, fill = IslandBuild.Gold, display = false)
        }
    }
}

/** The island menu: every function as a painted piece on harbour paper under a signpost. */
@Composable
internal fun IslandMenuContent(
    home: ApiClient.HomeIsland?, onOpen: (IsleBuilding) -> Unit, onSettings: () -> Unit, onBuild: () -> Unit, onOverview: () -> Unit = {}, overviewLabel: String = "Übersicht",
    creativeEligible: Boolean = false, creative: Boolean = false, onCreative: (Boolean) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding()) {
        // The creative switch sits on top, plainly labelled, where the island's own options are.
        if (creativeEligible) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (creative) Color(0xFFF6E3AE) else Color(0xFFF1E6CC))
                    .border(1.5.dp, if (creative) Color(0xFFD9B05A) else Color(0xFFC9A55C), RoundedCornerShape(18.dp))
                    .clickable(onClickLabel = if (creative) "Kreativmodus ausschalten" else "Kreativmodus einschalten") { onCreative(!creative) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(R.drawable.ico_build, null, tint = Color(0xFF8A5A14), size = 24.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Kreativmodus: ${if (creative) "Ein" else "Aus"}", fontFamily = Kit.Display, fontSize = 17.sp, color = Isle.Ink)
                    Text("Frei bauen, ausbauen, abreißen – kostenlos", fontFamily = Kit.Body, fontSize = 12.sp, color = Isle.Muted)
                }
                androidx.compose.material3.Switch(creative, onCheckedChange = onCreative,
                    modifier = Modifier.semantics { contentDescription = "Kreativmodus" })
            }
        }
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
                    val stage = IslandStages.of(b, level)
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
                            PaintedIslandPlace(b, level, stage.res, Modifier.fillMaxSize(.9f))
                            AppIcon(stage.icon, "${stage.name} – ${stage.function}", Modifier.align(Alignment.TopEnd), tint = Isle.TealDark, size = 16.dp)
                        }
                        Text(stage.name, fontFamily = Kit.Display, fontSize = 13.sp, color = Isle.Ink, maxLines = 2, textAlign = TextAlign.Center)
                        Text(stage.subtitle, fontSize = 10.5.sp, color = Isle.Muted, fontFamily = Kit.Body, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        MenuPill(overviewLabel, if (overviewLabel == "Meine Insel") R.drawable.ico_island else R.drawable.ico_map, Modifier.fillMaxWidth().padding(bottom = 10.dp), onOverview)
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
        Spacer(Modifier.width(4.dp))
        Text(label, color = IslandBuild.Cream, fontFamily = Kit.Body, fontWeight = FontWeight.Bold)
    }
}
