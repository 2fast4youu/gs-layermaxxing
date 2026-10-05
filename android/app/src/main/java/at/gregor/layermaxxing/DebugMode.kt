package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Developer switch (Mehr → Darstellung → Debug-Modus). Off by default: the painted
 * world shows no tap rectangles at all. On: every world hitbox is outlined so wrong
 * touch targets are easy to spot.
 */
internal val LocalHitboxDebug = compositionLocalOf { false }

internal object DebugMode {
    /** App-wide live state of the switch, so toggling in settings updates the world immediately. */
    val hitboxes = androidx.compose.runtime.mutableStateOf(false)
    const val PREF_KEY = "debug_hitboxes"
    val HitboxFill = Color(0x33FF1744)
    val HitboxLine = Color(0xCCFF1744)

    /** Ripples/press highlights are a debug aid in the painted world, never part of the normal look. */
    fun showsHitboxes(debug: Boolean): Boolean = debug
}

/**
 * Tap target inside the painted world (buildings, islands, boats, scene regions).
 * Normal mode: invisible, no grey press rectangle. Debug mode: red outline + tint.
 */
internal fun Modifier.worldTap(label: String? = null, onClick: () -> Unit): Modifier = composed {
    val debug = LocalHitboxDebug.current
    val source = remember { MutableInteractionSource() }
    this
        .then(if (DebugMode.showsHitboxes(debug)) Modifier.background(DebugMode.HitboxFill).border(1.5.dp, DebugMode.HitboxLine) else Modifier)
        .clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = onClick)
}

@Composable
internal fun rememberHitboxDebug(): Boolean = LocalHitboxDebug.current
