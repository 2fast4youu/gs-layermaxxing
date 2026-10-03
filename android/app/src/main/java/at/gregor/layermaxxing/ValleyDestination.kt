package at.gregor.layermaxxing

/** Map destinations link real existing features; they grant no extra permissions. */
enum class ValleyDestination(val label: String, val detail: String, val anchor: MapPoint) {
    CONVERSATIONS("💬 Treffpunkt", "Alle Gespräche und Freundschaftsregeln", MapPoint(.522f, .254f)),
    TOPICS("📝 Schwarzes Brett", "Private, Freundschafts- und Gruppenthemen", MapPoint(.767f, .345f)),
    GROUPS("👥 Gruppenplatz", "Gruppen und gemeinsame Briefe", MapPoint(.244f, .345f)),
    PEOPLE("🧭 Wegweiser", "Freunde finden und verwalten", MapPoint(.752f, .124f)),
    GLOSSARY("📖 Bibliothek", "App-Begriffe verständlich erklärt", MapPoint(.249f, .498f)),
    SPARKS("✨ Funkenplatz", "Freundesfunken senden und empfangen", MapPoint(.742f, .498f)),
    ARCHIVE("✉ Postarchiv", "Alle Briefe und Prüfdateien", MapPoint(.420f, .827f)),
    EP("★ EP-Verwaltung", "EP vorschlagen und Verlauf prüfen", MapPoint(.728f, .820f)),
    SETTINGS("⚙ Gemeindehaus", "Profil, Modus, Konten, Geräte und Sicherheit", MapPoint(.254f, .133f)),
}

object VillageScenes {
    val HOME = MapPoint(.288f, .661f)
    val FRIEND = MapPoint(.723f, .661f)
    const val PLATE = "village_plate"
    const val HIT = "village_hit"
    fun spot(id: String, point: MapPoint, label: String) = SceneSprite(
        id = id, asset = HIT, anchor = point, widthFraction = .18f,
        z = 20, interactive = true, label = label, pivotY = .5f, hitRadius = .09f,
    )
    fun valley(friendName: String?): Scene = Scene(
        "village", PLATE, MapSize(1024f, 1536f),
        ValleyDestination.entries.map { spot(it.name, it.anchor, "${it.label}: ${it.detail}") } +
            spot("home", HOME, "Dein Hof: Post, Schatzkammer und Ausbau") +
            spot("friend", FRIEND, friendName?.let { "Hof von $it" } ?: "Freunde finden"),
        3f,
    )
    fun courtyard() = Scene("village-court", PLATE, MapSize(1024f, 1536f), listOf(
        spot("cy-board", ValleyDestination.ARCHIVE.anchor, "Poststelle"),
        spot("cy-chest", ValleyDestination.EP.anchor, "Schatzkammer"),
        spot("cy-build", HOME, "Hof ausbauen"),
    ), 3f)
}
