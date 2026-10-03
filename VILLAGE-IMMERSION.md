# Dorf-Fenster und Aktivitätsanzeigen – 5.2-village-preview2

Entwicklungsstand im Branch `gregor-erweiterung`; keine Änderungen am Produktionsserver.

## Umgesetzt

- Die Dorfkarte bleibt beim Öffnen ihrer Funktionsfenster im selben Compose-Zweig aktiv. Der Hintergrund ist die tatsächliche Karte, nicht ein Ersatz-Screenshot; Kameraposition und Szenenzustand werden nicht durch den Menüwechsel neu aufgebaut.
- Gespräche, Themen, Gruppen, Wörterbuch, Archiv, EP, Personen und Einstellungen öffnen innerhalb eines gemeinsamen pergamentfarbenen Fensters mit braunem Rahmen und Rückkehr ins Dorf.
- Die Dorf-Farbpalette gilt auch für untergeordnete Material-Dialoge, Sheets, Eingaben und Schaltflächen. Normale App-Navigationsleisten werden in diesem Kontext verborgen. Der reine Messenger-Modus bleibt unverändert.
- Freund-/Chat-Aufrufe aus der Welt wechseln nicht mehr ungewollt zum Messenger-Tab. Die Welt bleibt auch im kombinierten Modus der Navigationskontext.
- Eine hinter dem Fenster liegende Eingabe-Sperrfläche verhindert versehentliche Kartenaktionen durch Fensterränder.
- Gebäude tragen grafische Statusmarken mit echten Daten: neue Chats, offene Themen, Gruppenbestand, ungeöffnete Funken, bereitliegende bzw. zustimmungspflichtige Briefe, EP-, Freundschafts- und Regelvorschläge. Ereignisse am selben Gebäude werden zusammengefasst, damit Marken nicht übereinanderliegen. Nullbestände erzeugen keine Aktivität.
- Die Statusmarken pulsieren; die Einstellung für reduzierte Bewegung schaltet diese Animation ab. Die Anzeigen funktionieren auch ohne Freundschaft.
- Bestehende Briefzustände werden als Boten auf einer Dorfroute angezeigt. Ihre Position nutzt den vorhandenen Brief-Zeit-/Freigabestatus; dies ist keine reale GPS-Route und keine neue Backend-Zustellgarantie.

## Verifikation und Grenzen

Clean Android-Test-/Lint-/APK-Build erfolgreich; 114 Unit-Tests ohne Fehler, keine Lint-Fehler. Die unveränderten Backend-Tests liefen ebenfalls erfolgreich (57 Tests).

Eine visuelle Geräteabnahme ist weiterhin nicht erfolgt. Die Statusmarken verwenden aktuell Symbolglyphen statt eigens gezeichneter Spielfiguren. Die gemeinsame Fensteroptik ersetzt nicht alle Einzelkomponenten durch individuell illustrierte Gebäude-Innenräume. Es gibt noch keine vollständige grafische Ereignischronik oder eigene Aktionsanimation für jede Nachricht, Themenänderung und Bestätigung. Die statische Dorfplatte zeigt weiterhin keine individuellen Gebäude-Ausbaustufen. Deshalb ist dies eine überprüfte technische Preview, keine Behauptung einer vollständig animierten Spielwelt.
