package at.gregor.layermaxxing

/** Independent global capacity oracle for physical received-mail pages. */
internal fun receivedMonthPages(messages: List<ApiClient.Message>, capacity: Int): List<List<Long>> = messages
    .filter { it.incoming && it.groupId == null && it.unlocked }
    .distinctBy { it.id }
    .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
    .chunked(capacity).map { batch -> batch.map { it.id } }
