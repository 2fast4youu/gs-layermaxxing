package at.gregor.layermaxxing

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

internal object IslandEvolution {
    fun primitiveName(b: IsleBuilding) = when(b) {
        IsleBuilding.HOUSE -> "Schlafplatz"
        IsleBuilding.POST -> "Briefbaum"
        IsleBuilding.LIGHTHOUSE -> "Aussichtspalme"
        IsleBuilding.LIBRARY -> "Wörter am Treibholz"
        IsleBuilding.CAMPFIRE -> "Feuerkreis"
        IsleBuilding.HARBOUR -> "Strandtreff"
    }
    fun levelName(b: IsleBuilding, level: Int) = if (level == 0) "${b.label} · noch nicht gebaut" else when(level) {
        1 -> "${b.label} · Holzbau"
        2 -> "${b.label} · ausgebaut"
        else -> "${b.label} · Meisterbau"
    }
    fun allowed(c: ApiClient.IslandConstruction, cost: Int, days: Int) = c.mode == "creative" || c.available >= cost && c.ageDays >= days
}

/** Reuse Gregor's painted sprite family at EVERY stage, including the natural starter. */
internal object IslandArtwork {
    fun resource(building: IsleBuilding, tier: Int): Int = when (tier.coerceIn(0, 3)) {
        0 -> when (building) {
            IsleBuilding.HOUSE -> R.drawable.decor_hammock
            IsleBuilding.POST, IsleBuilding.LIGHTHOUSE -> R.drawable.decor_palm
            IsleBuilding.LIBRARY -> R.drawable.n_crates
            IsleBuilding.CAMPFIRE -> R.drawable.decor_campfire
            IsleBuilding.HARBOUR -> R.drawable.n_rowboat
        }
        1 -> when (building) {
            IsleBuilding.HOUSE -> R.drawable.fief_hut_stage1
            IsleBuilding.POST -> R.drawable.lm_post
            IsleBuilding.LIGHTHOUSE -> R.drawable.decor_lantern
            IsleBuilding.LIBRARY -> R.drawable.lm_library
            IsleBuilding.CAMPFIRE -> R.drawable.b_workshop
            IsleBuilding.HARBOUR -> R.drawable.b_jetty
        }
        2 -> when (building) {
            IsleBuilding.HOUSE -> R.drawable.b_house
            IsleBuilding.POST -> R.drawable.b_post
            IsleBuilding.LIGHTHOUSE -> R.drawable.lm_lighthouse
            IsleBuilding.LIBRARY -> R.drawable.b_library
            IsleBuilding.CAMPFIRE -> R.drawable.b_hall
            IsleBuilding.HARBOUR -> R.drawable.lm_jetty
        }
        else -> when (building) {
            IsleBuilding.HOUSE -> R.drawable.fief_hut_stage3
            IsleBuilding.POST -> R.drawable.b_post
            IsleBuilding.LIGHTHOUSE -> R.drawable.b_lighthouse
            IsleBuilding.LIBRARY -> R.drawable.b_library
            IsleBuilding.CAMPFIRE -> R.drawable.b_hall
            IsleBuilding.HARBOUR -> R.drawable.lm_jetty
        }
    }

    fun accent(building: IsleBuilding, tier: Int): Int? = if (tier == 0) when (building) {
        IsleBuilding.POST -> R.drawable.p_env
        IsleBuilding.LIBRARY -> R.drawable.p_book
        IsleBuilding.HARBOUR -> R.drawable.decor_flag
        else -> null
    } else null
}

/** Same production artwork for the island and its upgrade preview. */
@Composable
internal fun PaintedIslandPlace(building: IsleBuilding?, tier: Int, resource: Int, modifier: Modifier = Modifier, description: String? = null) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Image(painterResource(resource), description, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.BottomCenter)
        building?.let { IslandArtwork.accent(it, tier) }?.let { accent ->
            Image(painterResource(accent), null, Modifier.fillMaxSize(.38f).align(if (building == IsleBuilding.LIBRARY) Alignment.TopCenter else Alignment.CenterEnd), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
        if (tier == 3 && building != null) Image(painterResource(R.drawable.decor_flag), null,
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
    onMove: () -> Unit = {}, onOpen: () -> Unit, onAction: (String) -> Unit, onDismiss: () -> Unit,
) {
    val c=island.construction
    val level=building?.let { c.buildings[it.name] ?: 0 } ?: c.land
    val max=if(building==null) 5 else 3
    val costs=if(building==null) c.landCosts else c.buildCosts
    val days=if(building==null) c.landDays else c.buildDays
    val cost=costs.getOrElse(level) { 0 }; val day=days.getOrElse(level) { 0 }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(building?.label ?: "Insel vergrößern",fontSize=22.sp,color=Isle.Ink)
        Text(if(c.mode=="creative") "Kreativinsel · kostenlos und unabhängig von deinem Fortschritt" else "${c.available} Punkte verfügbar · Tag ${c.ageDays}",color=Isle.Muted,fontSize=13.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            if(building!=null) {
                BuildingPreview(building,level)
                Text("→",color=Isle.Muted,fontSize=24.sp)
                BuildingPreview(building,(level+1).coerceAtMost(max))
            } else {
                DynamicIsland(IslandPlans.home(1,0,c),Modifier.width(125.dp),animate=false)
                Text("→",color=Isle.Muted,fontSize=24.sp)
                DynamicIsland(IslandPlans.home(1,0,c.copy(land=(level+1).coerceAtMost(max))),Modifier.width(145.dp),animate=false)
            }
        }
        Text(if(building==null) "Landstufe $level / $max" else IslandEvolution.levelName(building,level),color=Isle.Ink)
        if(building!=null) Button(onClick=onOpen,modifier=Modifier.fillMaxWidth(),enabled=!busy) { Text("${building.label} öffnen") }
        if(building!=null && level>0) OutlinedButton(onClick=onMove,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Verschieben") }
        if(level<max) {
            Text(if(c.mode=="creative") "Nächster Ausbau: kostenlos" else "Nächster Ausbau: $cost Punkte · ab Tag $day. Punkte werden beim Bauen verbraucht.",color=Isle.Muted,fontSize=13.sp)
            Button(onClick={onAction(if(building==null) "expand" else "upgrade")},enabled=!busy && IslandEvolution.allowed(c,cost,day),modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Wird gespeichert…" else if(building==null) "Land erweitern" else if(level==0) "Gebäude bauen" else "Auf Stufe ${level+1} ausbauen")
            }
        } else Text("Vollständig ausgebaut",color=Isle.TealDark)
        if(c.mode=="creative" && level>0) OutlinedButton(onClick={onAction(if(building==null) "shrink" else "downgrade")},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(building==null) "Land kostenlos verkleinern" else "Kostenlos zurückbauen") }
        TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Schließen") }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BuildingPreview(building: IsleBuilding,level: Int) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        if (level > 0) PaintedIslandPlace(building, level, IslandArtwork.resource(building, level), Modifier.size(76.dp))
        else Box(Modifier.size(76.dp), contentAlignment=Alignment.Center) { Text("Leer", color=Isle.Muted) }
        Text(if(level==0) "Nicht gebaut" else "Stufe $level",color=Isle.Muted,fontSize=12.sp)
    }
}
