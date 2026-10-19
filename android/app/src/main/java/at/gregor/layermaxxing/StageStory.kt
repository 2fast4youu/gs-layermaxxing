package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 7.9.13: the stage stories of docs/insel-kueste-storyplan-7.9.13.md (matrix of 24), told by the place itself, not by
 * text cards. Every building × stage has its story core and up to two painted things in the scene (left and right of
 * the golden action) that DO something the building already does: the name board opens the profile, the flap of the
 * mailbox the first unread letter, the spyglass the people search, the quill "add a word", the notice the open group
 * plans, the pennant the topics. Counts are the real ones; a thing without a handler is not drawn (no dummies); a thing
 * whose data is empty says why instead of pretending. Each stage keeps one thing of the stage before ([Item.key]):
 * the anchor of the story (the name board becomes the door sign and the flag, the spyglass moves from the palm to the
 * lookout, the tree's pin becomes the mailbox flap …).
 */
internal object StageStory {
    enum class Act {
        PROFILE, EDIT, UNREAD, LOCKED, FIND, REQUESTS, FRIENDS, SEARCH, ADD_WORD, CATEGORIES,
        GROUP_QUEST, GROUP_PLANS, TOPICS, ARRIVALS, OPEN_QUESTS,
    }
    /** A painted thing of the stage: [res] is a painted prop ([painted]) or a line icon on a brass disc. */
    data class Item(val key: String, val label: String, val act: Act, val res: Int, val painted: Boolean = true)
    data class Chapter(val building: IsleBuilding, val level: Int, val core: String, val items: List<Item>)

    private fun c(b: IsleBuilding, l: Int, core: String, vararg items: Item) = Chapter(b, l, core, items.toList())
    private val H = IsleBuilding.HOUSE; private val P = IsleBuilding.POST; private val L = IsleBuilding.LIGHTHOUSE
    private val B = IsleBuilding.LIBRARY; private val F = IsleBuilding.CAMPFIRE; private val R2 = IsleBuilding.HARBOUR

    private val table: List<Chapter> = listOf(
        c(H, 0, "Gestrandet: nur ein Ort zum Ausruhen",
            Item("name", "Namensbrett am Pfosten – Profil", Act.PROFILE, R.drawable.p_sign),
            Item("tools", "Treibgut ordnen – Insel bearbeiten", Act.EDIT, R.drawable.n_crates)),
        c(H, 1, "Die erste Nacht unter einem Dach",
            Item("name", "Namensbrett über der Tür – Profil", Act.PROFILE, R.drawable.p_sign),
            Item("tools", "Werkbank – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),
        c(H, 2, "Man richtet sich ein und zeigt, was man gesammelt hat",
            Item("name", "Türschild – Profil", Act.PROFILE, R.drawable.p_plate),
            Item("tools", "Fensterbank umräumen – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),
        c(H, 3, "Ein Zuhause, das Gäste sehen",
            Item("name", "Fahne in deiner Profilfarbe – Farbe ändern", Act.PROFILE, R.drawable.decor_flag),
            Item("tools", "Landhaus umbauen – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),

        c(P, 0, "Nachrichten werden an den Stamm geheftet",
            Item("flap", "Nadel am Stamm – ungelesener Brief", Act.UNREAD, R.drawable.p_pin),
            Item("seal", "Nebelbrief – noch gesperrt", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 1, "Die Briefe bekommen ein trockenes Zuhause",
            Item("flap", "Klappe des Postkastens – ungelesener Brief", Act.UNREAD, R.drawable.p_env_open),
            Item("seal", "Versiegelter Brief – noch gesperrt", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 2, "Jeder Freund hat sein eigenes Fach",
            Item("flap", "Fach mit neuer Post – ungelesener Brief", Act.UNREAD, R.drawable.p_cubby),
            Item("seal", "Verschlossenes Fach – gesperrte Briefe", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 3, "Die Post der Insel läuft über einen Schalter",
            Item("flap", "Eingang am Schalter – ungelesener Brief", Act.UNREAD, R.drawable.p_env_open),
            Item("seal", "Siegel – gesperrte Briefe und Nachweis", Act.LOCKED, R.drawable.ico_seal, painted = false)),

        c(L, 0, "Von der Palme aus Ausschau nach anderen halten",
            Item("spyglass", "Fernrohr – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Flaschenpost – offene Anfragen", Act.REQUESTS, R.drawable.p_bottle)),
        c(L, 1, "Ein fester Platz zum Ausschauhalten",
            Item("spyglass", "Fernrohr am Geländer – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Segel am Horizont – offene Anfragen", Act.REQUESTS, R.drawable.boat_sail)),
        c(L, 2, "Ein Licht, damit Freunde den Weg finden",
            Item("spyglass", "Fernrohr neben dem Feuer – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Feuerkorb – der Lichtkegel zeigt die Anfragen", Act.REQUESTS, R.drawable.decor_campfire)),
        c(L, 3, "Das Licht verbindet die Insel mit allen Freunden",
            Item("signal", "Laterne – Freunde im Strahl weiterdrehen", Act.FRIENDS, R.drawable.decor_lantern),
            Item("spyglass", "Fernrohr – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false)),

        c(B, 0, "Angeschwemmte Kisten, in einer liegt ein Buch",
            Item("lens", "Lupe – im Buch suchen", Act.SEARCH, R.drawable.ico_search, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.p_quill)),
        c(B, 1, "Ein Dach schützt die Wörter vor Regen",
            Item("lens", "Lupe – im Buch suchen", Act.SEARCH, R.drawable.ico_search, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.p_quill)),
        c(B, 2, "Aus losen Wörtern wird eine Sammlung",
            Item("books", "Bücher am Bord – nächste Kategorie", Act.CATEGORIES, R.drawable.p_books),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.p_quill)),
        c(B, 3, "Die Sprache der Insel ist bewahrt",
            Item("books", "Ledereinbände – nächste Kategorie", Act.CATEGORIES, R.drawable.p_book),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.p_quill)),

        c(F, 0, "Wer sich trifft, sitzt ums Feuer",
            Item("fire", "Feuer schüren – Plan für die Gruppe", Act.GROUP_QUEST, R.drawable.decor_campfire),
            Item("notice", "Zettel am Stein – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.p_note)),
        c(F, 1, "Die Runde zieht unter ein Dach",
            Item("fire", "Kerze auf dem Tisch – Plan für die Gruppe", Act.GROUP_QUEST, R.drawable.decor_lantern),
            Item("notice", "Aushang – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.p_note)),
        c(F, 2, "Mehrere Gruppen haben einen gemeinsamen Raum",
            Item("fire", "Haupttisch – Plan für die Gruppe", Act.GROUP_QUEST, R.drawable.p_table),
            Item("notice", "Aushangbrett – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.p_sticky)),
        c(F, 3, "Gemeinsam Erreichtes wird gefeiert",
            Item("fire", "Festtisch – Plan für die Gruppe", Act.GROUP_QUEST, R.drawable.p_table),
            Item("notice", "Wimpel – erledigte und offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.decor_flag)),

        c(R2, 0, "Ein Boot am Strand, bereit für die erste Aufgabe",
            Item("pennant", "Wimpel – Themen", Act.TOPICS, R.drawable.decor_flag),
            Item("arrival", "Ruderboot – angekommene Briefe", Act.ARRIVALS, R.drawable.n_rowboat)),
        c(R2, 1, "Vom Strand hinaus aufs Wasser",
            Item("pennant", "Wimpel am Stegende – Themen", Act.TOPICS, R.drawable.decor_flag),
            Item("arrival", "Boot am Steg – angekommene Briefe", Act.ARRIVALS, R.drawable.boat_row)),
        c(R2, 2, "Mehrere Boote können festmachen",
            Item("bollard", "Poller – offene Quests", Act.OPEN_QUESTS, R.drawable.p_stool),
            Item("pennant", "Wimpel – Themen", Act.TOPICS, R.drawable.decor_flag)),
        c(R2, 3, "Briefe und Aufgaben laufen hier ein und aus",
            Item("pennant", "Kompass an der Laternenstange – Themen", Act.TOPICS, R.drawable.ico_compass, painted = false),
            Item("arrival", "Laterne am Kai – angekommene Briefe", Act.ARRIVALS, R.drawable.decor_lantern)),
    )

    fun of(b: IsleBuilding, level: Int): Chapter = table.single { it.building == b && it.level == level.coerceIn(0, 3) }
    val all: List<Chapter> get() = table

    /** The things of [b] at [level] that have a real handler here; the others are not drawn. */
    fun bind(b: IsleBuilding, level: Int, hooks: Map<Act, StoryHook>): List<Pair<Item, StoryHook>> =
        of(b, level).items.mapNotNull { item -> hooks[item.act]?.let { item to it } }
}

/**
 * What a painted thing does: [count] = the real number behind it (null = no number), [onClick] = the building's own
 * handler; null when there is nothing to do, then [idle] says why (read aloud, shown greyed). [accent] = a real colour the
 * thing carries (the landhaus flag in the own profile colour).
 */
internal data class StoryHook(val count: Int? = null, val onClick: (() -> Unit)?, val idle: String? = null, val accent: Color? = null)

/** Accessible description of a painted thing: its story label and the real count. */
internal fun storyDescription(item: StageStory.Item, hook: StoryHook): String =
    item.label + (hook.count?.let { " · $it" } ?: "")

/** One painted thing of the stage: at least 56 dp to tap, a badge with the real count, greyed with a reason when idle. */
@Composable
internal fun StoryThing(item: StageStory.Item, hook: StoryHook, modifier: Modifier = Modifier) {
    val enabled = hook.onClick != null
    Box(
        modifier.size(56.dp).shadow(if (enabled) 6.dp else 0.dp, CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFF6E5), Color(0xFFE9D3A6))), CircleShape)
            .border(if (hook.accent != null) 4.dp else 2.dp, hook.accent ?: Color(0xFF8A5A2B), CircleShape).clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = item.label) { hook.onClick?.invoke() }
            .semantics {
                set(SceneSlotKey, "story:${item.key}")
                contentDescription = storyDescription(item, hook)
                role = Role.Button
                if (!enabled) stateDescription = hook.idle ?: "gerade nichts zu tun"
            }
            .alpha(if (enabled) 1f else .6f),
        Alignment.Center,
    ) {
        if (item.painted) Image(painterResource(item.res), null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
        else Image(painterResource(item.res), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(Color(0xFF5A3A12)))
        val n = hook.count
        if (n != null && n > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(20.dp).background(Color(0xFFC94F3D), CircleShape)
                .border(1.dp, Color(0xFFFFF6E5), CircleShape),
            Alignment.Center,
        ) { Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 1.dp)) }
    }
}
