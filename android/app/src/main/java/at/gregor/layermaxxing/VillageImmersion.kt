package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun VillageTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3F7D22), onPrimary = Color(0xFFFFFBEA),
            primaryContainer = Color(0xFFCBE6A8), onPrimaryContainer = Color(0xFF1F3B0E),
            secondary = Color(0xFF80522D), onSecondary = Color(0xFFFFF2D8),
            secondaryContainer = Color(0xFFE5C994), onSecondaryContainer = Color(0xFF49331F),
            background = Color(0xFFF0DCAF), onBackground = Color(0xFF392C20),
            surface = Color(0xFFF7E6C2), onSurface = Color(0xFF392C20),
            surfaceVariant = Color(0xFFE5CFA3), onSurfaceVariant = Color(0xFF624A33),
            outline = Color(0xFF93704A), outlineVariant = Color(0xFFC5A675),
            surfaceContainer = Color(0xFFEEDAB3), surfaceContainerHigh = Color(0xFFE9D0A4),
            surfaceContainerLow = Color(0xFFF2DDB9), surfaceContainerLowest = Color(0xFFFFF0D3),
            surfaceContainerHighest = Color(0xFFE5CCA0), surfaceDim = Color(0xFFE1C694), surfaceBright = Color(0xFFF7E6C2),
        ),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
        content = content,
    )
}

/**
 * A building's interior: the painted village stays visible and dimmed behind a
 * parchment room in an oak frame, with the building's medallion and name on top.
 * The same frame for every building, so entering one never feels like leaving.
 */
@Composable
internal fun VillageRoom(
    enabled: Boolean,
    mapBehind: Boolean = false,
    icon: Int = R.drawable.ic_settings,
    title: String = "",
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) { content(); return }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val rise by animateFloatAsState(if (entered) 1f else 0f, spring(dampingRatio = .78f, stiffness = 380f), label = "room-rise")
    Box(Modifier.fillMaxSize()) {
        if (!mapBehind) Image(painterResource(R.drawable.village_plate), null, Modifier.fillMaxSize().blur(2.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x99140A04)).pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent() }
        })
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationY = (1f - rise) * 80.dp.toPx(); alpha = rise
                    scaleX = .94f + .06f * rise; scaleY = .94f + .06f * rise
                },
        ) {
            RoomHeader(icon, title, onBack = null, onClose = onClose)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(Kit.woodBrush, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    .background(Kit.parchmentBrush, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
            ) { CompositionLocalProvider(LocalInVillageRoom provides true) { content() } }
        }
    }
}

internal val LocalInVillageRoom = staticCompositionLocalOf { false }

data class VillageActivity(val destination: ValleyDestination, val count: Int, val label: String)
object VillageActivityModel {
    fun collect(unread: Int, topics: Int, groups: Int, sparks: Int, letters: Int, requests: Int, friendRequests: Int = 0, rules: Int = 0) = listOf(
        VillageActivity(ValleyDestination.CONVERSATIONS, unread, "Neue Nachrichten"),
        VillageActivity(ValleyDestination.TOPICS, topics, "Offene Themen"),
        VillageActivity(ValleyDestination.GROUPS, groups, "Gruppen"),
        VillageActivity(ValleyDestination.SPARKS, sparks, "Ungeöffnete Funken"),
        VillageActivity(ValleyDestination.ARCHIVE, letters, "Briefe: bereit oder Zustimmung nötig"),
        VillageActivity(ValleyDestination.EP, requests, "Offene EP-Vorschläge"),
        VillageActivity(ValleyDestination.PEOPLE, friendRequests, "Freundschaftsanfragen"),
        VillageActivity(ValleyDestination.CONVERSATIONS, rules, "Regelvorschläge"),
    ).filter { it.count > 0 }.groupBy { it.destination }.map { (destination, items) ->
        VillageActivity(destination, items.sumOf { it.count }, items.joinToString(" · ") { it.label })
    }
    fun courierPoint(position: Float): MapPoint {
        val t = position.coerceIn(0f, 1f)
        if (t == 0f) return VillageScenes.HOME
        if (t == 1f) return VillageScenes.FRIEND
        // Route along the actual village path: cottage -> central crossroads -> friend.
        val mid = MapPoint(.51f, .72f)
        val a = if (t < .5f) VillageScenes.HOME else mid
        val b = if (t < .5f) mid else VillageScenes.FRIEND
        val f = if (t < .5f) t * 2 else (t - .5f) * 2
        return MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }
}
