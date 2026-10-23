package at.gregor.layermaxxing

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

internal object IslandEvolution {
    fun primitiveName(b: IsleBuilding) = IslandStages.of(b, 0).name
    fun levelName(b: IsleBuilding, level: Int, standing: Boolean = level > 0) = "${IslandStages.of(b, level).name} · " + if (!standing) "noch nicht gebaut" else IslandStages.stageLabel(level)
    fun allowed(c: ApiClient.IslandConstruction, cost: Int, days: Int) = c.mode == "creative" || c.available >= cost && c.ageDays >= days
}

/**
 * The picture of a building at a stage on an island: 7.9.14 the style E art ([IslandStages.Stage.art]) wherever the
 * island follows the ground rule (grid_version 6, the art was fitted to its ground rectangles) or has no construction
 * ledger; an island of a server that still runs rule 5 or older keeps the 7.9.13 picture its masks were measured from.
 */
internal object IslandArtwork {
    fun resource(building: IsleBuilding, tier: Int, c: ApiClient.IslandConstruction? = null): Int =
        IslandStages.of(building, tier).let { if (c == null || IslandLayout.ground(c)) it.art else it.res }
    fun accent(building: IsleBuilding, tier: Int): Int? = IslandStages.of(building, tier).accent
}

/**
 * 7.9.14: island sprites decoded once and shared by every place that shows them (the island, the header, the upgrade
 * preview, the build shelf), in an LRU cache with a byte budget instead of painterResource (which decodes again per call
 * site, on the main thread). The budget holds every standing building of an island and its previews (a style E sprite
 * is at most 704 × 704 px = 1.98 MB as ARGB); older ones are dropped, so all 24 never stay in memory together.
 */
internal object IslandSprites {
    const val BUDGET_BYTES = 16 * 1024 * 1024
    private val cache = object : android.util.LruCache<Int, androidx.compose.ui.graphics.ImageBitmap>(BUDGET_BYTES) {
        override fun sizeOf(key: Int, value: androidx.compose.ui.graphics.ImageBitmap): Int = value.width * value.height * 4
    }
    private val decoded = java.util.concurrent.atomic.AtomicInteger()
    /** How many sprites were really decoded (tests). */
    val decodes: Int get() = decoded.get()
    /** Bytes the cache holds now (tests). */
    val bytes: Int get() = cache.size()
    fun of(res: android.content.res.Resources, id: Int): androidx.compose.ui.graphics.ImageBitmap =
        cache.get(id) ?: synchronized(this) {
            cache.get(id) ?: androidx.compose.ui.graphics.ImageBitmap.imageResource(res, id).also { decoded.incrementAndGet(); cache.put(id, it) }
        }
    fun clear() = cache.evictAll()
}

@Composable
internal fun islandSprite(id: Int): androidx.compose.ui.graphics.ImageBitmap {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return remember(id, res) { IslandSprites.of(res, id) }
}

/**
 * Same production artwork for the island and its upgrade preview. A style E sprite ([StyleEArt]) is a whole picture
 * (no accent or flag added) whose canvas is [StyleEArt.Frame.scale] times the box: on the island ([onGround]) it is
 * drawn that big with its bottom middle on the foot (crowns and roofs may reach over the box, the ground is the box's
 * plate); anywhere else (header, preview, shelf) the canvas is fitted into the box.
 */
@Composable
internal fun PaintedIslandPlace(building: IsleBuilding?, tier: Int, resource: Int, modifier: Modifier = Modifier, description: String? = null,
                                onGround: Boolean = false) {
    val frame = StyleEArt.byRes(resource)
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (frame != null) {
            val bmp = islandSprite(resource)
            if (onGround) BoxWithConstraints(Modifier.fillMaxSize()) {
                Image(bmp, description, Modifier.align(Alignment.BottomCenter).wrapContentSize(Alignment.BottomCenter, unbounded = true)
                    .size(maxWidth * frame.scale), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
            } else Image(bmp, description, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.BottomCenter)
            return@Box
        }
        Image(islandSprite(resource), description, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.BottomCenter)
        building?.let { IslandArtwork.accent(it, tier) }?.let { accent ->
            Image(islandSprite(accent), null, Modifier.fillMaxSize(.38f).align(if (building == IsleBuilding.LIBRARY) Alignment.TopCenter else Alignment.CenterEnd), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
        if (tier == 3 && building != null) Image(islandSprite(R.drawable.decor_flag), null,
            Modifier.fillMaxSize(.30f).align(Alignment.TopEnd), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}

@Composable
internal fun AnimatedIslandSea(modifier: Modifier = Modifier, oversized: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduced = remember(context) { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    val transition=rememberInfiniteTransition(label="sea-current")
    val t by transition.animateFloat(0f,1f,infiniteRepeatable(tween(12000,easing=LinearEasing)),label="sea-drift")
    val brush=rememberSeaBrush()
    Canvas(modifier) {
        val margin=if(oversized) 3f else 0f
        drawRect(brush,Offset(-size.width*margin,-size.height*margin),Size(size.width*(1+2*margin),size.height*(1+2*margin)))
        val phase=if(reduced) 0f else t
        for(row in -12..16) for(col in -8..12) {
            val x=col*size.width*.17f+sin(row*.8f)*size.width*.06f+phase*size.width*.17f
            val y=row*size.height*.085f+sin(phase*6.28f+col*.7f)*size.height*.007f
            val a=.10f+.06f*sin(phase*6.28f+row)
            drawLine(Color.White.copy(alpha=a),Offset(x,y),Offset(x+size.width*.045f,y-2f),2f,StrokeCap.Round)
        }
    }
}

@Composable
internal fun IslandUpgradeContent(
    building: IsleBuilding?, island: ApiClient.HomeIsland, busy: Boolean,
    onAction: (String) -> Unit, onDismiss: () -> Unit,
) {
    val c=island.construction
    val level=building?.let { c.level(it) } ?: c.land
    // 7.9.9: a function that does not stand yet is built at its free stage 0 first.
    val standing=building?.let { c.standing(it) } ?: true
    val max=if(building==null) 5 else 3
    val costs=if(building==null) c.landCosts else c.buildCosts
    val days=if(building==null) c.landDays else c.buildDays
    val cost=costs.getOrElse(level) { 0 }; val day=days.getOrElse(level) { 0 }
    val creative=c.mode=="creative"
    // 7.9.10: the upgrade sheet in the island's painted family: brass plate title, two paper frames
    // (now → next) with the real stage art, cost chips, one brass main button. Labels and actions unchanged.
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        val title=building?.let { if (standing) "${IslandStages.of(it, level).name} ausbauen" else "${IslandStages.of(it, 0).name} bauen" } ?: "Insel vergrößern"
        UpgradeTitlePlate(title)
        Text(if(creative) "Kreativmodus · kostenlos auf deiner echten Insel" else "${c.available} Punkte verfügbar · Tag ${c.ageDays}",
            color=IslandWood.InkSoft, fontFamily=Kit.Body, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, fontSize=13.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            if(building!=null && !standing) {
                BuildingPreview(building, 0, Modifier.fillMaxWidth(.56f), next=true)
            } else if(building!=null) {
                BuildingPreview(building, level, Modifier.weight(1f))
                if(level<max) { UpgradeArrow(); BuildingPreview(building, level+1, Modifier.weight(1f), next=true) }
            } else {
                LandPreview(Modifier.weight(1f)) { DynamicIsland(IslandPlans.home(1,0,c),Modifier.fillMaxWidth(.9f),animate=false) }
                if(level<max) { UpgradeArrow(); LandPreview(Modifier.weight(1f), next=true) { DynamicIsland(IslandPlans.home(1,0,c.copy(land=level+1)),Modifier.fillMaxWidth(),animate=false) } }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(building!=null) { StagePips(if (standing) level else 0); Spacer(Modifier.width(6.dp)) }
            Text(if(building==null) "Landstufe $level / $max" else IslandEvolution.levelName(building,level,standing),color=IslandWood.Ink,
                fontFamily=Kit.Body, fontWeight=androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize=14.sp)
        }
        if(!standing) {
            CostLine(listOf("gratis" to true), "Start: gratis · Ausbau ab Stufe 1 kostet Punkte.")
            IslandWoodButton(if(busy) "Wird gespeichert…" else "Gratis bauen", { onAction("upgrade") }, Modifier.fillMaxWidth(), enabled=!busy)
        } else if(level<max) {
            // 7.9.12: the next stage's bigger picture must fit where the building stands, checked before paying
            val room=building?.let { IslandLayout.upgradeProblem(c, it) }
            val allowed=IslandEvolution.allowed(c,cost,day) && room==null
            CostLine(
                if(creative) listOf("gratis" to true) else listOfNotNull("$cost P" to (c.available>=cost), ("ab Tag $day" to (c.ageDays>=day)).takeIf { day>0 }),
                when {
                    creative -> "Nächster Ausbau: kostenlos"
                    c.ageDays<day -> "Möglich ab Tag $day · heute ist Tag ${c.ageDays}"
                    c.available<cost -> "Es fehlen ${cost-c.available} Punkte · Punkte kommen aus bestätigten Quests"
                    else -> "Nächster Ausbau: $cost Punkte. Punkte werden beim Bauen verbraucht."
                })
            IslandWoodButton(if(busy) "Wird gespeichert…" else if(building==null) "Land erweitern" else if(level==0) "Gebäude bauen" else "Auf Stufe ${level+1} ausbauen",
                { onAction(if(building==null) "expand" else "upgrade") }, Modifier.fillMaxWidth(), enabled=!busy && allowed)
            if(room!=null) {
                Text(room, color=Color(0xFF8C2F22), fontFamily=Kit.Body, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, fontSize=13.sp,
                    modifier=Modifier.semantics { contentDescription="Kein Platz: $room" })
                // 7.9.13: hold the next stage and pick its spot (e.g. the rowboat's pier at the water); one upgrade on ✓
                IslandWoodButton("Platz für die nächste Stufe wählen", { onAction("move") }, Modifier.fillMaxWidth(), enabled=!busy && IslandEvolution.allowed(c,cost,day), secondary=true)
            }
        } else Row(Modifier.background(IslandWood.Paper, androidx.compose.foundation.shape.RoundedCornerShape(50)).border(1.5.dp, IslandWood.Moss, androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal=14.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
            AppIcon(R.drawable.ico_check, null, tint=IslandWood.Moss, size=16.dp); Spacer(Modifier.width(6.dp))
            Text("Vollständig ausgebaut",color=IslandWood.Moss, fontFamily=Kit.Display, fontSize=16.sp)
        }
        if(creative && (if (building==null) level>0 else standing)) IslandWoodButton(if(building==null) "Land kostenlos verkleinern" else "Kostenlos zurückbauen",
            { onAction(if(building==null) "shrink" else "downgrade") }, Modifier.fillMaxWidth(), enabled=!busy, secondary=true)
        TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text("Schließen", color=IslandWood.InkSoft, fontFamily=Kit.Body, fontWeight=androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize=15.sp)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * 7.9.10: the brass title plate of the upgrade sheet. p_plate.webp (300 px wide) is stretched over the plate and
 * its rivets reach 48 px (16 %) in from each end, so the title only uses the middle of the plate ([INSET] per side).
 * The plate grows with the title up to the sheet width; a title that still does not fit is set smaller (measured,
 * down to [MIN_SP]) and only then wraps onto two lines at the largest size where no word is split ([MIN_WRAP_SP]
 * at the least; the plate grows in height). 7.9.10 before the render review: 28 dp padding, the rivets covered P and n.
 */
internal object UpgradeTitle {
    const val INSET = .19f
    val MIN_WIDTH = 230.dp
    val MIN_HEIGHT = 58.dp
    const val MAX_SP = 20f
    const val MIN_SP = 15f
    /** Smallest size of a title that has to wrap anyway (a large system font on a narrow phone; scaled by it). */
    const val MIN_WRAP_SP = 11f
    const val PLATE_TAG = "upgrade-title-plate"
}

@Composable
internal fun UpgradeTitlePlate(title: String, modifier: Modifier = Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Measured and drawn with the same style (incl. the theme's letter spacing).
    val style = LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(fontFamily = Kit.Display, color = IslandWood.Ink,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxPlate = maxWidth
        val safeShare = 1f - 2 * UpgradeTitle.INSET
        val (size, plate) = remember(title, maxPlate, density, style) {
            fun lineWidth(sp: Float) = with(density) {
                measurer.measure(title, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width.toDp()
            }
            val maxSafe = maxPlate * safeShare
            val oneLine = generateSequence(UpgradeTitle.MAX_SP) { it - 1f }.takeWhile { it >= UpgradeTitle.MIN_SP }
                .firstOrNull { lineWidth(it) + 2.dp <= maxSafe }
            if (oneLine != null) oneLine to ((lineWidth(oneLine) + 2.dp) / safeShare).coerceIn(minOf(UpgradeTitle.MIN_WIDTH, maxPlate), maxPlate)
            else {
                // Two lines on the full-width plate: the largest size at which no word is split and nothing is cut off.
                val widePx = with(density) { (maxSafe - 2.dp).roundToPx() }.coerceAtLeast(1)
                val words = title.split(' ').filter { it.isNotEmpty() }
                val sp = generateSequence(UpgradeTitle.MAX_SP) { it - 1f }.takeWhile { it >= UpgradeTitle.MIN_WRAP_SP }.firstOrNull { sp ->
                    words.all { w -> measurer.measure(w, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= widePx } &&
                        !measurer.measure(title, style.copy(fontSize = sp.sp), maxLines = 2,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = widePx)).hasVisualOverflow
                } ?: UpgradeTitle.MIN_WRAP_SP
                sp to maxPlate
            }
        }
        Box(Modifier.width(plate).heightIn(min = UpgradeTitle.MIN_HEIGHT).shadow(5.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .testTag(UpgradeTitle.PLATE_TAG), Alignment.Center) {
            Image(painterResource(R.drawable.p_plate), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
            Text(title, style = style.copy(fontSize = size.sp, lineHeight = (size * 1.1f).sp), maxLines = 2,
                modifier = Modifier.width(plate * safeShare).padding(vertical = 10.dp))
        }
    }
}

/** Brass coin with the growth icon between "now" and "next". */
@Composable
private fun UpgradeArrow() {
    Box(Modifier.padding(horizontal=6.dp).size(34.dp).shadow(3.dp, androidx.compose.foundation.shape.CircleShape)
        .background(IslandWood.Brass, androidx.compose.foundation.shape.CircleShape).border(1.5.dp, IslandWood.Ink.copy(alpha=.6f), androidx.compose.foundation.shape.CircleShape),
        Alignment.Center) { AppIcon(R.drawable.ico_growth, null, tint=IslandWood.Ink, size=18.dp) }
}

/** Cost chips (red-brown when not reached yet) and one explaining line under them. */
@Composable
private fun CostLine(chips: List<Pair<String, Boolean>>, line: String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            chips.forEach { (text, ok) ->
                if (ok) PriceChip(text) else Text(text, color=IslandBuild.Cream, fontSize=10.sp, fontFamily=Kit.Body, fontWeight=androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    maxLines=1, softWrap=false, modifier=Modifier.background(IslandWood.Brick, androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .border(1.dp, Color(0x885A3A1A), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal=7.dp, vertical=1.dp))
            }
        }
        Text(line, color=IslandWood.InkSoft, fontFamily=Kit.Body, fontSize=12.5.sp, textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** One paper frame of the comparison; [next] = the stage the button builds (brass rim). */
@Composable
private fun PreviewFrame(modifier: Modifier, next: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Column(modifier.shadow(if (next) 5.dp else 2.dp, shape).background(IslandWood.Paper, shape)
        .border(if (next) 2.5.dp else 1.5.dp, if (next) IslandWood.Brass else androidx.compose.ui.graphics.SolidColor(IslandWood.PaperEdge.copy(alpha=.6f)), shape)
        .padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally, content=content)
}

@Composable
private fun LandPreview(modifier: Modifier, next: Boolean = false, island: @Composable () -> Unit) {
    PreviewFrame(modifier, next) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Isle.SeaTop.copy(alpha=.55f), Color.Transparent))), Alignment.Center) { island() }
    }
}

/**
 * 7.9.10: height of the painted stage in the upgrade comparison. It follows the frame's width (taller than
 * wide, like the sprites), but never drops below [MIN] (the art drawn at 92 % of it stays >= 160 dp on a
 * narrow phone, where two frames and the arrow share one row) and never grows beyond [MAX] (the sheet scrolls).
 */
internal object UpgradePreview {
    val MIN = 180.dp
    val MAX = 240.dp
    const val ART = .92f
    fun height(width: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = (width * 1.25f).coerceIn(MIN, MAX)
}

@Composable
private fun BuildingPreview(building: IsleBuilding, level: Int, modifier: Modifier = Modifier, next: Boolean = false) {
    val stage = IslandStages.of(building, level)
    PreviewFrame(modifier, next) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(UpgradePreview.height(maxWidth)).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x557FA2A3), Color.Transparent))), Alignment.BottomCenter) {
                PaintedIslandPlace(building, level, stage.art, Modifier.fillMaxSize(UpgradePreview.ART), description = "Ausbauvorschau: ${stage.name}")
            }
        }
        Text(stage.name, color = IslandWood.Ink, fontFamily = Kit.Display, fontSize = 16.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StagePips(level); Spacer(Modifier.width(5.dp))
            Text(if (level == 0) "Start" else "Stufe $level", color = IslandWood.InkSoft, fontFamily = Kit.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Text(stage.look, color = IslandWood.InkSoft, fontFamily = Kit.Body, fontSize = 11.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
