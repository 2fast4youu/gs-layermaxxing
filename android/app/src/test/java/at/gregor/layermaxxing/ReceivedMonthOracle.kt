package at.gregor.layermaxxing

/** Independent calendar/capacity oracle for physical received-mail pages. */
internal fun receivedMonthPages(messages: List<ApiClient.Message>, capacity: Int): List<List<Long>> = messages
    .filter { it.incoming && it.groupId == null }
    .distinctBy { it.id }
    .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
    .groupBy { java.time.Instant.ofEpochSecond(it.createdAt).atZone(java.time.ZoneOffset.UTC).let { d -> d.year to d.monthValue } }
    .values.flatMap { month -> month.chunked(capacity).map { batch -> batch.map { it.id } } }
