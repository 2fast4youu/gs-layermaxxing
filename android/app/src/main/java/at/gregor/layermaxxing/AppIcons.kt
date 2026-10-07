package at.gregor.layermaxxing

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one icon language: thin rounded line icons (Lucide, ISC licence),
 * generated as vector drawables `ico_*` by docs/icons/build_icons.py and tinted
 * by the surrounding colour. Emoji stay only where they are content the user
 * picks (avatars, reactions, stickers) – never as interface symbols.
 */
@Composable
internal fun AppIcon(res: Int, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

internal object AppIcons {
    /** Quest kinds: one line icon per kind, the same in every quest list and form. */
    val quest: Map<String, Int> = linkedMapOf(
        "hike" to R.drawable.ico_q_hike, "grill" to R.drawable.ico_q_grill, "bike" to R.drawable.ico_q_bike,
        "food" to R.drawable.ico_q_food, "game" to R.drawable.ico_q_game, "travel" to R.drawable.ico_q_travel,
        "sport" to R.drawable.ico_q_sport, "music" to R.drawable.ico_q_music, "help" to R.drawable.ico_q_help,
        "star" to R.drawable.ico_q_star,
    )

    fun questIcon(key: String): Int = quest[key] ?: R.drawable.ico_q_star

    val questLabels: Map<String, String> = mapOf(
        "hike" to "Wandern", "grill" to "Grillen", "bike" to "Radfahren", "food" to "Essen", "game" to "Spielen",
        "travel" to "Reisen", "sport" to "Sport", "music" to "Musik", "help" to "Helfen", "star" to "Sonstiges",
    )

    fun tab(t: MainTab): Int = when (t) {
        MainTab.CHATS -> R.drawable.ico_chats
        MainTab.MORE -> R.drawable.ico_more
        MainTab.CASTLES -> R.drawable.ico_island
    }

    /** Island bar and HUD. */
    val bar: Map<String, Int> = mapOf(
        "map" to R.drawable.ico_map, "home" to R.drawable.ico_island, "harbour" to R.drawable.ico_harbour,
        "chats" to R.drawable.ico_chats, "done" to R.drawable.ico_check,
    )

    fun building(b: IsleBuilding): Int = when (b) {
        IsleBuilding.HOUSE -> R.drawable.ico_house
        IsleBuilding.LIGHTHOUSE -> R.drawable.ico_lighthouse
        IsleBuilding.POST -> R.drawable.ico_letter
        IsleBuilding.LIBRARY -> R.drawable.ico_glossary
        IsleBuilding.CAMPFIRE -> R.drawable.ico_groups
        IsleBuilding.HARBOUR -> R.drawable.ico_harbour
    }

    /** Leading icon for a one-line preview (chat list, pinned bar, quotes); null = plain text. */
    fun previewIcon(text: String, sealed: Boolean): Int? = when {
        sealed -> R.drawable.ico_letter
        text == "Foto" || text.startsWith("Foto · ") -> R.drawable.ico_camera
        text == "Flaschenpost" || text.startsWith("Flaschenpost · ") -> R.drawable.ico_mic
        text.startsWith("Sticker") -> R.drawable.ico_emoji
        text == "Nachricht gelöscht" -> R.drawable.ico_delete
        else -> null
    }
}
