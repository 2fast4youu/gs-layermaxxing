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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun VillageTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val base = Typography()
    fun TextStyle.body() = copy(fontFamily = Kit.Body)
    fun TextStyle.display() = copy(fontFamily = Kit.Display, fontWeight = FontWeight.Normal)
    MaterialTheme(
        colorScheme = lightColorScheme(
            // Island rooms: harbour paper with the muted Nordic teal of the map – no plain white.
            primary = Color(0xFF3F7C78), onPrimary = Color.White,
            primaryContainer = Color(0xFFD5ECE6), onPrimaryContainer = Color(0xFF173A40),
            secondary = Color(0xFF2F5D5A), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEFE3CA), onSecondaryContainer = Color(0xFF3B3122),
            tertiary = Color(0xFFC9A55C), onTertiary = Color.White,
            background = Color(0xFFFBF3E2), onBackground = Color(0xFF1F3B4D),
            surface = Color(0xFFFBF3E2), onSurface = Color(0xFF1F3B4D),
            surfaceVariant = Color(0xFFF1E6CE), onSurfaceVariant = Color(0xFF6B6250),
            outline = Color(0xFFC9B892), outlineVariant = Color(0xFFE6D9BC),
            surfaceContainer = Color(0xFFF7EEDA), surfaceContainerHigh = Color(0xFFF3E8D1),
            surfaceContainerLow = Color(0xFFFAF2E2), surfaceContainerLowest = Color(0xFFFFFBF2),
            surfaceContainerHighest = Color(0xFFEFE3CA), surfaceDim = Color(0xFFE9DDC3), surfaceBright = Color(0xFFFFFBF2),
        ),
        typography = Typography(
            displayLarge = base.displayLarge.display(), displayMedium = base.displayMedium.display(), displaySmall = base.displaySmall.display(),
            headlineLarge = base.headlineLarge.display(), headlineMedium = base.headlineMedium.display(), headlineSmall = base.headlineSmall.display(),
            titleLarge = base.titleLarge.display(), titleMedium = base.titleMedium.body().copy(fontWeight = FontWeight.ExtraBold),
            titleSmall = base.titleSmall.body().copy(fontWeight = FontWeight.ExtraBold),
            bodyLarge = base.bodyLarge.body(), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
            labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.ExtraBold),
            labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Bold), labelSmall = base.labelSmall.body(),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
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
    icon: Int = R.drawable.ico_more,
    title: String = "",
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) { content(); return }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val rise by animateFloatAsState(if (entered) 1f else 0f, spring(dampingRatio = .78f, stiffness = 380f), label = "room-rise")
    Box(Modifier.fillMaxSize()) {
        if (!mapBehind) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Isle.SeaTop, Isle.SeaBottom))))
        // A light sea veil: the island stays visible around the open building.
        Box(Modifier.fillMaxSize().background(Color(0x5517555A)).pointerInput(Unit) {
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
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Isle.Card, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
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
        VillageActivity(ValleyDestination.EP, requests, "Offene Punkte-Vorschläge"),
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
