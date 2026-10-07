package at.gregor.layermaxxing

/** Map destinations link real existing features; they grant no extra permissions. */
enum class ValleyDestination(val label: String, val detail: String, val anchor: MapPoint) {
    CONVERSATIONS("Treffpunkt", "Chatte mit deinen Freunden", MapPoint(.526f, .245f)),
    TOPICS("Schwarzes Brett", "Private, Freundschafts- und Gruppenthemen", MapPoint(.802f, .371f)),
    GROUPS("Gruppenplatz", "Gruppen und gemeinsame Briefe", MapPoint(.242f, .341f)),
    PEOPLE("Wegweiser", "Freunde finden und verwalten", MapPoint(.774f, .110f)),
    GLOSSARY("Bibliothek", "App-Begriffe verständlich erklärt", MapPoint(.260f, .506f)),
    SPARKS("Funkenplatz", "Freundesfunken senden und empfangen", MapPoint(.763f, .499f)),
    ARCHIVE("Briefe", "Alle Briefe und Prüfdateien", MapPoint(.451f, .817f)),
    EP("Punkte-Verwaltung", "Punkte vorschlagen und Verlauf prüfen", MapPoint(.749f, .801f)),
    SETTINGS("Gemeindehaus", "Profil, Modus, Konten, Geräte und Sicherheit", MapPoint(.254f, .129f)),
}

object VillageScenes {
    val HOME = MapPoint(.335f, .663f)
    val FRIEND = MapPoint(.748f, .675f)
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
