package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class IslandMapPagesTest {
    @Test fun everyFriendAppearsExactlyOnceWithinTheGeometryBudget() {
        for (n in listOf(0, 1, 12, 20, 50)) {
            val all = (0 until n).map { 100L + it }
            val pages = (0 until IslandMapPages.count(n)).map { IslandMapPages.page(all, it) }
            assertEquals(all, pages.flatten())
            assertTrue(pages.all { it.size <= 6 })
            assertEquals(all.take(6), IslandMapPages.page(all, -1))
            assertEquals(pages.last(), IslandMapPages.page(all, Int.MAX_VALUE))
            // Dense mail is associated by peer id, never by page slot or route index.
            val mail = (0 until 120).map { it.toLong() to (if (n == 0) 999L else all[it % n]) }
            val perPage = pages.flatMap { page -> page.flatMap { peer -> mail.filter { it.second == peer } } }
            assertEquals(if (n == 0) emptySet<Long>() else mail.map { it.first }.toSet(), perPage.map { it.first }.toSet())
            assertEquals(if (n == 0) 0 else 120, perPage.size)
        }
    }

    @Test fun savedPageIsClampedWhenFriendsDisappear() {
        assertEquals(8, IslandMapPages.clamp(50, 8))
        assertEquals(1, IslandMapPages.clamp(12, 8))
        assertEquals(0, IslandMapPages.clamp(0, 8))
        assertEquals(listOf(7), IslandMapPages.page(listOf(7), 8))
    }
}
