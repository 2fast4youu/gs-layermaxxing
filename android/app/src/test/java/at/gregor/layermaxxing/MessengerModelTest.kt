package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsTest {
    @Test
    fun letterStateCoversEveryGate() {
        assertEquals(LetterState.LOCKED_TIMED, Conversations.letterState(letter(1, 2, mode = "timed"), false))
        assertEquals(LetterState.LOCKED_RANDOM, Conversations.letterState(letter(1, 2, mode = "random"), false))
        assertEquals(LetterState.LOCKED_MANUAL, Conversations.letterState(letter(1, 2, mode = "manual"), false))
        assertEquals(LetterState.LOCKED_PRESENCE, Conversations.letterState(letter(1, 2, mode = "presence"), false))
        assertEquals(
            LetterState.LOCKED_MUTUAL_WAITING_ME,
            Conversations.letterState(letter(1, 2, mode = "mutual", incoming = true, recipientApproved = false), false),
        )
        assertEquals(
            LetterState.LOCKED_MUTUAL_WAITING_PEER,
            Conversations.letterState(letter(1, 2, mode = "mutual", incoming = true, recipientApproved = true), false),
        )
        assertEquals(LetterState.READY, Conversations.letterState(letter(1, 2, incoming = true, unlocked = true), false))
        assertEquals(LetterState.OPENED, Conversations.letterState(letter(1, 2, incoming = true, unlocked = true), true))
        assertEquals(
            LetterState.DELIVERED,
            Conversations.letterState(letter(1, 2, incoming = false, unlocked = true, readAt = null), false),
        )
        assertEquals(
            LetterState.READ,
            Conversations.letterState(letter(1, 2, incoming = false, unlocked = true, readAt = 5), false),
        )
    }

    @Test
    fun threadMergesLettersAndChatsInOneTimeline() {
        val chats = listOf(chatMsg(10, 1, 2, "hi", at = 100), chatMsg(11, 2, 1, "yo", at = 300))
        val letters = listOf(letter(12, 2, createdAt = 200), letter(13, 2, createdAt = 400))
        val entries = Conversations.threadEntries(chats, letters, ownUserId = 1, openedLetterIds = emptySet())
        assertEquals(listOf(10L, 12L, 11L, 13L), entries.map { it.id })
        assertTrue(entries[0] is ThreadEntry.Chat)
        assertTrue(entries[1] is ThreadEntry.Letter)
        // Outgoing detection: chat 10 sent by me (user 1) is outgoing.
        assertTrue(entries[0].outgoing)
        assertFalse(entries[2].outgoing)
    }

    @Test
    fun overviewIsBuiltFromFriendsNotOnlyChats() {
        val friends = listOf(user(2, "Bea"), user(3, "Ann"))
        val rules = listOf(settings(2, "Bea"), settings(3, "Ann"))
        // Only Bea has a chat thread; Ann is reachable but silent. Both must appear.
        val threads = listOf(
            ApiClient.ChatThread(2, "Bea", "🐝", "#111111", lastMessageAt = 500, unreadCount = 2,
                lastSenderId = 2, lastText = "letzte Zeile"),
        )
        val overview = Conversations.overview(friends, rules, threads, emptyList(), emptyList(), ownUserId = 1)
        assertEquals(2, overview.size)
        assertEquals("Bea", overview[0].friendName) // more recent activity first
        assertEquals("letzte Zeile", overview[0].preview.text)
        assertEquals(2, overview[0].unreadChats)
        assertEquals(Conversations.NO_MESSAGES, overview[1].preview.text)
        assertFalse(overview[1].hasNews)
    }

    @Test
    fun overviewPreviewNeverLeaksSealedBody() {
        val friends = listOf(user(2, "Bea"))
        val rules = listOf(settings(2, "Bea"))
        val incoming = listOf(letter(20, 2, title = "Brieftitel", coverNote = "offen", createdAt = 900))
        val overview = Conversations.overview(friends, rules, emptyList(), incoming, emptyList(), ownUserId = 1)
        assertTrue(overview[0].preview.sealed)
        // Title is openly readable; the encrypted body is never part of the preview.
        assertEquals("Brieftitel", overview[0].preview.text)
        assertEquals(1, overview[0].lockedLetters)
    }

    @Test
    fun silencedFriendStaysVisibleWithoutNews() {
        val friends = listOf(user(2, "Bea"))
        val rules = listOf(settings(2, "Bea", letters = false, chats = false))
        val overview = Conversations.overview(friends, rules, emptyList(), emptyList(), emptyList(), ownUserId = 1)
        assertEquals(1, overview.size)
        assertTrue(overview[0].silenced)
        assertFalse(overview[0].chatsEnabled)
    }

    @Test
    fun conversationIsOneFlowAndLettersUseRequiredBucketsWithoutBodyPreview() {
        val letters = listOf(
            letter(1, 2, incoming = false, mode = "manual", title = "A"),
            letter(2, 2, incoming = true, mode = "timed", title = "B"),
            letter(3, 2, incoming = true, unlocked = true, title = "C"),
            letter(4, 2, incoming = true, unlocked = true, readAt = 9, title = "Öffentlich", ciphertext = "VERSIEGELTER_BODY"),
        )
        val groups = Conversations.groupedLetters(letters, emptySet()).associateBy { it.bucket }
        assertEquals(listOf(1L), groups.getValue(LetterBucket.WAITING_ON_YOU).messages.map { it.id })
        assertEquals(listOf(2L), groups.getValue(LetterBucket.IN_TRANSIT).messages.map { it.id })
        assertEquals(listOf(3L), groups.getValue(LetterBucket.READY).messages.map { it.id })
        assertEquals(listOf(4L), groups.getValue(LetterBucket.OPENED_HISTORY).messages.map { it.id })
        assertEquals("Öffentlich", Conversations.letterPreviewText(letters.last()))
        assertFalse(Conversations.letterPreviewText(letters.last()).contains("VERSIEGELTER_BODY"))
        assertEquals(
            listOf(10L, 13L),
            Conversations.topicsWith(
                2, "B", listOf(topic(10, 2), topic(11, 3), topic(12, null, "personal"), topic(13, null)),
            ).map { it.id },
        )
        // The rooms are gone: chat and letters share exactly one ordered flow, and
        // the buckets survive only as jump badges and as the sorted letter stack.
        val chats = listOf(chatMsg(50, 1, 2, "hi", at = 500))
        val flow = Conversations.threadEntries(chats, letters, ownUserId = 1, openedLetterIds = emptySet())
        assertEquals(letters.size + chats.size, flow.size)
        assertEquals(flow.map { it.timestamp }.sorted(), flow.map { it.timestamp })
        assertEquals(setOf(1L, 2L, 3L, 4L, 50L), flow.map { it.id }.toSet())
    }

    @Test
    fun sealBandShowsOnlyActiveBucketsInFixedOrder() {
        val letters = listOf(
            letter(1, 2, incoming = false, mode = "manual", createdAt = 100),   // waiting on me
            letter(2, 2, incoming = false, mode = "manual", createdAt = 400),   // waiting on me, newer
            letter(3, 2, incoming = true, mode = "timed", createdAt = 200),     // in transit
            letter(4, 2, incoming = true, unlocked = true, createdAt = 300),    // ready
            letter(5, 2, incoming = true, unlocked = true, readAt = 9, createdAt = 50), // history
        )
        val band = Conversations.sealBand(Conversations.groupedLetters(letters, emptySet()))
        assertEquals(
            listOf(LetterBucket.WAITING_ON_YOU, LetterBucket.READY, LetterBucket.IN_TRANSIT),
            band.map { it.bucket },
        )
        assertEquals(listOf(2, 1, 1), band.map { it.count })
        // The jump target is the oldest letter of its bucket, never the newest.
        assertEquals(1L, band.first().oldestLetterId)
        // The opened history is not something to act on and never becomes a badge.
        assertTrue(band.none { it.bucket == LetterBucket.OPENED_HISTORY })
    }

    @Test
    fun sealBandAbsentWithoutLetters() {
        assertTrue(Conversations.sealBand(Conversations.groupedLetters(emptyList(), emptySet())).isEmpty())
        // Only history left: no badge either, the stack chip carries it.
        val onlyHistory = listOf(letter(1, 2, incoming = true, unlocked = true, readAt = 5))
        assertTrue(Conversations.sealBand(Conversations.groupedLetters(onlyHistory, emptySet())).isEmpty())
    }

    @Test
    fun withDayMarksInsertsExactlyOneMarkPerDayAndKeepsTheOrder() {
        val zone = java.time.ZoneOffset.UTC
        val day = 86400L
        val now = 10 * day + 3600
        val entries = Conversations.threadEntries(
            listOf(
                chatMsg(1, 1, 2, "a", at = 8 * day + 100),
                chatMsg(2, 2, 1, "b", at = 8 * day + 200),
                chatMsg(3, 1, 2, "c", at = 9 * day + 100),
                chatMsg(4, 1, 2, "d", at = 10 * day + 100),
            ),
            emptyList(), ownUserId = 1, openedLetterIds = emptySet(),
        )
        val items = Conversations.withDayMarks(entries, now, zone)
        val marks = items.filterIsInstance<TimelineItem.DayMark>()
        assertEquals(3, marks.size)
        assertEquals(marks.size, marks.map { it.dayStart }.toSet().size)
        assertEquals(listOf("Heute", "Gestern"), marks.map { it.label }.takeLast(2).reversed())
        // The entries keep their exact order and none of them is dropped.
        assertEquals(
            entries.map { it.id },
            items.filterIsInstance<TimelineItem.Entry>().map { it.entry.id },
        )
        assertTrue(items.first() is TimelineItem.DayMark)
        // A seal badge jumps to the letter's own row, never to a day mark.
        val withLetter = Conversations.withDayMarks(
            Conversations.threadEntries(emptyList(), listOf(letter(77, 2, createdAt = 8 * day)), 1, emptySet()),
            now, zone,
        )
        assertEquals(1, Conversations.timelineIndexOf(withLetter, letterId = 77))
        assertNull(Conversations.timelineIndexOf(items, letterId = 77))
    }

    @Test
    fun composerPlanHonorsBilateralGates() {
        val open = composerPlan(LetterAccess.forSettings(settings(2, "Bea")), chatsEnabled = true, openTopics = 3)
        assertTrue(open.sealVisible)
        assertTrue(open.inputEnabled)
        assertEquals("Nachricht", open.placeholder)
        assertEquals(3, open.topicBadge)
        assertNull(open.blockedReason)

        val chatOff = composerPlan(LetterAccess.forSettings(settings(2, "Bea", chats = false)), false, 0)
        assertTrue(chatOff.sealVisible)
        assertFalse(chatOff.inputEnabled)
        assertTrue(chatOff.placeholder.contains("aus"))
        assertNull(chatOff.blockedReason)

        val lettersOff = composerPlan(LetterAccess.forSettings(settings(2, "Bea", letters = false)), true, 0)
        assertFalse(lettersOff.sealVisible)
        assertTrue(lettersOff.inputEnabled)
        assertNull(lettersOff.blockedReason)

        val silenced = composerPlan(
            LetterAccess.forSettings(settings(2, "Bea", letters = false, chats = false)), false, 0,
        )
        assertFalse(silenced.sealVisible)
        assertFalse(silenced.inputEnabled)
        assertTrue(silenced.blockedReason!!.contains("Freundschaftsregeln"))
    }
}

class RequestsTest {
    @Test
    fun epPointLabelsUseSingularAndKeepHistoricalPlural() {
        assertEquals("1 Ebenen-Punkt", formatEpPoints(1))
        assertEquals("2 Ebenen-Punkte", formatEpPoints(2))
        assertEquals("25 Ebenen-Punkte", formatEpPoints(25))
    }

    @Test
    fun inboxOrdersSettingsThenEpThenFriend() {
        val incomingProposal = ApiClient.SettingsProposal(7, 2, "Bea", proposerId = 2,
            lettersEnabled = true, chatsEnabled = true, epEnabled = true, minLetterDelaySeconds = 0)
        val rules = listOf(settings(2, "Bea", ep = true, incoming = incomingProposal))
        val overview = ApiClient.EpOverview(
            incoming = listOf(ep(9, proposerId = 2, beneficiaryId = 1, points = 1, status = "pending")),
            outgoing = emptyList(), history = emptyList(), given = 0, received = 0, levelName = "x",
        )
        val friendRequests = listOf(ApiClient.IncomingRequest(3, 4, "Cal", 1000))
        val inbox = RequestInbox.collect(friendRequests, rules, overview)
        assertEquals(listOf(RequestKind.SETTINGS, RequestKind.EP, RequestKind.FRIEND), inbox.map { it.kind })
        assertEquals("P2 schlägt dir 1 Ebenen-Punkt vor", inbox[1].headline)
    }

    @Test
    fun epFromNonFriendIsNotDelivered() {
        // No settings row for proposer 99 → not an active friend → drop it.
        val overview = ApiClient.EpOverview(
            incoming = listOf(ep(9, proposerId = 99, beneficiaryId = 1, points = 40, status = "pending")),
            outgoing = emptyList(), history = emptyList(), given = 0, received = 0, levelName = "x",
        )
        val inbox = RequestInbox.collect(emptyList(), emptyList(), overview)
        assertTrue(inbox.isEmpty())
    }

    @Test
    fun dialogSnoozeAndDismissRespectKeys() {
        val requests = listOf(
            PendingRequest(RequestKind.SETTINGS, 1, 2, "Bea", "a", "b"),
            PendingRequest(RequestKind.EP, 5, 3, "Cal", "c", "d"),
        )
        assertEquals(1L, RequestInbox.dialog(requests, emptySet(), snoozed = false)?.id)
        assertNull(RequestInbox.dialog(requests, emptySet(), snoozed = true))
        // Dismissing the first surfaces the next; the cards stay regardless.
        assertEquals(5L, RequestInbox.dialog(requests, setOf("SETTINGS-1"), snoozed = false)?.id)
    }

    @Test
    fun epOpportunityAppearsForBothSidesAfterRead() {
        val letters = listOf(
            letter(30, 2, incoming = true, unlocked = true, readAt = 10),   // I opened theirs
            letter(31, 2, incoming = false, unlocked = true, readAt = 20),  // they read mine
            letter(32, 2, incoming = true, unlocked = true, readAt = null),  // not opened yet
        )
        val opportunities = EpOpportunities.forFriend(
            2, "Bea", letters, epEnabled = true, linkedLetterIds = emptySet(), dismissedLetterIds = emptySet(),
        )
        assertEquals(listOf(30L, 31L), opportunities.map { it.letterId })
        assertEquals(EpRole.I_OPENED_THEIR_LETTER, opportunities[0].role)
        assertEquals(EpRole.THEY_READ_MY_LETTER, opportunities[1].role)
    }

    @Test
    fun epOpportunitySuppressedWhenDisabledOrAlreadyLinkedOrDismissed() {
        val letters = listOf(letter(30, 2, unlocked = true, readAt = 10), letter(31, 2, unlocked = true, readAt = 10))
        assertTrue(EpOpportunities.forFriend(2, "Bea", letters, false, emptySet(), emptySet()).isEmpty())
        assertEquals(
            listOf(31L),
            EpOpportunities.forFriend(2, "Bea", letters, true, setOf(30L), emptySet()).map { it.letterId },
        )
        assertEquals(
            listOf(30L),
            EpOpportunities.forFriend(2, "Bea", letters, true, emptySet(), setOf(31L)).map { it.letterId },
        )
    }

    @Test
    fun linkedLetterIdsCollectFromEveryBucket() {
        val overview = ApiClient.EpOverview(
            incoming = listOf(ep(1, 2, 1, 10, "pending", letterId = 100)),
            outgoing = listOf(ep(2, 1, 2, 10, "pending", letterId = 200)),
            history = listOf(ep(3, 2, 1, 10, "accepted", letterId = 300)),
            given = 0, received = 0, levelName = "x",
        )
        assertEquals(setOf(100L, 200L, 300L), EpOpportunities.linkedLetterIds(overview))
    }
}

class CastlesTest {
    @Test
    fun couriersMapReleaseModesAndFallBack() {
        assertEquals(Courier.RIDER, Castles.courierFor("timed"))
        assertEquals(Courier.PIGEON, Castles.courierFor("random"))
        assertEquals(Courier.OWL, Castles.courierFor("presence"))
        assertEquals(Courier.HERALD, Castles.courierFor("mutual"))
        assertEquals(Courier.COURTYARD, Castles.courierFor("manual"))
        assertEquals(Courier.RIDER, Castles.courierFor("nonsense"))
    }

    @Test
    fun castleBackChainNeverSkipsALevel() {
        assertEquals(CastlePlace.BOARD, Castles.back(CastlePlace.COMPOSER))
        assertEquals(CastlePlace.COURTYARD, Castles.back(CastlePlace.BOARD))
        assertEquals(CastlePlace.COURTYARD, Castles.back(CastlePlace.TREASURY))
        assertEquals(CastlePlace.COURTYARD, Castles.back(CastlePlace.BUILD_SITE))
        assertEquals(CastlePlace.VALE, Castles.back(CastlePlace.COURTYARD))
        assertNull(Castles.back(CastlePlace.VALE))
        // The whole chain from the composer walks out one place at a time.
        val walked = generateSequence(CastlePlace.COMPOSER) { Castles.back(it) }.toList()
        assertEquals(
            listOf(CastlePlace.COMPOSER, CastlePlace.BOARD, CastlePlace.COURTYARD, CastlePlace.VALE),
            walked,
        )
    }

    @Test
    fun valesDeriveEarnedEpPerFriendshipSymmetrically() {
        val friends = listOf(user(2, "Bea"))
        val rules = listOf(settings(2, "Bea", ep = true))
        val history = listOf(
            ep(1, proposerId = 2, beneficiaryId = 1, points = 8), // I earned 8 from Bea
            ep(2, proposerId = 1, beneficiaryId = 2, points = 3), // Bea earned 3 from me
        )
        val vales = Castles.vales(
            friends, rules, history, mapOf(2L to 3),
            mapOf(2L to setOf(BuildStep.WELL, BuildStep.PATH)), ownUserId = 1,
        )
        assertEquals(1, vales.size)
        assertEquals(8, vales[0].myEarnedEp)
        assertEquals(3, vales[0].theirEarnedEp)
        assertEquals(2, vales[0].spent)
        assertEquals(6, vales[0].balance) // 8 earned − 2 built
        assertEquals(3, vales[0].lettersInTransit)
        assertTrue(vales[0].epEnabled)
        assertFalse(vales[0].abandoned)
        // The other side grows only from what they accepted, never from my ledger.
        assertEquals(0, vales[0].hutStage)
        assertEquals(1, vales[0].theirHutStage)
    }

    @Test
    fun valesNeverInventAFriendshipAndNeverGoNegative() {
        val friends = listOf(user(2, "Bea"))
        // No settings row at all: an abandoned friendship keeps every feature off.
        val vales = Castles.vales(friends, emptyList(), emptyList(), emptyMap(), emptyMap(), ownUserId = 1)
        assertTrue(vales[0].abandoned)
        assertFalse(vales[0].epEnabled)
        assertFalse(vales[0].lettersEnabled)
        assertEquals(0, vales[0].balance)
        // A legacy-heavy ledger with almost no accepted EP still owes nothing.
        val rich = Castles.vales(
            friends, listOf(settings(2, "Bea", ep = true)), emptyList(), emptyMap(),
            mapOf(2L to Fief.chain.toSet()), ownUserId = 1,
        )
        assertEquals(0, rich[0].balance)
        assertEquals(18, rich[0].spent)
        // Without a logged-in user there is no valley at all.
        assertTrue(Castles.vales(friends, emptyList(), emptyList(), emptyMap(), emptyMap(), null).isEmpty())
    }

    @Test
    fun deliveryStopsExistOnlyForRealSealedLettersAndCarryTheirCourier() {
        val letters = listOf(
            letter(40, 2, mode = "timed", unlocked = false, createdAt = 100, releaseAt = 200),
            letter(41, 2, mode = "random", unlocked = false, createdAt = 90),
            letter(42, 2, mode = "timed", unlocked = true, createdAt = 80), // arrived → off the road
        )
        val stops = FiefScenes.deliveryStops(letters, "Bea", now = 150)
        assertEquals(listOf(40L, 41L), stops.map { it.letterId })
        assertEquals(Courier.RIDER, stops[0].courier)
        assertEquals(Courier.PIGEON, stops[1].courier)
        // Nothing on the road promises a clock time, in any mode.
        assertTrue(stops.none { it.label.contains("Uhr") })
        // A released letter is never a figure on the road.
        assertTrue(FiefScenes.deliveryStops(listOf(letters[2]), "Bea", 150).isEmpty())
        // Without letters there is no motion at all: no random walker exists.
        assertTrue(FiefScenes.deliveryStops(emptyList(), "Bea", 150).isEmpty())
    }

    @Test
    fun aTimedLetterReallyTravelsWhileOtherGatesStandWhereTheyBelong() {
        val timed = letter(50, 2, mode = "timed", createdAt = 1_000, releaseAt = 2_000)
        val early = FiefScenes.journeyFraction(timed, now = 1_100)
        val late = FiefScenes.journeyFraction(timed, now = 1_900)
        assertTrue("a timed letter must move forward over time", late > early)
        // Clamped at both ends, so a courier never stands inside a hut.
        assertTrue(FiefScenes.journeyFraction(timed, now = 0) >= .08f)
        assertTrue(FiefScenes.journeyFraction(timed, now = 9_999) <= .92f)
        // A random window keeps its secret; consent gates wait at the destination;
        // a manual letter has not left the sender's own yard.
        assertEquals(.5f, FiefScenes.journeyFraction(letter(51, 2, mode = "random"), 5), .0001f)
        assertEquals(.85f, FiefScenes.journeyFraction(letter(52, 2, mode = "mutual"), 5), .0001f)
        assertEquals(.85f, FiefScenes.journeyFraction(letter(53, 2, mode = "presence"), 5), .0001f)
        assertEquals(.12f, FiefScenes.journeyFraction(letter(54, 2, mode = "manual"), 5), .0001f)
    }

    @Test
    fun deliveryDirectionMirrorsTheRoadAndTheRoadStaysLegible() {
        val incoming = letter(60, 2, incoming = true, mode = "manual")
        val outgoing = letter(61, 2, incoming = false, mode = "manual")
        val stops = FiefScenes.deliveryStops(listOf(incoming, outgoing), "Bea", 10).associateBy { it.letterId }
        // 0 is my hut, 1 is theirs: an incoming letter sits near their end.
        assertTrue(stops.getValue(60L).roadPosition > stops.getValue(61L).roadPosition)
        assertTrue(stops.getValue(60L).label.contains("von Bea"))
        assertTrue(stops.getValue(61L).label.contains("zu Bea"))
        // The road never shows more couriers than it can keep readable.
        val many = (0 until 12).map { letter(100L + it, 2, mode = "manual", createdAt = 100L + it) }
        assertEquals(FiefScenes.MAX_DELIVERIES, FiefScenes.deliveryStops(many, "Bea", 10).size)
    }

    @Test
    fun visitingAFriendShowsOnlyMyOwnLettersAndTheirDerivedStage() {
        val letters = listOf(
            letter(70, 2, incoming = false, unlocked = true, readAt = null),
            letter(71, 2, incoming = false, unlocked = false),
            letter(72, 2, incoming = true, unlocked = true), // their letter to me: not their post
        )
        val visit = FiefScenes.visitStatus(letters)
        assertEquals(2, visit.total)
        assertEquals(1, visit.ready)
        // A visit is read-only by construction: no sprite in the scene can be tapped.
        val scene = FiefScenes.friendCourtScene(vale(theirEarned = 9))
        assertTrue(scene.sprites.none { it.interactive })
        // Their court grows only from the EP they accepted from me.
        assertEquals(
            Fief.hutStages[Fief.friendHutStage(9)],
            scene.sprites.first { it.id == "fc-hut" }.asset,
        )
        assertEquals(
            Fief.hutStages[0],
            FiefScenes.friendCourtScene(vale(theirEarned = 0)).sprites.first { it.id == "fc-hut" }.asset,
        )
    }

    @Test
    fun legacyBuildingPricesStillReadableForMigration() {
        assertEquals(3, Castles.spentFor(mapOf(Building.PALISADE to 1, Building.WELL to 1)))
        // Levels above the old maximum are clamped instead of trusted.
        assertEquals(Castles.spentFor(mapOf(Building.WELL to 3)), Castles.spentFor(mapOf(Building.WELL to 9)))
    }
}

class LockedLetterEffectsTest {
    @Test
    fun fifthTapWithinFourSecondsTriggersFogAndResetIsClean() {
        val tracker = LockedLetterTapTracker()
        repeat(4) { index ->
            assertEquals(LockedTapResult.PLAY_SOUND, tracker.register(7, index * 1_000L))
        }
        assertEquals(LockedTapResult.SHOW_FOG, tracker.register(7, 4_000L))
        assertEquals(LockedTapResult.PLAY_SOUND, tracker.register(7, 4_100L))
        // Another letter owns its own sequence.
        assertEquals(LockedTapResult.PLAY_SOUND, tracker.register(8, 4_200L))
    }

    @Test
    fun tapsOutsideWindowDoNotTriggerAndSoundsNeverRepeatDirectly() {
        val tracker = LockedLetterTapTracker()
        repeat(4) { index -> tracker.register(7, index * 1_000L) }
        assertEquals(LockedTapResult.PLAY_SOUND, tracker.register(7, 4_001L))

        val picker = NonRepeatingSoundPicker(size = 6, nextInt = { 0 })
        val picks = List(20) { picker.next() }
        assertTrue(picks.zipWithNext().all { (a, b) -> a != b })
        assertTrue(picks.all { it in 0..5 })
    }
}

class NavigationTest {
    @Test
    fun flagOffShowsExactlyChatsAndMoreAndNoPeopleTab() {
        val tabs = Navigation.tabs(castleExperiment = false)
        assertEquals(listOf(MainTab.CHATS, MainTab.MORE), tabs)
        assertFalse(tabs.contains(MainTab.CASTLES))
        // "Leute" is gone as a destination entirely, not merely hidden.
        assertTrue(MainTab.entries.none { it.name == "PEOPLE" || it.label == "Leute" })
    }

    @Test
    fun flagOnAddsCastlesAsThirdTab() {
        val tabs = Navigation.tabs(castleExperiment = true)
        assertEquals(3, tabs.size)
        assertEquals(MainTab.CASTLES, tabs.last())
        // The castle tab is only ever added, never reorders the messenger tabs.
        assertEquals(Navigation.tabs(false), tabs.dropLast(1))
    }

    @Test
    fun castleFlagStoreDefaultsOffAndWritesOnlyItsOwnKey() {
        val values = mutableMapOf<String, String>()
        val store = CastleFlagStore(read = { values[it] }, write = { k, v -> values[k] = v })
        assertFalse(store.enabled("gerfried"))
        store.setEnabled("gerfried", true)
        assertTrue(store.enabled("gerfried"))
        // Per-profile isolation: enabling on Gerfried leaves Gregor off.
        assertFalse(store.enabled("gregor"))
        assertEquals(setOf("castle_experiment_gerfried"), values.keys)
    }
}

/**
 * The hard product rule: with the castle experiment switched off the app is a
 * complete messenger. Nothing below may reference a map, a castle, a building or
 * a build ledger — that is the point of these tests.
 */
class CastleIndependenceTest {
    @Test
    fun flagOffKeepsExactlyTwoTabsAndTheWholeConversationInOneFlow() {
        assertEquals(2, Navigation.tabs(castleExperiment = false).size)
        assertEquals(listOf(MainTab.CHATS, MainTab.MORE), Navigation.tabs(false))
        // The conversation itself needs no valley state whatsoever: the seal band,
        // the composer gates and the timeline are built from letters, chat and the
        // bilateral rules alone.
        val letters = listOf(
            letter(1, 2, incoming = true, unlocked = true, createdAt = 300),
            letter(2, 2, incoming = false, mode = "manual", createdAt = 100),
        )
        val band = Conversations.sealBand(Conversations.groupedLetters(letters, emptySet()))
        assertEquals(listOf(LetterBucket.WAITING_ON_YOU, LetterBucket.READY), band.map { it.bucket })
        val plan = composerPlan(LetterAccess.forSettings(settings(2, "Bea")), chatsEnabled = true, openTopics = 0)
        assertTrue(plan.sealVisible && plan.inputEnabled)
        val flow = Conversations.withDayMarks(
            Conversations.threadEntries(listOf(chatMsg(9, 1, 2, "hi", at = 200)), letters, 1, emptySet()),
            nowEpochSeconds = 1_000, zone = java.time.ZoneOffset.UTC,
        )
        assertEquals(3, flow.count { it is TimelineItem.Entry })
        // Requests are filtered to this friendship, never to a valley.
        val requests = listOf(
            PendingRequest(RequestKind.EP, 1, 2, "Bea", "a", "b"),
            PendingRequest(RequestKind.SETTINGS, 2, 3, "Cal", "c", "d"),
        )
        assertEquals(listOf(1L), RequestInbox.forFriend(requests, 2).map { it.id })
    }

    @Test
    fun threadRequestsFilterByFriendAndKeepTheirBindingOrder() {
        val requests = listOf(
            PendingRequest(RequestKind.SETTINGS, 10, 2, "Bea", "regeln", ""),
            PendingRequest(RequestKind.EP, 11, 2, "Bea", "ep", ""),
            PendingRequest(RequestKind.FRIEND, 12, 3, "Cal", "freund", ""),
        )
        assertEquals(listOf(10L, 11L), RequestInbox.forFriend(requests, 2).map { it.id })
        assertEquals(
            listOf(RequestKind.SETTINGS, RequestKind.EP),
            RequestInbox.forFriend(requests, 2).map { it.kind },
        )
        assertTrue(RequestInbox.forFriend(requests, 99).isEmpty())
        // A foreign proposal never leaks into someone else's conversation.
        assertTrue(RequestInbox.forFriend(requests, 3).none { it.friendId == 2L })
    }

    @Test
    fun letterAccessDependsOnlyOnTheBilateralFriendshipRules() {
        val open = LetterAccess.forSettings(settings(2, "Bea", letters = true))
        assertTrue(open.enabled)
        assertNull(open.reason)
        assertEquals("✦ Brief", open.label)

        val closed = LetterAccess.forSettings(settings(2, "Bea", letters = false))
        assertFalse(closed.enabled)
        assertTrue(closed.reason!!.contains("Freundschaftsregeln"))

        // No settings row at all is an honest "not possible", never a dead button.
        val gone = LetterAccess.forSettings(null)
        assertFalse(gone.enabled)
        assertTrue(gone.reason!!.isNotBlank())
    }

    @Test
    fun everyMessengerActionStaysReachableWithoutAnyCastleState() {
        val friends = listOf(user(2, "Bea"))
        val rules = listOf(settings(2, "Bea", letters = true, chats = true, ep = true))
        val letters = listOf(
            letter(1, 2, incoming = true, unlocked = true, readAt = 5, title = "Gelesen"),
            letter(2, 2, incoming = false, mode = "manual"),
        )
        // Conversation list, letter buckets, EP offers and topics: all built from
        // server data plus friendship rules, with no castle input in sight.
        val overview = Conversations.overview(friends, rules, emptyList(), listOf(letters[0]), listOf(letters[1]), 1)
        assertEquals(1, overview.size)
        assertTrue(overview[0].lettersEnabled)
        assertTrue(overview[0].epEnabled)
        assertEquals(2, Conversations.groupedLetters(letters, emptySet()).size)
        assertTrue(LetterAccess.forSettings(rules.first()).enabled)
        assertEquals(
            listOf(1L),
            EpOpportunities.forFriend(2, "Bea", letters, true, emptySet(), emptySet()).map { it.letterId },
        )
        assertEquals(listOf(10L), Conversations.topicsWith(2, "Bea", listOf(topic(10, 2), topic(11, 3))).map { it.id })
        // Every release mode still exists and maps to a courier only for display.
        listOf("timed", "random", "presence", "mutual", "manual").forEach { mode ->
            assertTrue(Conversations.letterState(letter(9, 2, mode = mode), false).locked)
        }
    }
}
