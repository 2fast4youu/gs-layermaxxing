package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsPropertyKey

/** All points traced in the approved 1024 x 1536 originals. Clockwise face corners, then
 * the real wood in front of that face. No independently stretched foreground layer. */
internal data class PostalSlot(val face: List<Offset>, val lip: List<Offset>, val lift: Float, val ambient: Float) {
    val left get() = face.minOf { it.x }
    val top get() = face.minOf { it.y }
    val width get() = face.maxOf { it.x } - left
    val box get() = LetterTree.Box(left / 1024f, top / 1536f,
        (left + width) / 1024f, face.maxOf { it.y } / 1536f)
}
internal data class StorageFrame(val slot: PostalSlot, val progress: Float, val occluders: List<List<Offset>> = listOf(slot.lip)) {
    val clearance get() = PostalStorageMotion.clearance(progress)
    val turn get() = PostalStorageMotion.turn(progress)
    val dy get() = -slot.lift * clearance
    val light get() = slot.ambient + (1f - slot.ambient) * PostalStorageMotion.roomLight(progress)
    // The same function is sampled in reverse; no independent return animation or threshold jump.
    val corners get() = slot.face.mapIndexed { i, p ->
        val target = Offset(slot.left + if (i == 1 || i == 2) slot.width else 0f,
            slot.top + if (i >= 2) slot.width / EnvelopeArt.ASPECT else 0f)
        Offset(p.x + (target.x - p.x) * turn, p.y + (target.y - p.y) * turn + dy)
    }
}
internal val StorageFrameKey = SemanticsPropertyKey<StorageFrame>("Registered postal face, clearance and illumination")
internal object PhysicalPostalStorage {
    private fun points(vararg xy: Int) = xy.toList().chunked(2).map { Offset(it[0].toFloat(), it[1].toFloat()) }
    private fun slot(face: List<Offset>, lip: List<Offset>, lift: Float, light: Float) = PostalSlot(face, lip, lift, light)
    // Four disjoint faces on the recessed floor, two rows; all clear of the real side walls.
    private val chestLip = points(112,933, 201,930, 262,675, 278,660, 218,932, 775,937,
        752,668, 780,650, 882,937, 917,950, 911,1200, 115,1200)
    private val chest = listOf(
        slot(points(305,742, 499,739, 502,813, 285,817), chestLip, 52f, .68f),
        slot(points(525,739, 716,742, 735,817, 521,813), chestLip, 52f, .68f),
        slot(points(280,835, 500,832, 500,918, 253,921), chestLip, 48f, .64f),
        slot(points(525,832, 739,835, 765,921, 523,918), chestLip, 48f, .64f))
    private fun cubby(l: Int, t: Int, r: Int, bottom: Int, lip: List<Offset>) =
        slot(points(l,t, r,t+8, r+9,bottom, l-9,bottom-5), lip, 24f, .62f)
    private val cubbies = listOf(
        cubby(187,280,337,370,points(165,367,377,376,377,428,160,414)),
        cubby(417,292,555,377,points(398,376,581,384,581,433,398,428)),
        cubby(619,314,748,391,points(599,386,790,400,790,445,598,434)),
        cubby(209,510,341,594,points(164,589,379,593,379,647,160,645)),
        cubby(418,529,545,595,points(399,593,581,599,581,648,398,646)),
        cubby(624,530,751,602,points(599,598,789,605,789,648,598,648)))
    // One in each shallow tray, one directly in front on the clear counter. A pair is
    // physical correspondence, never a representative hiding other real messages.
    private val counterLip = points(0,1285, 1024,1285, 1024,1300, 0,1300)
    private val trays = listOf(
        slot(points(174,839,344,840,353,916,158,911), points(113,899,141,796,163,781,144,899,363,903,388,785,408,779,382,907,362,946,112,940), 36f,.76f),
        slot(points(172,1000,358,1003,372,1114,158,1110), counterLip, 24f,.91f),
        slot(points(423,851,598,852,606,925,409,922), points(383,905,408,779,416,783,405,904,628,908,645,784,654,781,646,909,627,951,382,947), 36f,.72f),
        slot(points(415,1006,625,1004,639,1117,402,1120), counterLip, 24f,.91f),
        slot(points(673,847,848,850,864,930,655,926), points(646,909,654,781,665,782,663,909,891,913,877,795,891,803,918,943,898,956,644,952), 36f,.68f),
        slot(points(676,1001,848,1005,862,1118,661,1114), counterLip, 24f,.91f))
    fun slots(level: Int): List<PostalSlot> = when(level) { 1 -> chest; 2 -> cubbies; 3 -> trays; else -> emptyList() }
    fun frame(level: Int, index: Int, progress: Float, occupied: Int = slots(level).size) =
        slots(level).getOrNull(index)?.let {
            StorageFrame(it, progress, slots(level).map { s -> s.lip } +
                if (level == 1) slots(level).take(occupied).drop(index + 1).map { s -> s.face } else emptyList())
        }

    fun contains(polygon: List<Offset>, p: Offset): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[j]
            if ((a.y > p.y) != (b.y > p.y) &&
                p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
    fun visible(level: Int, index: Int, occupied: Int, p: Offset): Boolean {
        val slot = slots(level)[index]
        return contains(slot.face, p) && frame(level, index, 0f, occupied)!!.occluders.none { contains(it, p) }
    }

    /** Largest axis-aligned rectangle wholly inside exposed mail, in source pixels.
     * A 2px inset avoids anti-aliased wood boundaries. No 48dp expansion into another letter. */
    fun hitBox(level: Int, index: Int, occupied: Int): LetterTree.Box {
        val slot = slots(level)[index]
        val left = slot.left.toInt(); val top = slot.top.toInt()
        val width = slot.width.toInt()
        val occluders = frame(level, index, 0f, occupied)!!.occluders
        val heights = IntArray(width)
        var area = 0; var best = intArrayOf(left, top, left, top)
        for (y in top..slot.face.maxOf { it.y }.toInt()) {
            for (x in 0 until width) {
                val p = Offset((left+x).toFloat(), y.toFloat())
                heights[x] = if (contains(slot.face, p) && occluders.none { contains(it, p) }) heights[x]+1 else 0
            }
            val stack = java.util.ArrayDeque<Int>()
            for (x in 0..width) {
                val h = if (x == width) 0 else heights[x]
                while (stack.isNotEmpty() && heights[stack.peekLast()] > h) {
                    val height = heights[stack.removeLast()]
                    val start = if (stack.isEmpty()) 0 else stack.peekLast()+1
                    if ((x-start)*height > area) {
                        area = (x-start)*height
                        best = intArrayOf(left+start, y-height+1, left+x, y+1)
                    }
                }
                stack.addLast(x)
            }
        }
        require(area > 16) { "Postal slot has no exposed hit face" }
        return LetterTree.Box((best[0]+2)/1024f, (best[1]+2)/1536f,
            (best[2]-2)/1024f, (best[3]-2)/1536f)
    }
}
