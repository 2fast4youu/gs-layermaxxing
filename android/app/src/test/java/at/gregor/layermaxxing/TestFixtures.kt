package at.gregor.layermaxxing

/** Builders keep the wide ApiClient data classes readable in the tests of this package. */
internal fun user(id: Long, name: String) = ApiClient.UserSummary(id, name)

internal fun settings(
    friendId: Long, name: String, letters: Boolean = true, chats: Boolean = true,
    ep: Boolean = false, delay: Long = 0,
    incoming: ApiClient.SettingsProposal? = null, outgoing: ApiClient.SettingsProposal? = null,
) = ApiClient.FriendshipSettings(friendId, name, letters, chats, ep, delay, incoming, outgoing)

internal fun letter(
    id: Long, peerId: Long, peerName: String = "Peer", incoming: Boolean = true,
    mode: String = "timed", unlocked: Boolean = false, readAt: Long? = null,
    createdAt: Long = 1000, title: String = "", coverNote: String = "",
    senderApproved: Boolean = false, recipientApproved: Boolean = false, oneTime: Boolean = false,
    ciphertext: String? = null, releaseAt: Long? = null,
) = ApiClient.Message(
    id = id, peerId = peerId, peerName = peerName, incoming = incoming, title = title,
    coverNote = coverNote, proofStatus = "sealed", createdAt = createdAt, mode = mode,
    releaseAt = releaseAt, randomFrom = null, randomTo = null, unlocked = unlocked, oneTime = oneTime,
    readAt = readAt, senderApproved = senderApproved, recipientApproved = recipientApproved,
    attachmentName = null, attachmentMime = null, groupId = null, reactions = emptyList(),
    ciphertext = ciphertext, nonce = null, encryptionKey = null,
)

internal fun chatMsg(id: Long, senderId: Long, recipientId: Long, text: String, at: Long) =
    ApiClient.ChatMessage(id, senderId, recipientId, text, at, null)

internal fun ep(
    id: Long, proposerId: Long, beneficiaryId: Long, points: Int, status: String = "accepted",
    letterId: Long? = null, title: String = "Danke",
) = ApiClient.EpProposal(
    id, proposerId, "P$proposerId", beneficiaryId, "B$beneficiaryId", points, title, null,
    letterId, status, 1000,
)

internal fun topic(id: Long, targetId: Long?, targetType: String = "friend") = ApiClient.Topic(
    id = id, title = "Tag $id", details = "", creatorName = "A",
    targetType = targetType, targetName = "B", targetId = targetId, createdAt = 1,
    completedAt = null, completedByName = null, canDelete = true,
)

internal fun vale(
    earned: Int = 0, theirEarned: Int = 0, built: Set<BuildStep> = emptySet(),
    epEnabled: Boolean = true, lettersEnabled: Boolean = true, transit: Int = 0,
) = Vale(
    friendId = 9, friendName = "Bea", avatarEmoji = "🐝", myEarnedEp = earned, theirEarnedEp = theirEarned,
    built = built, lettersInTransit = transit, epEnabled = epEnabled, lettersEnabled = lettersEnabled,
    abandoned = false,
)
