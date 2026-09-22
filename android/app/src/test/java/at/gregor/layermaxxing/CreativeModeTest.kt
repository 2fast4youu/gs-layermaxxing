package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three-key rule of creative mode and the promise that free building can
 * never pose as real progress. Everything here is server data plus pure Kotlin,
 * so a production build, a revoked entitlement or a disabled switch each win
 * deterministically.
 */
class CreativeModeTest {
    @Test
    fun eligibilityNeedsTestRoleAndEntitlement() {
        assertTrue(CreativeMode.eligible(serverRole = "test", entitled = true))
        assertFalse(CreativeMode.eligible(serverRole = "test", entitled = false))
        assertFalse(CreativeMode.eligible(serverRole = "production", entitled = true))
        // An old server that reports no role at all can never activate the mode.
        assertFalse(CreativeMode.eligible(serverRole = null, entitled = true))
    }

    @Test
    fun activationNeedsAllThreeKeys() {
        assertTrue(CreativeMode.active("test", entitled = true, localSwitch = true))
        assertFalse(CreativeMode.active("test", entitled = true, localSwitch = false))
        assertFalse(CreativeMode.active("test", entitled = false, localSwitch = true))
        assertFalse(CreativeMode.active("production", entitled = true, localSwitch = true))
    }

    @Test
    fun fastForwardIsOfferedOnlyForPurelyTimeBasedGates() {
        // Time is the only thing a test account may pull forward.
        assertTrue(CreativeMode.canAdvance(active = true, mode = "timed", unlocked = false))
        assertTrue(CreativeMode.canAdvance(active = true, mode = "random", unlocked = false))
        // Everything that waits for somebody else's decision keeps waiting:
        // fast-forwarding those would forge a consent that was never given.
        listOf("mutual", "presence", "manual").forEach { mode ->
            assertFalse(
                "$mode waits for a decision and must never be advanced",
                CreativeMode.canAdvance(active = true, mode = mode, unlocked = false),
            )
        }
        assertEquals(setOf("timed", "random"), CreativeMode.ADVANCEABLE_MODES)
    }

    @Test
    fun fastForwardNeedsTheWholeModeAndAnUnreleasedLetter() {
        // Without the three-key mode the action does not exist at all …
        assertFalse(CreativeMode.canAdvance(active = false, mode = "timed", unlocked = false))
        // … and an already released letter has nothing left to advance.
        assertFalse(CreativeMode.canAdvance(active = true, mode = "timed", unlocked = true))
        // An unknown mode from a newer server is refused, not guessed at.
        assertFalse(CreativeMode.canAdvance(active = true, mode = "brandneu", unlocked = false))
    }

    @Test
    fun testLetterPresetsOnlyUseGatesThatCanBeWalkedAlone() {
        assertTrue(CreativeMode.LETTER_PRESETS.isNotEmpty())
        CreativeMode.LETTER_PRESETS.forEach { (label, mode, seconds) ->
            assertTrue(label.isNotBlank())
            assertTrue(
                "a test preset must stay fast-forwardable, else the loop needs a second person",
                mode in CreativeMode.ADVANCEABLE_MODES,
            )
            assertTrue("a release must lie in the future", seconds > 0)
        }
    }

    @Test
    fun freeBuildingFollowsTheChainButNeverThePrice() {
        // Free builds ignore coins entirely …
        var built = emptySet<BuildStep>()
        Fief.chain.forEach { step -> built = Fief.buildFree(built, step) }
        assertEquals(Fief.chain.toSet(), built)
        // … but still refuse to skip a strand or build twice.
        assertTrue(Fief.buildFree(emptySet(), BuildStep.KEEP).isEmpty())
        assertTrue(Fief.buildFree(emptySet(), BuildStep.BRIDGE).isEmpty())
        assertEquals(built, Fief.buildFree(built, BuildStep.WELL))
    }

    @Test
    fun creativeValeSpendsNothingAndKeepsTheHonestEp() {
        // The full free ledger would owe 18 EP in the real economy; in creative
        // mode nothing is spent and the chest keeps showing the accepted EP.
        val creative = vale(earned = 2, built = Fief.chain.toSet()).copy(creative = true)
        assertEquals(0, creative.spent)
        assertEquals(2, creative.balance)
        assertEquals(3, creative.hutStage)
        // The same ledger without creative mode is clamped, never negative.
        val real = vale(earned = 2, built = Fief.chain.toSet())
        assertEquals(18, real.spent)
        assertEquals(0, real.balance)
        // The friend's side derives from accepted EP alone, in both modes.
        assertEquals(creative.theirHutStage, real.theirHutStage)
    }

    @Test
    fun creativePlansAreFreeButKeepTheBilateralEpGate() {
        val free = FiefScenes.buildPlans(emptySet(), earnedEp = 0, epEnabled = true, free = true)
        assertEquals(2, free.size)
        assertTrue(free.all { it.affordable && it.free && !it.locked })
        // EP switched off in this friendship locks the plans in creative mode too.
        val gated = FiefScenes.buildPlans(emptySet(), earnedEp = 0, epEnabled = false, free = true)
        assertTrue(gated.all { it.locked && !it.affordable && !it.free })
        // Without creative mode the plans price and clamp exactly as before.
        val real = FiefScenes.buildPlans(emptySet(), earnedEp = 0, epEnabled = true, free = false)
        assertTrue(real.none { it.affordable || it.free })
    }

    @Test
    fun valesCarryTheCreativeFlagThrough() {
        val friends = listOf(user(2, "Bea"))
        val rules = listOf(settings(2, "Bea", ep = true))
        val creative = Castles.vales(friends, rules, emptyList(), emptyMap(), emptyMap(), 1, creative = true)
        assertTrue(creative.single().creative)
        val normal = Castles.vales(friends, rules, emptyList(), emptyMap(), emptyMap(), 1)
        assertFalse(normal.single().creative)
    }
}
