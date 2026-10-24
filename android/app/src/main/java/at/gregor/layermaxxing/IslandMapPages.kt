package at.gregor.layermaxxing

/** Only this bounded slice enters coast, route, lane and sprite preparation. Postal data stays whole. */
internal object IslandMapPages {
    const val SIZE = 6

    fun count(size: Int): Int = if (size <= 0) 1 else (size - 1) / SIZE + 1
    fun clamp(size: Int, page: Int): Int = page.coerceIn(0, count(size) - 1)
    fun <T> page(all: List<T>, page: Int): List<T> {
        val start = clamp(all.size, page) * SIZE
        return all.subList(start, minOf(start + SIZE, all.size))
    }
}
