#!/usr/bin/env python3
"""Offline isolated JVM geometry probe. Executes extracted production Kotlin, never Gradle.

Requires an existing local Kotlin compiler and Compose geometry jar in ~/.gradle (read only).
All generated files live in TMPDIR, which must be explicitly set. No app compilation, downloads or DB.
"""
import glob
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'android/app/src/main/java/at/gregor/layermaxxing'

def function(text, name):
    start = text.index('fun ' + name + '(')
    begin = text.index('{', start)
    depth = 1
    end = begin + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

def jar(pattern):
    matches = sorted(glob.glob(str(Path.home() / '.gradle' / pattern)))
    if not matches:
        raise SystemExit('Missing cached dependency: ' + pattern)
    return matches[-1]

compiler = jar('wrapper/dists/gradle-*/**/gradle-*/lib/kotlin-compiler-embeddable-*.jar')
compiler_lib = Path(compiler).parent
stdlib = str(next(compiler_lib.glob('kotlin-stdlib-*.jar')))
geometry = jar('caches/9.3.1/transforms/*/transformed/ui-geometry-release/jars/classes.jar')
annotations = jar('caches/modules-2/files-2.1/org.jetbrains/annotations/*/*/annotations-*.jar')
ground = json.loads((ROOT / 'server/app/geometry/island_ground.json').read_text())
coast = ground['coast']
zones = ground['zones']
cove = min(((i, j) for j in range(21) for i in range(21) if zones[j][i] == 'B' and (j == 20 or zones[j+1][i] == 'W')), key=lambda p: abs(p[0]-10)*10-p[1])
route_source = (SRC / 'HarbourRoutes.kt').read_text()
# The entire current SeaChart is also the pre-fix SeaChart: repair intentionally changes no routing geometry.
sea = route_source[route_source.index('internal object SeaChart {'):]
positions = function((SRC / 'IslandWorld.kt').read_text(), 'positions')
lane = function(route_source, 'lane')
length = function(route_source, 'length')
outline = ','.join(f'{x}f to {y}f' for x, y in coast['outline'])
rocks = ','.join(f'Triple({x}f,{y}f,{r}f)' for x, y, r in coast['rocks'])
source = '''package at.gregor.layermaxxing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*
'''+sea+'\nobject IsleLayout { '+positions+' }\n'+'''
object IslandPlans {
    const val ASPECT = 1367f / 1116f
    fun constructionScale(land: Int): Float = listOf(.46f, .58f, .70f, .82f, .92f, 1f)[land.coerceIn(0, 5)]
    fun onLand(x: Float, y: Float, s: Float) = (.5f + (x - .5f) * s) to (.55f + (y - .55f) * s)
}
object HarbourRoute {
    fun onMap(topLeft: Offset, width: Float, frac: Pair<Float, Float>) = topLeft + Offset(frac.first * width, frac.second * width / IslandPlans.ASPECT)
'''+lane+'\n'+length+'\n}\n'+f'''
object IslandGround {{
    data class Coast(val outline: List<Pair<Float, Float>>, val rocks: List<Triple<Float, Float, Float>>)
    val coast = Coast(listOf({outline}), listOf({rocks}))
}}
'''+(SRC / 'IslandMapPages.kt').read_text().replace('package at.gregor.layermaxxing','')+'''
fun measure(n: Int): Long {
    val w = 1233f; val h = 2673f
    val boxes = IsleLayout.positions(n).mapIndexed { i, (x,y) ->
        val width = (104 + i % 6 * 10) * 3f
        Triple(Offset(w*x-width/2, h*y-width*.5f), width, i%6)
    } + Triple(Offset(w/2-270, h/2-540*.42f), 540f, 5)
    val coasts = boxes.map { (tl, width, land) -> SeaChart.coastOf(tl,width,land) }
    val p0 = SeaChart.probes
    val begin = System.nanoTime()
    val field = SeaChart.Field(coasts, Rect(126f,63f,w-126f,h-63f),21.6f)
    // Same overlap predicate as production coast clipping. No route substitutes or fake crash.
    val overlapPairs = coasts.indices.sumOf { i -> (0 until i).count { j ->
        coasts[i].outline.any { SeaChart.inside(it, coasts[j].outline) } ||
            coasts[j].outline.any { SeaChart.inside(it, coasts[i].outline) }
    } }
    val checks = SeaChart.probes-p0
    println("geometry friends=$n coasts=${coasts.size} fieldCells=${field.cols*field.rows} coastPairBudget=${coasts.size*(coasts.size-1)/2} overlapPairs=$overlapPairs clearanceProbes=$checks elapsedMs=${(System.nanoTime()-begin)/1000000}")
    val routeStart = System.nanoTime()
    val routeP0 = SeaChart.probes
    val berths = boxes.map { (tl,width,land) ->
        val frac = IslandPlans.onLand(COVE_X / 20f, (COVE_Y + 2) / 20f, IslandPlans.constructionScale(land))
        val raw = HarbourRoute.onMap(tl,width,frac)
        (SeaChart.moor(raw,raw,coasts,12f,3*IslandPlans.constructionScale(land)/20f*width,hull=36f)
            ?: SeaChart.Mooring(raw,true)).point
    }
    val routes = berths.dropLast(1).map { SeaChart.route(berths.last(),it,field,36f,12f) }
    val routeProbes = SeaChart.probes-routeP0
    println("routes friends=$n found=${routes.count { it != null }} probes=$routeProbes elapsedMs=${(System.nanoTime()-routeStart)/1000000}")
    if (n <= 6) {
        check(routeProbes <= 4000) { "bounded page exceeds route work budget" }
        check(routes.all { it != null }) { "bounded fallback-berth fixture loses a route" }
    } else {
        check(routeProbes > 4000) { "legacy fixture no longer reproduces excessive route work" }
    }
    return coasts.size.toLong() * field.cols * field.rows
}
fun main() {
    measure(6) // JVM warmup
    for (n in listOf(0,1,12,20,50)) {
        val oldWork = measure(n)
        val peers = (0 until n).toList()
        val pages = (0 until IslandMapPages.count(n)).map { IslandMapPages.page(peers,it) }
        check(pages.flatten() == peers)
        check(pages.all { it.size <= 6 })
        val boundedWork = measure(pages.first().size)
        if (n > 6) check(boundedWork < oldWork)
        println("bounded friends=$n pages=${pages.size} firstPage=${pages.first().size} maxCoastCellVisits=$boundedWork oldMaxCoastCellVisits=$oldWork")
    }
}
'''
source = source.replace('COVE_X', str(cove[0])).replace('COVE_Y', str(cove[1]))
if not os.environ.get('TMPDIR'):
    raise SystemExit('TMPDIR must be the profile scratch directory')
with tempfile.TemporaryDirectory(prefix='island-jvm-probe-', dir=os.environ['TMPDIR']) as work:
    work = Path(work)
    kt = work / 'Probe.kt'
    kt.write_text(source)
    out = work / 'classes'
    cp = os.pathsep.join([stdlib, geometry, annotations])
    jvm = ['java', '-XX:-UsePerfData', '-Djava.io.tmpdir=' + str(work)]
    subprocess.run(jvm + ['-cp', str(compiler_lib / '*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-classpath', cp, '-d', str(out), str(kt)], check=True)
    subprocess.run(jvm + ['-cp', str(out) + os.pathsep + cp, 'at.gregor.layermaxxing.ProbeKt'], check=True)
