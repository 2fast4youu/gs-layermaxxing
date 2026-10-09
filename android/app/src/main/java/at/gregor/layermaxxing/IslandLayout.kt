package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

internal object IslandLayout {
    fun defaultPosition(b: IsleBuilding): List<Int> = IslandPlans.homeBuildings.getValue(b).let { listOf((it.x*20).roundToInt(),(it.y*20).roundToInt()) }
    fun separation(c: ApiClient.IslandConstruction) = 3f / IslandPlans.constructionScale(c.land)
    fun obstacles(h: ApiClient.HomeIsland): List<List<Int>> = if(h.construction.mode=="creative") emptyList() else
        h.places.keys.mapNotNull { LifePlaces.plots.getOrNull(it) }.plus(h.decor.keys.mapNotNull { IslandPlans.homeSlots.getOrNull(it) })
            .map { (x,y) -> listOf((x*20).roundToInt(),(y*20).roundToInt()) }
    fun valid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>, obstacles: List<List<Int>> = emptyList()): Boolean {
        if (p.size != 2 || !IslandPlans.inside(p[0]/20f,p[1]/20f,radius=.95f)) return false
        val clearance=separation(c)*separation(c)
        if(obstacles.any { q -> (q[0]-p[0])*(q[0]-p[0])+(q[1]-p[1])*(q[1]-p[1]) < clearance }) return false
        return IsleBuilding.entries.filter { it != b && (c.buildings[it.name] ?: 0) > 0 }.all {
            val q=c.positions[it.name] ?: defaultPosition(it)
            (q[0]-p[0])*(q[0]-p[0])+(q[1]-p[1])*(q[1]-p[1]) >= clearance
        }
    }
}

/** Placement consumes its own drags; coordinates stay in island-local grid space at any zoom. */
@Composable internal fun IslandPlacementGrid(c: ApiClient.IslandConstruction, b: IsleBuilding, position: List<Int>, obstacles: List<List<Int>>, onPosition: (List<Int>)->Unit) {
    val scale=IslandPlans.constructionScale(c.land)
    fun point(o: Offset, width: Float, height: Float) = listOf(
        (((o.x/width-.5f)/scale+.5f)*20).roundToInt().coerceIn(0,20),
        (((o.y/height-.55f)/scale+.55f)*20).roundToInt().coerceIn(0,20))
    Canvas(Modifier.fillMaxSize().semantics { contentDescription="Bauplatzraster" }
        .pointerInput(scale,b) { detectTapGestures { onPosition(point(it,size.width.toFloat(),size.height.toFloat())) } }
        .pointerInput(scale,b) { detectDragGestures { change,_ -> change.consume(); onPosition(point(change.position,size.width.toFloat(),size.height.toFloat())) } }) {
        val color=if(IslandLayout.valid(c,b,position,obstacles)) Color(0xFF47CC78) else Color(0xFFEF665E)
        for(i in 0..20) for(j in 0..20) if(IslandPlans.inside(i/20f,j/20f,radius=.95f)) {
            val (x,y)=IslandPlans.onLand(i/20f,j/20f,scale)
            val tileW=size.width*scale/20;val tileH=size.height*scale/20
            drawRect(Color(0xFF24515A).copy(alpha=.35f),Offset(x*size.width-tileW/2,y*size.height-tileH/2),androidx.compose.ui.geometry.Size(tileW,tileH),style=Stroke(1.2f))
        }
        val (x,y)=IslandPlans.onLand(position[0]/20f,position[1]/20f,scale)
        val w=size.width*.15f; val h=size.width*.15f
        drawRect(color.copy(alpha=.30f),Offset(x*size.width-w/2,y*size.height-h/2),androidx.compose.ui.geometry.Size(w,h))
        drawRect(color,Offset(x*size.width-w/2,y*size.height-h/2),androidx.compose.ui.geometry.Size(w,h),style=Stroke(3f))
    }
}

@Composable internal fun IslandBuildShop(c: ApiClient.IslandConstruction, onPick:(IsleBuilding)->Unit,onLand:()->Unit,onLayout:()->Unit,onDecor:()->Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Bauen",fontSize=24.sp,color=Isle.Ink)
        Text(if(c.mode=="creative") "Kreativinsel · kostenlos" else "${c.available} Punkte · Tag ${c.ageDays}",color=Isle.Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onLayout) { Text("Layout bearbeiten") }
            OutlinedButton(onClick=onLand) { Text("Land") }
            if(c.mode!="creative") TextButton(onClick=onDecor) { Text("Deko") }
        }
        IsleBuilding.entries.forEach { b ->
            val level=c.buildings[b.name] ?: 0
            val cost=c.buildCosts.getOrElse(level) {0}; val day=c.buildDays.getOrElse(level) {0}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                PaintedIslandPlace(b,level.coerceAtLeast(1),IslandArtwork.resource(b,level.coerceAtLeast(1)),Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(start=12.dp)) {
                    Text(b.label,color=Isle.Ink)
                    Text(if(level==0) if(c.mode=="creative") "Kostenlos" else "$cost Punkte · ab Tag $day" else "Stufe $level / 3",fontSize=12.sp,color=Isle.Muted)
                }
                Button(onClick={onPick(b)},modifier=Modifier.semantics { contentDescription=b.label+if(level==0) " platzieren" else " auswählen" },enabled=level>0 || IslandEvolution.allowed(c,cost,day)) { Text(if(level==0) "Platzieren" else "Auswählen") }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
