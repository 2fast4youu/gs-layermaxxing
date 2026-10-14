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
    fun levelName(b: IsleBuilding, level: Int, standing: Boolean = level > 0) = "${IslandStages.of(b, level).name} · " + if (!standing) "noch nicht gebaut" else IslandStages.stageLabel(level)
    fun allowed(c: ApiClient.IslandConstruction, cost: Int, days: Int) = c.mode == "creative" || c.available >= cost && c.ageDays >= days
}

/** Reuse Gregor's painted sprite family at EVERY stage, including the natural starter (table: [IslandStages]). */
internal object IslandArtwork {
    fun resource(building: IsleBuilding, tier: Int): Int = IslandStages.of(building, tier).res
    fun accent(building: IsleBuilding, tier: Int): Int? = IslandStages.of(building, tier).accent
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(building?.let { if (standing) "${IslandStages.of(it, level).name} ausbauen" else "${IslandStages.of(it, 0).name} bauen" } ?: "Insel vergrößern",fontSize=22.sp,color=Isle.Ink)
        Text(if(c.mode=="creative") "Kreativmodus · kostenlos auf deiner echten Insel" else "${c.available} Punkte verfügbar · Tag ${c.ageDays}",color=Isle.Muted,fontSize=13.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            if(building!=null && !standing) {
                BuildingPreview(building, 0, Modifier.weight(1f))
            } else if(building!=null) {
                BuildingPreview(building, level, Modifier.weight(1f))
                Text("→", color=Isle.Muted, fontSize=24.sp, modifier=Modifier.padding(horizontal=6.dp))
                BuildingPreview(building, (level+1).coerceAtMost(max), Modifier.weight(1f))
            } else {
                DynamicIsland(IslandPlans.home(1,0,c),Modifier.width(125.dp),animate=false)
                Text("→",color=Isle.Muted,fontSize=24.sp)
                DynamicIsland(IslandPlans.home(1,0,c.copy(land=(level+1).coerceAtMost(max))),Modifier.width(145.dp),animate=false)
            }
        }
        Text(if(building==null) "Landstufe $level / $max" else IslandEvolution.levelName(building,level,standing),color=Isle.Ink)
        if(!standing) {
            Text("Start: gratis · Ausbau ab Stufe 1 kostet Punkte.",color=Isle.Muted,fontSize=13.sp)
            Button(onClick={onAction("upgrade")},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Wird gespeichert…" else "Gratis bauen") }
        } else if(level<max) {
            Text(if(c.mode=="creative") "Nächster Ausbau: kostenlos" else "Nächster Ausbau: $cost Punkte · ab Tag $day. Punkte werden beim Bauen verbraucht.",color=Isle.Muted,fontSize=13.sp)
            Button(onClick={onAction(if(building==null) "expand" else "upgrade")},enabled=!busy && IslandEvolution.allowed(c,cost,day),modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Wird gespeichert…" else if(building==null) "Land erweitern" else if(level==0) "Gebäude bauen" else "Auf Stufe ${level+1} ausbauen")
            }
        } else Text("Vollständig ausgebaut",color=Isle.TealDark)
        if(c.mode=="creative" && (if (building==null) level>0 else standing)) OutlinedButton(onClick={onAction(if(building==null) "shrink" else "downgrade")},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(building==null) "Land kostenlos verkleinern" else "Kostenlos zurückbauen") }
        TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Schließen") }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BuildingPreview(building: IsleBuilding, level: Int, modifier: Modifier = Modifier) {
    val stage = IslandStages.of(building, level)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PaintedIslandPlace(building, level, stage.res, Modifier.fillMaxWidth().height(170.dp), description = "Ausbauvorschau: ${stage.name}")
        Text(stage.name, color = Isle.Ink, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(if (level == 0) "Start" else "Stufe $level", color = Isle.Muted, fontSize = 12.sp)
        Text(stage.look, color = Isle.Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
