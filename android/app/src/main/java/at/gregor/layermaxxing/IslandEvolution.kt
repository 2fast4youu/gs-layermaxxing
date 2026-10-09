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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.cos

internal object IslandEvolution {
    fun primitiveName(b: IsleBuilding) = when(b) {
        IsleBuilding.HOUSE -> "Schlafplatz"
        IsleBuilding.POST -> "Briefbaum"
        IsleBuilding.LIGHTHOUSE -> "Aussichtspalme"
        IsleBuilding.LIBRARY -> "Wörter am Treibholz"
        IsleBuilding.CAMPFIRE -> "Feuerkreis"
        IsleBuilding.HARBOUR -> "Strandtreff"
    }
    fun levelName(b: IsleBuilding, level: Int) = if (level == 0) primitiveName(b) else when(level) {
        1 -> "${b.label} · Holzbau"
        2 -> "${b.label} · ausgebaut"
        else -> "${b.label} · Meisterbau"
    }
    fun allowed(c: ApiClient.IslandConstruction, cost: Int, days: Int) = c.mode == "creative" || c.available >= cost && c.ageDays >= days
}

/** Native starter artwork, deliberately no building-shaped ruins. */
internal fun DrawScope.drawPrimitivePlace(b: IsleBuilding, phase: Float = 0f) {
    val w = size.width; val h = size.height
    val wood = Color(0xFF805331); val light = Color(0xFFDBB77D)
    fun palm(post: Boolean) {
        val sway = sin(phase * 6.28f) * w * .035f
        drawLine(wood, Offset(w*.5f,h*.95f), Offset(w*.57f+sway,h*.27f), w*.11f, StrokeCap.Round)
        for (i in 0..4) {
            val a = (i * .7f + 3.0f)
            val p = Path().apply { moveTo(w*.57f+sway,h*.27f); quadraticTo(w*(.57f+cos(a)*.25f),h*(.27f+sin(a)*.25f), w*(.57f+cos(a)*.43f),h*(.36f+sin(a)*.3f)) }
            drawPath(p, Color(0xFF538850), style=androidx.compose.ui.graphics.drawscope.Stroke(w*.075f, cap=StrokeCap.Round))
        }
        if(post) {
            drawRect(Color(0xFFFFF4D5), Offset(w*.20f,h*.55f), Size(w*.4f,h*.24f))
            drawLine(wood,Offset(w*.2f,h*.55f),Offset(w*.4f,h*.70f),w*.015f)
            drawLine(wood,Offset(w*.6f,h*.55f),Offset(w*.4f,h*.70f),w*.015f)
        } else {
            repeat(3) { i -> drawLine(light,Offset(w*.45f,h*(.43f+i*.14f)),Offset(w*.66f,h*(.43f+i*.14f)),w*.04f) }
        }
    }
    when(b) {
        IsleBuilding.POST -> palm(true)
        IsleBuilding.LIGHTHOUSE -> palm(false)
        IsleBuilding.HOUSE -> {
            drawOval(Color(0xFFCD7956),Offset(w*.12f,h*.54f),Size(w*.77f,h*.34f))
            repeat(4) { i -> drawLine(light,Offset(w*(.2f+i*.18f),h*.57f),Offset(w*(.2f+i*.18f),h*.85f),w*.03f) }
            drawOval(Color(0xFFF6DCAE),Offset(w*.15f,h*.52f),Size(w*.23f,h*.21f))
        }
        IsleBuilding.LIBRARY -> {
            drawLine(wood,Offset(w*.12f,h*.77f),Offset(w*.9f,h*.65f),w*.23f,StrokeCap.Round)
            drawRect(Color(0xFFFAE5B0),Offset(w*.30f,h*.35f),Size(w*.48f,h*.33f))
            repeat(3) { i -> drawLine(wood,Offset(w*.37f,h*(.42f+i*.08f)),Offset(w*.7f,h*(.42f+i*.08f)),w*.025f) }
        }
        IsleBuilding.CAMPFIRE -> {
            for(i in 0..7) { val a=i*.785f; drawOval(Color(0xFF8A8E80),Offset(w*(.45f+cos(a)*.32f),h*(.65f+sin(a)*.18f)),Size(w*.14f,h*.13f)) }
            drawLine(wood,Offset(w*.27f,h*.84f),Offset(w*.72f,h*.7f),w*.08f,StrokeCap.Round)
            val fire=Path().apply { moveTo(w*.36f,h*.76f); quadraticTo(w*.23f,h*.56f,w*.52f,h*.28f); quadraticTo(w*.5f,h*.54f,w*.68f,h*.63f); quadraticTo(w*.75f,h*.86f,w*.36f,h*.76f) }
            drawPath(fire,Color(0xFFEAA04F))
        }
        IsleBuilding.HARBOUR -> {
            drawLine(wood,Offset(w*.15f,h*.8f),Offset(w*.87f,h*.64f),w*.13f,StrokeCap.Round)
            drawLine(wood,Offset(w*.55f,h*.65f),Offset(w*.55f,h*.25f),w*.06f)
            val flag=Path().apply { moveTo(w*.55f,h*.25f); lineTo(w*.87f,h*.38f); lineTo(w*.55f,h*.48f);close() }
            drawPath(flag,Color(0xFFF2D397))
            drawOval(Color(0xFFBCA476),Offset(w*.08f,h*.84f),Size(w*.28f,h*.12f))
        }
    }
}

/** First construction is a light timber shelter; later tiers use the painted stone building. */
internal fun DrawScope.drawTimberPlace(b: IsleBuilding) {
    val w=size.width; val h=size.height
    val wood=Color(0xFFBE8652)
    drawRect(wood,Offset(w*.18f,h*.43f),Size(w*.64f,h*.48f))
    for(i in 0..4) drawLine(Color(0xFF946139),Offset(w*(.22f+i*.13f),h*.45f),Offset(w*(.22f+i*.13f),h*.9f),w*.025f)
    val roof=Path().apply {moveTo(w*.08f,h*.46f);lineTo(w*.48f,h*.12f);lineTo(w*.93f,h*.46f);close()}
    drawPath(roof,Color(0xFFDABB75))
    drawRect(Color(0xFF654C35),Offset(w*.42f,h*.64f),Size(w*.20f,h*.27f))
    when(b) {
        IsleBuilding.POST -> { drawRect(Color(0xFFFFF1CF),Offset(w*.26f,h*.52f),Size(w*.25f,h*.13f)); drawLine(Color(0xFFA8743E),Offset(w*.26f,h*.52f),Offset(w*.39f,h*.61f),w*.02f) }
        IsleBuilding.LIGHTHOUSE -> { drawLine(Color(0xFF75532D),Offset(w*.50f,h*.18f),Offset(w*.50f,h*.04f),w*.04f); drawCircle(Color(0xFFFFE9A0),w*.07f,Offset(w*.5f,h*.10f)) }
        IsleBuilding.LIBRARY -> drawRect(Color(0xFF617E6C),Offset(w*.23f,h*.52f),Size(w*.22f,h*.19f))
        IsleBuilding.CAMPFIRE -> drawCircle(Color(0xFFE89B45),w*.08f,Offset(w*.29f,h*.72f))
        IsleBuilding.HARBOUR -> drawLine(Color(0xFF658D8D),Offset(w*.15f,h*.9f),Offset(w*.85f,h*.9f),w*.08f)
        else -> Unit
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
    onOpen: () -> Unit, onAction: (String) -> Unit, onDismiss: () -> Unit,
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
        if(level<=1) Canvas(Modifier.size(76.dp)) { if(level==0) drawPrimitivePlace(building) else drawTimberPlace(building) }
        else Box(Modifier.size(76.dp),contentAlignment=Alignment.BottomCenter) {
            Image(painterResource(IslandPlans.homeBuildings.getValue(building).res),null,Modifier.size((48+level*8).dp))
            if(level>1) Text(if(level==2) "✦" else "✦✦",color=Isle.Star,modifier=Modifier.align(Alignment.TopEnd))
        }
        Text(if(level==0) "Start" else "Stufe $level",color=Isle.Muted,fontSize=12.sp)
    }
}
