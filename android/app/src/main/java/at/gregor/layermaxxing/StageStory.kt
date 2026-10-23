package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 7.9.13 / 7.9.14: the stage stories (matrix of 24), told by the place itself, not by
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

    /**
     * 7.9.14: the approved style E stories. The things are line icons on a brass disc (no old painted props beside the
     * new pictures); each stage keeps the anchor of the stage before ([Item.key]).
     */
    private val table: List<Chapter> = listOf(
        c(H, 0, "Ein Nachtlager unter der alten Buche: Decke, Tasche und Laterne",
            Item("name", "Holzscheibe am Ast – Profil", Act.PROFILE, R.drawable.ico_friend, painted = false),
            Item("tools", "Tasche packen – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),
        c(H, 1, "Schräge Stämme an der Buche werden ein Unterschlupf",
            Item("name", "Scheibe unter dem Dach – Profil", Act.PROFILE, R.drawable.ico_friend, painted = false),
            Item("tools", "Querbalken – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),
        c(H, 2, "Eine Holzhütte mit Ofen, das Bild hängt an der Wand",
            Item("name", "Bild im Rahmen – Profil", Act.PROFILE, R.drawable.ico_friend, painted = false),
            Item("tools", "Werkzeug am Ofen – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),
        c(H, 3, "Ein Landhaus mit Schreibtisch, an dem alles seinen Platz hat",
            Item("name", "Fahne in deiner Profilfarbe – Profil", Act.PROFILE, R.drawable.ico_appearance, painted = false),
            Item("tools", "Pläne auf dem Schreibtisch – Insel bearbeiten", Act.EDIT, R.drawable.ico_build, painted = false)),

        c(P, 0, "Briefe werden an den Stamm der Buche genagelt",
            Item("flap", "Nagel am Stamm – ungelesener Brief", Act.UNREAD, R.drawable.ico_pin, painted = false),
            Item("seal", "Versiegelter Brief – noch gesperrt", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 1, "Die Briefe bekommen einen Kasten mit Schub",
            Item("flap", "Fahne am Kasten – ungelesener Brief", Act.UNREAD, R.drawable.ico_letter_ready, painted = false),
            Item("seal", "Versiegelter Brief – noch gesperrt", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 2, "Jeder Freund bekommt sein Fach in der Briefstube",
            Item("flap", "Fach mit neuer Post – ungelesener Brief", Act.UNREAD, R.drawable.ico_letter_ready, painted = false),
            Item("seal", "Siegelstempel – gesperrte Briefe", Act.LOCKED, R.drawable.ico_lock, painted = false)),
        c(P, 3, "Die Post der Insel läuft über den Schalter",
            Item("flap", "Klingel am Schalter – ungelesener Brief", Act.UNREAD, R.drawable.ico_bell, painted = false),
            Item("seal", "Siegel – gesperrte Briefe und Nachweis", Act.LOCKED, R.drawable.ico_seal, painted = false)),

        c(L, 0, "Oben in der Palme am Rand nach anderen Ausschau halten",
            Item("spyglass", "Fernrohr – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Flaschenpost – offene Anfragen", Act.REQUESTS, R.drawable.ico_letter, painted = false)),
        c(L, 1, "Ein fester Ausguck mit Fernrohr an der Küste",
            Item("spyglass", "Fernrohr auf dem Dreibein – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Flaschenpost am Ufer – offene Anfragen", Act.REQUESTS, R.drawable.ico_letter, painted = false)),
        c(L, 2, "Die Signalstation meldet, wer sich nähert",
            Item("spyglass", "Fernrohr auf der Mauer – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false),
            Item("signal", "Flaggen am Mast – offene Anfragen", Act.REQUESTS, R.drawable.ico_bell, painted = false)),
        c(L, 3, "Das Licht des Leuchtturms erreicht alle Freunde",
            Item("signal", "Laterne – Freunde im Strahl weiterdrehen", Act.FRIENDS, R.drawable.ico_lighthouse, painted = false),
            Item("spyglass", "Fernrohr am Geländer – Leute suchen", Act.FIND, R.drawable.ico_search, painted = false)),

        c(B, 0, "Auf dem Baumstumpf liegt das erste Buch der Wörter",
            Item("lens", "Lupe – im Buch suchen", Act.SEARCH, R.drawable.ico_search, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.ico_edit, painted = false)),
        c(B, 1, "Ein Dach schützt den Schreibplatz vor Regen",
            Item("lens", "Lupe – im Buch suchen", Act.SEARCH, R.drawable.ico_search, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.ico_edit, painted = false)),
        c(B, 2, "In der Schreibstube bekommen die Wörter ihre Kartei",
            Item("books", "Karteischubladen – nächste Kategorie", Act.CATEGORIES, R.drawable.ico_glossary, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.ico_edit, painted = false)),
        c(B, 3, "Die Sprache der Insel ist in der Bibliothek bewahrt",
            Item("books", "Banner – nächste Kategorie", Act.CATEGORIES, R.drawable.ico_library, painted = false),
            Item("quill", "Feder – Wort hinzufügen", Act.ADD_WORD, R.drawable.ico_edit, painted = false)),

        c(F, 0, "Wer sich trifft, sitzt am Lagerfeuer",
            Item("fire", "Feuer schüren – Plan für die Runde", Act.GROUP_QUEST, R.drawable.ico_campfire, painted = false),
            Item("notice", "Wimpel – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.ico_rules, painted = false)),
        c(F, 1, "Die Runde bekommt ein Dach, das Feuer brennt weiter",
            Item("fire", "Laterne über dem Tisch – Plan für die Runde", Act.GROUP_QUEST, R.drawable.ico_campfire, painted = false),
            Item("notice", "Zettel am Pfosten – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.ico_rules, painted = false)),
        c(F, 2, "Im Versammlungshaus sitzt man am Kamin zusammen",
            Item("fire", "Kamin – Plan für die Runde", Act.GROUP_QUEST, R.drawable.ico_campfire, painted = false),
            Item("notice", "Zettel am Sims – offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.ico_rules, painted = false)),
        c(F, 3, "Das Glashaus wächst um den Baum am ersten Feuer",
            Item("fire", "Ofen – Plan für die Runde", Act.GROUP_QUEST, R.drawable.ico_campfire, painted = false),
            Item("notice", "Erledigte und offene Gruppenpläne", Act.GROUP_PLANS, R.drawable.ico_rules, painted = false)),

        c(R2, 0, "Ein kurzer Steg vom Ufer: hier legen Briefboote an",
            Item("pennant", "Treibholzbrett – Themen", Act.TOPICS, R.drawable.ico_topics, painted = false),
            Item("arrival", "Briefboot am Steg – angekommene Briefe", Act.ARRIVALS, R.drawable.ico_letter_ready, painted = false)),
        c(R2, 1, "Der Bootssteg bekommt eine Anschlagtafel",
            Item("pennant", "Tafel unter dem Dach – Themen", Act.TOPICS, R.drawable.ico_topics, painted = false),
            Item("arrival", "Briefboot am Stegende – angekommene Briefe", Act.ARRIVALS, R.drawable.ico_letter_ready, painted = false)),
        c(R2, 2, "Am Anleger mit Schuppen machen mehrere Briefboote fest",
            Item("bollard", "Poller – offene Quests", Act.OPEN_QUESTS, R.drawable.ico_q_star, painted = false),
            Item("pennant", "Laterne am Schuppen – Themen", Act.TOPICS, R.drawable.ico_topics, painted = false)),
        c(R2, 3, "Im Hafen laufen Briefe und Aufgaben zusammen",
            Item("pennant", "Kompass am Kai – Themen", Act.TOPICS, R.drawable.ico_compass, painted = false),
            Item("arrival", "Laternenpoller – angekommene Briefe", Act.ARRIVALS, R.drawable.ico_letter_ready, painted = false)),
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
    // 7.9.14: the count badge sits on an outer box, so the round clip of the thing never cuts its digits
    Box(modifier.size(60.dp)) { Box(
        Modifier.align(Alignment.Center).size(56.dp).shadow(if (enabled) 6.dp else 0.dp, CircleShape)
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
    }
        val n = hook.count
        if (n != null && n > 0) Box(
            Modifier.align(Alignment.TopEnd).defaultMinSize(22.dp, 22.dp).background(Color(0xFFC94F3D), RoundedCornerShape(50))
                .border(1.dp, Color(0xFFFFF6E5), RoundedCornerShape(50)).padding(horizontal = 4.dp).testTag("story-badge"),
            Alignment.Center,
        ) { Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
    }
}
