package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The economy and the scenery of "Das Lehen".
 *
 * The screens only render these values, so the promises of the design — the
 * valley is empty at the start, it stays calm, decoration is never tappable and
 * every spent EP changes a picture — are pinned here rather than in Compose.
 */
class FiefEconomyTest {
    @Test
    fun chainCostsSumTo18AndNoStepCostsMoreThanThree() {
        assertEquals(12, Fief.chain.size)
        assertEquals(Fief.TOTAL_COST, Fief.chain.sumOf { it.cost })
        assertEquals(18, Fief.chain.sumOf { it.cost })
        assertTrue(Fief.chain.all { it.cost in 1..3 })
        // Both strands exist and the chain interleaves them.
        assertEquals(setOf(Track.HOF, Track.LAND), Fief.chain.map { it.track }.toSet())
    }

    @Test
    fun firstBuildCostsExactlyOneEp() {
        val plans = Fief.availablePlans(emptySet())
        assertTrue(plans.all { it.cost == 1 })
        assertEquals(1, plans.minOf { it.cost })
        val after = Fief.build(emptySet(), BuildStep.WELL, earnedEp = 1)
        assertEquals(setOf(BuildStep.WELL), after)
        assertEquals(0, Fief.balance(1, after))
    }

    @Test
    fun availablePlansAreAtMostTwoAndFromDistinctTracks() {
        var built = emptySet<BuildStep>()
        Fief.chain.forEach { _ ->
            val plans = Fief.availablePlans(built)
            assertTrue(plans.size <= 2)
            assertEquals(plans.size, plans.map { it.track }.toSet().size)
            // Each plan is the first still open step of its strand: nothing is skipped.
            plans.forEach { plan ->
                assertEquals(plan, Fief.chain.first { it.track == plan.track && it !in built })
            }
            built = built + (plans.firstOrNull() ?: return@forEach)
        }
        assertTrue(Fief.availablePlans(Fief.chain.toSet()).isEmpty())
    }

    @Test
    fun buildNeverOverdrawsNeverSkipsNeverRemoves() {
        // Unaffordable: a plan that costs more than the balance changes nothing.
        val poor = Fief.build(emptySet(), BuildStep.WELL, earnedEp = 0)
        assertTrue(poor.isEmpty())
        // Skipping a strand is refused, even with plenty of EP.
        assertTrue(Fief.build(emptySet(), BuildStep.KEEP, earnedEp = 40).isEmpty())
        assertTrue(Fief.build(emptySet(), BuildStep.BRIDGE, earnedEp = 40).isEmpty())
        // Walking the whole chain in order never overdraws and never loses a step.
        var built = emptySet<BuildStep>()
        var earned = 0
        Fief.chain.forEach { step ->
            earned += step.cost
            val before = built
            built = Fief.build(built, step, earned)
            assertTrue(built.containsAll(before))
            assertEquals(before.size + 1, built.size)
            assertTrue(Fief.balance(earned, built) >= 0)
        }
        assertEquals(Fief.chain.toSet(), built)
        assertEquals(18, Fief.spent(built))
        assertEquals(0, Fief.balance(18, built))
        // Building twice is a no-op, the ledger never shrinks.
        assertEquals(built, Fief.build(built, BuildStep.WELL, earnedEp = 100))
    }

    @Test
    fun legacyLedgerMigratesWithoutLosingEpOrGoingNegative() {
        // The interrupted v1 ledger: keep level 2 plus palisade level 1 = 26 EP spent.
        val legacy = mapOf(Building.KEEP to 2, Building.PALISADE to 1)
        assertEquals(26, Castles.spentFor(legacy))
        val migrated = Fief.migrateLegacy(legacy)
        assertEquals(Fief.chain.toSet(), migrated)
        assertEquals(18, Fief.spent(migrated))
        assertTrue(Fief.balance(earnedEp = 5, built = migrated) >= 0)
        // A small ledger buys the longest affordable prefix; the rest stays as coins.
        val small = Fief.migrateLegacy(mapOf(Building.WELL to 1, Building.PALISADE to 1))
        assertEquals(3, Castles.spentFor(mapOf(Building.WELL to 1, Building.PALISADE to 1)))
        assertEquals(listOf(BuildStep.WELL, BuildStep.PATH, BuildStep.ROOF), Fief.chain.filter { it in small })
        assertEquals(3, Fief.spent(small))
        // Nothing is ever migrated beyond the chain, and nothing goes negative.
        (0..40).forEach { earned ->
            assertTrue(Fief.balance(earned, migrated) >= 0)
        }
        assertTrue(Fief.migrateLegacy(emptyMap()).isEmpty())
    }

    @Test
    fun hutStageAndFriendStageAreMonotone() {
        var built = emptySet<BuildStep>()
        var stage = Fief.hutStage(built)
        assertEquals(0, stage)
        Fief.chain.forEach { step ->
            built = built + step
            val next = Fief.hutStage(built)
            assertTrue("stage fell back at ${step.name}", next >= stage)
            stage = next
        }
        assertEquals(3, stage)
        assertEquals(0, Fief.friendHutStage(0))
        assertEquals(0, Fief.friendHutStage(2))
        assertEquals(1, Fief.friendHutStage(3))
        assertEquals(2, Fief.friendHutStage(8))
        assertEquals(3, Fief.friendHutStage(14))
        assertEquals(3, Fief.friendHutStage(999))
        val stages = (0..40).map { Fief.friendHutStage(it) }
        assertTrue(stages.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(stages.all { it in Fief.hutStages.indices })
    }

    @Test
    fun treasuryCoinsEqualBalanceCappedAt18AndCoinsDropOnlyForward() {
        assertEquals(0, FiefScenes.treasuryCoins(0).size)
        assertEquals(4, FiefScenes.treasuryCoins(4).size)
        assertEquals(Fief.TOTAL_COST, FiefScenes.treasuryCoins(99).size)
        assertTrue(FiefScenes.treasuryCoins(18).all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(2, FiefScenes.coinDropCount(earnedEp = 5, lastSeenEarned = 3))
        assertEquals(0, FiefScenes.coinDropCount(earnedEp = 3, lastSeenEarned = 5))
        assertEquals(0, FiefScenes.coinDropCount(earnedEp = 0, lastSeenEarned = 0))
    }

    @Test
    fun shortRemainingStaysShort() {
        assertEquals("3 T", shortRemaining(3 * 86400 + 7200))
        assertEquals("2 Std", shortRemaining(2 * 3600 + 59))
        assertEquals("14 Min", shortRemaining(14 * 60))
        assertEquals("gleich", shortRemaining(0))
        assertEquals("gleich", shortRemaining(-99))
    }
}

class FiefSceneTest {
    @Test
    fun valleySceneAtZeroIsBoringOnPurpose() {
        val scene = FiefScenes.valleyScene(vale(), emptySet())
        assertEquals(FiefAssets.VALLEY_PLATE, scene.plate)
        assertTrue("a fresh valley must stay nearly empty", scene.sprites.size <= 5)
        assertEquals(3, scene.sprites.count { it.interactive })
        assertEquals(listOf("home", "friend", "signpost"), scene.sprites.map { it.id })
        // Nothing built means no land sprite and no building object at all.
        val builtAssets = BuildStep.entries.map { it.sprite }.toSet() - FiefAssets.HUT_STAGE1 -
            FiefAssets.HUT_STAGE2 - FiefAssets.HUT_STAGE3
        assertTrue(scene.sprites.none { it.asset in builtAssets })
        assertEquals(FiefAssets.HUT_STAGE0, scene.sprites.first { it.id == "home" }.asset)
    }

    @Test
    fun everySpentEpChangesAScene() {
        var built = emptySet<BuildStep>()
        var valley = FiefScenes.valleyScene(vale(earned = 18), built)
        var yard = FiefScenes.courtyardScene(vale(earned = 18), built, epEnabled = true, lettersEnabled = true)
        Fief.chain.forEach { step ->
            built = built + step
            val nextValley = FiefScenes.valleyScene(vale(earned = 18), built)
            val nextYard = FiefScenes.courtyardScene(vale(earned = 18), built, epEnabled = true, lettersEnabled = true)
            assertTrue(
                "building ${step.name} changed no picture",
                nextValley.sprites != valley.sprites || nextYard.sprites != yard.sprites,
            )
            valley = nextValley
            yard = nextYard
        }
    }

    @Test
    fun calmBudgetHolds() {
        val scenes = buildList {
            listOf(emptySet(), setOf(BuildStep.WELL), Fief.chain.toSet()).forEach { built ->
                add(FiefScenes.valleyScene(vale(earned = 18, theirEarned = 14), built))
                add(FiefScenes.courtyardScene(vale(earned = 18), built, epEnabled = true, lettersEnabled = true))
                add(FiefScenes.courtyardScene(vale(earned = 18), built, epEnabled = false, lettersEnabled = false))
            }
        }
        scenes.forEach { scene ->
            val moving = scene.sprites.mapNotNull { it.motion }
            assertTrue("${scene.id} moves too much", moving.size <= FiefScenes.MAX_MOVING_SPRITES)
            assertTrue(moving.all { it.ampPx <= FiefScenes.MAX_MOTION_AMPLITUDE_PX })
            assertTrue(moving.all { it.periodSec >= FiefScenes.MIN_MOTION_PERIOD_SEC })
            // Only usable things breathe; decoration is dead still.
            assertTrue(scene.sprites.none { !it.interactive && it.motion != null })
        }
    }

    @Test
    fun decorationIsNeverInteractiveAndTheBuildCornerDisappearsWhenDone() {
        val full = Fief.chain.toSet()
        val valley = FiefScenes.valleyScene(vale(earned = 18), full)
        assertEquals(setOf("home", "friend", "signpost"), valley.sprites.filter { it.interactive }.map { it.id }.toSet())
        val yard = FiefScenes.courtyardScene(vale(earned = 18), full, epEnabled = true, lettersEnabled = true)
        // Everything built shows up, but only board and chest remain usable.
        assertEquals(setOf("cy-board", "cy-chest"), yard.sprites.filter { it.interactive }.map { it.id }.toSet())
        assertTrue(yard.sprites.none { it.id == "cy-build" })
        // While a plan is open the build corner exists, and it is usable.
        val early = FiefScenes.courtyardScene(vale(earned = 1), setOf(BuildStep.WELL), true, true)
        assertTrue(early.sprites.first { it.id == "cy-build" }.interactive)
        // Without a single accepted EP there is no chest at all.
        val fresh = FiefScenes.courtyardScene(vale(earned = 0), emptySet(), epEnabled = true, lettersEnabled = true)
        assertTrue(fresh.sprites.none { it.id == "cy-chest" })
    }

    @Test
    fun epOffPutsAChainOnTheChestAndNeverRemovesTheBoard() {
        val locked = FiefScenes.courtyardScene(vale(earned = 4), setOf(BuildStep.WELL), epEnabled = false, lettersEnabled = true)
        assertNotNull(locked.sprites.firstOrNull { it.id == "cy-chest-lock" })
        assertFalse(locked.sprites.first { it.id == "cy-chest-lock" }.interactive)
        assertNotNull(locked.sprites.firstOrNull { it.id == "cy-board" })
        // Letters off never removes the board either; only its description is honest.
        val noLetters = FiefScenes.courtyardScene(vale(earned = 0), emptySet(), epEnabled = true, lettersEnabled = false)
        assertTrue(noLetters.sprites.first { it.id == "cy-board" }.label.contains("aus"))
    }

    @Test
    fun glintOrderIsDeterministicAndCoversOnlyInteractive() {
        val scene = FiefScenes.valleyScene(vale(earned = 18), Fief.chain.toSet())
        val order = Fief.glintOrder(scene)
        assertEquals(order, Fief.glintOrder(scene))
        assertEquals(scene.sprites.count { it.interactive }, order.size)
        assertTrue(order.all { id -> scene.sprites.first { it.id == id }.interactive })
        // Sorted by depth in the picture, so the sweep walks from far to near.
        assertEquals(listOf("friend", "home", "signpost"), order)
    }

    @Test
    fun revealAnchorsLandInExactlyOneScenePerStep() {
        // Every step's dust reveal belongs to exactly one scene: HOF in the yard,
        // LAND in the valley. Nothing is revealed in both and nothing in neither.
        Fief.chain.forEach { step ->
            val yard = FiefScenes.courtyardRevealAnchor(step)
            val valley = FiefScenes.valleyRevealAnchor(step)
            assertTrue(
                "step ${step.name} must reveal in exactly one scene",
                (yard == null) != (valley == null),
            )
            when (step.track) {
                Track.HOF -> assertNotNull(yard)
                Track.LAND -> assertNotNull(valley)
            }
            (yard ?: valley)!!.let { anchor ->
                assertTrue(anchor.x in 0f..1f && anchor.y in 0f..1f)
            }
        }
    }

    @Test
    fun buildPlansShowAtMostTwoParchmentsWithCoinCosts() {
        val plans = FiefScenes.buildPlans(emptySet(), earnedEp = 1, epEnabled = true)
        assertEquals(2, plans.size)
        assertEquals(listOf(BuildStep.WELL, BuildStep.PATH), plans.map { it.step })
        assertTrue(plans.all { it.coins == it.step.cost && it.coins in 1..3 })
        assertTrue(plans.all { it.affordable })
        assertTrue(plans.none { it.locked })
        // Not enough coins: the plan is pinned but not buildable, never hidden.
        val broke = FiefScenes.buildPlans(emptySet(), earnedEp = 0, epEnabled = true)
        assertEquals(2, broke.size)
        assertTrue(broke.none { it.affordable })
        // EP switched off in this friendship locks both plans.
        val locked = FiefScenes.buildPlans(emptySet(), earnedEp = 9, epEnabled = false)
        assertTrue(locked.all { it.locked && !it.affordable })
        assertTrue(FiefScenes.buildPlans(Fief.chain.toSet(), 18, true).isEmpty())
    }

    @Test
    fun boardSceneMapsEveryLetterStateDistinctly() {
        val cases = mapOf(
            LetterState.LOCKED_TIMED to letter(1, 2, mode = "timed", releaseAt = 7200),
            LetterState.LOCKED_RANDOM to letter(2, 2, mode = "random"),
            LetterState.LOCKED_PRESENCE to letter(3, 2, mode = "presence"),
            LetterState.LOCKED_MUTUAL_WAITING_ME to letter(4, 2, mode = "mutual", recipientApproved = false),
            LetterState.LOCKED_MUTUAL_WAITING_PEER to letter(5, 2, mode = "mutual", recipientApproved = true),
            LetterState.LOCKED_MANUAL to letter(6, 2, incoming = false, mode = "manual"),
            LetterState.READY to letter(7, 2, unlocked = true),
            LetterState.OPENED to letter(8, 2, unlocked = true, readAt = 5),
            LetterState.DELIVERED to letter(9, 2, incoming = false, unlocked = true),
            LetterState.READ to letter(10, 2, incoming = false, unlocked = true, readAt = 5),
        )
        // The mapping is total: every state of the model has an envelope.
        assertEquals(LetterState.entries.toSet(), cases.keys)
        val rendered = cases.map { (state, message) ->
            val actual = Conversations.letterState(message, openedLocally = message.id == 8L)
            assertEquals(state, actual)
            FiefScenes.envelopeAsset(actual, message.incoming) to
                FiefScenes.envelopeTag(actual, remainingSeconds = 7200)
        }
        assertEquals(cases.size, rendered.toSet().size)
        assertNull(FiefScenes.envelopeTag(LetterState.READY, 0))
        assertEquals("2 Std", FiefScenes.envelopeTag(LetterState.LOCKED_TIMED, 7200))
    }

    @Test
    fun boardPagesHangIncomingLeftOutgoingRightWithoutCollisions() {
        val letters = (1L..14L).map { id ->
            letter(id, 2, incoming = id % 2 == 0L, createdAt = 1000 + id, mode = "timed")
        }
        val pages = FiefScenes.boardPages(letters, emptySet(), now = 0, epOpportunityLetterIds = setOf(14L))
        assertEquals(2, pages.size)
        val slots = pages.flatten()
        assertEquals(letters.size, slots.size)
        assertTrue(slots.filter { it.incoming }.all { it.anchor.x < .5f })
        assertTrue(slots.filterNot { it.incoming }.all { it.anchor.x > .5f })
        assertTrue(slots.all { it.anchor.y in 0f..1f })
        // Nails never collide inside a column of one page.
        pages.forEach { page ->
            listOf(true, false).forEach { incoming ->
                val ys = page.filter { it.incoming == incoming }.map { it.anchor.y }
                assertEquals(ys.size, ys.toSet().size)
            }
        }
        // The EP coin tag is attached to exactly the letter that offers one.
        assertEquals(listOf(14L), slots.filter { it.coinTag }.map { it.letterId })
        // Within each column the pages walk from urgent to history, never back.
        listOf(true, false).forEach { incoming ->
            val buckets = pages.flatMap { page -> page.filter { it.incoming == incoming } }.map { it.bucket.ordinal }
            assertEquals(buckets.sorted(), buckets)
        }
        assertTrue(slots.all { it.label.isNotBlank() })
    }

    @Test
    fun boardPagesKeepEveryLetterReachableForAnyCount() {
        // The acceptance counts: 0, 1, 9, 10 and many letters all fit the board.
        assertTrue(FiefScenes.boardPages(emptyList(), emptySet(), 0, emptySet()).isEmpty())
        listOf(1, 9, 10, 27).forEach { count ->
            val letters = (1L..count.toLong()).map { id ->
                letter(id, 2, incoming = id % 3 != 0L, createdAt = 1000 + id, mode = "timed")
            }
            val pages = FiefScenes.boardPages(letters, emptySet(), now = 0, epOpportunityLetterIds = emptySet())
            val slots = pages.flatten()
            // Complete and duplicate-free: every letter hangs on exactly one page.
            assertEquals(letters.map { it.id }.toSet(), slots.map { it.letterId }.toSet())
            assertEquals(count, slots.size)
            assertTrue(pages.all { it.size <= 2 * FiefScenes.BOARD_ROWS })
            assertTrue(pages.all { page ->
                page.count { it.incoming } <= FiefScenes.BOARD_ROWS &&
                    page.count { !it.incoming } <= FiefScenes.BOARD_ROWS
            })
            assertTrue(pages.none { it.isEmpty() })
        }
        // A one-sided flood still pages: 9 incoming letters need 3 pages.
        val oneSided = (1L..9L).map { letter(it, 2, incoming = true, createdAt = 1000 + it, mode = "timed") }
        assertEquals(3, FiefScenes.boardPages(oneSided, emptySet(), 0, emptySet()).size)
    }

    @Test
    fun postStatusMatchesTheBoardBuckets() {
        assertEquals(PostStatus(0, 0, 0), Conversations.postStatus(emptyList(), emptySet()))
        val letters = listOf(
            letter(1, 2, incoming = true, unlocked = true),                       // ready
            letter(2, 2, incoming = true, unlocked = true, readAt = 5),           // history
            letter(3, 2, incoming = false, mode = "manual"),                      // waiting on me
            letter(4, 2, incoming = true, mode = "mutual", recipientApproved = false), // waiting on me
            letter(5, 2, incoming = true, mode = "timed"),                        // in transit
        )
        val status = Conversations.postStatus(letters, emptySet())
        assertEquals(5, status.total)
        assertEquals(1, status.ready)
        assertEquals(2, status.awaitingMe)
        assertEquals(3, status.urgent)
        // A letter opened locally in this session stops counting as ready.
        assertEquals(0, Conversations.postStatus(listOf(letters[0]), setOf(1L)).ready)
    }
}

class FiefMapTest {
    private val viewport = MapSize(400f, 800f)
    private val image = FiefScenes.VALLEY_IMAGE

    @Test
    fun spritesTransformAndHitTestWithZoomAndPan() {
        val scene = FiefScenes.valleyScene(vale(earned = 18), Fief.chain.toSet())
        val pan = FiefMap.clampPan(viewport, image, zoom = 2f, pan = MapPoint(90f, -120f))
        val home = scene.sprites.first { it.id == "home" }
        val screen = FiefMap.screenPoint(home.hitPoint, viewport, image, zoom = 2f, pan = pan)
        assertEquals(home, FiefMap.hitTest(screen, viewport, image, 2f, pan, scene.sprites, minTouchPx = 48f))
        // The pan is clamped to the plate, never beyond it.
        assertTrue(pan.x <= (FiefMap.fittedSize(viewport, image).width * 2f - viewport.width) / 2f)
        // A corner of the viewport hits nothing at all.
        assertNull(FiefMap.hitTest(MapPoint(399f, 799f), viewport, image, 2f, pan, scene.sprites, 48f))
    }

    @Test
    fun decorationIsNotEvenACandidateAndSmallSpritesKeepATouchTarget() {
        val scene = FiefScenes.valleyScene(vale(earned = 18), Fief.chain.toSet())
        val well = scene.sprites.first { it.id == "well" }
        val zoom = 1f
        val pan = MapPoint(0f, 0f)
        val onJetty = FiefMap.screenPoint(
            scene.sprites.first { it.id == "jetty" }.anchor, viewport, image, zoom, pan,
        )
        // Tapping the decorative jetty opens nothing; it is the start of a pan.
        assertNull(FiefMap.hitTest(onJetty, viewport, image, zoom, pan, scene.sprites, 48f))
        assertFalse(well.interactive)
        // The signpost is a small picture but keeps at least a 48 dp touch circle.
        val signpost = scene.sprites.first { it.id == "signpost" }
        val center = FiefMap.screenPoint(signpost.hitPoint, viewport, image, zoom, pan)
        val edge = MapPoint(center.x + 23f, center.y)
        assertEquals(signpost, FiefMap.hitTest(edge, viewport, image, zoom, pan, scene.sprites, 48f))
    }

    @Test
    fun focusPanCentresAPointAndStaysInsideThePlate() {
        val focus = FiefMap.focusPan(FiefScenes.OWN_CLEARING, viewport, image, zoom = 1.35f)
        val centred = FiefMap.screenPoint(FiefScenes.OWN_CLEARING, viewport, image, 1.35f, focus)
        val unpanned = FiefMap.screenPoint(FiefScenes.OWN_CLEARING, viewport, image, 1.35f, MapPoint(0f, 0f))
        val middle = MapPoint(viewport.width / 2f, viewport.height / 2f)
        assertTrue(
            kotlin.math.abs(centred.y - middle.y) < kotlin.math.abs(unpanned.y - middle.y),
        )
        // The camera never flies off the plate: the focus pan is already clamped.
        assertEquals(focus, FiefMap.clampPan(viewport, image, 1.35f, focus))
    }

    @Test
    fun roadRunsFromMyClearingToTheirs() {
        val start = FiefScenes.roadPoint(0f)
        val end = FiefScenes.roadPoint(1f)
        assertTrue(start.y > end.y)
        assertTrue(start.x < end.x)
        assertEquals(FiefScenes.road.first(), start)
        assertEquals(FiefScenes.road.last(), end)
        assertTrue((0..10).map { FiefScenes.roadPoint(it / 10f).y }.zipWithNext().all { (a, b) -> b <= a })
    }
}
