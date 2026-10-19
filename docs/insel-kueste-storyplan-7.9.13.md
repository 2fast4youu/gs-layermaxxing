# Insel, Küste und Stufengeschichten – Plan 7.9.13 (versionCode 73, `7.9.13-kueste`)

Stand: 2026-10-19. **Planungsdokument.** Umgesetzt sind in 7.9.13 der Ladefix (Abschnitt 1) sowie die Phasen 3.1
(Grundflächen, grid_version 6), 3.2 (Strand/Zonen, Sternweg entfernt) und 3.4 (geprüfte Schiffswege), getestet in
`server/tests/test_v17_ground.py`, `IslandGroundTest` und `IslandMapRouteUiTest`. Die Stufengeschichten (Abschnitt 2 / 3.3)
sind weiterhin nur Design-Logik und nicht umgesetzt.

## 1. Umgesetzt in dieser Phase: Laden der eigenen Insel

- **Ursache des Hängers „Insel wird geladen…“:** (a) `ownId` kam nur aus `api.status`. Dieser Aufruf lief in einem
  Alles-oder-nichts-Refresh zusammen mit 17 anderen Anfragen. Scheiterte eine davon, wurden die übrigen abgebrochen,
  `status` blieb `null` und es wurde nie eine Insel angefragt. (b) Nach einem Fehler oder einer `null`-Antwort folgte
  eine 60-s-Pause. Es gab weder Timeout noch Fehlerzustand noch einen Retry-Knopf. (c) Zwischen Navigation und Rotation
  blieb nichts erhalten.
- **Korrektur:** Der Status wird getrennt abgefragt. Die zuletzt bestätigte User-ID wird pro Serverprofil und Konto
  gespeichert und wird beim Login durch die echte ID ersetzt. `status` wird bei einem Kontowechsel zurückgesetzt.
  `IslandSync` begrenzt jeden Ladeversuch auf 15 s. Wiederholt wird nach 2/5/10/20 s, danach im normalen
  60-s-Rhythmus. Es läuft immer nur ein Loader. `CancellationException` wird weitergereicht. Ein Retry ist per Knopf
  möglich und erfolgt automatisch, wenn die App nach einem Fehler wieder in den Vordergrund kommt.
- **Cache:** `IslandCache` liegt nur im Speicher und enthält nur den Inselzustand. Der Schlüssel besteht aus
  Profil, Basis-URL, Konto und User-ID. Logout oder das Entfernen eines Kontos löscht den Eintrag. Ein Stand aus dem
  Cache wird sofort angezeigt, dazu ein kleiner Hinweis „Aktualisiert…“ bzw. „Letzter Stand · nicht aktuell“.
  Schreibaktionen sind gesperrt, bis in dieser Sitzung eine frische Antwort vorliegt.
- **Ohne Stand:** Angezeigt wird „Noch kein Stand geladen“ mit Fehlertext und „Erneut versuchen“. Es gibt keine
  erfundene Insel.
- **Karte:** Die Routengeometrie wird nur neu berechnet, wenn sich Inseln oder Liegeplätze ändern, nicht beim
  Minutentakt oder beim Refresh.

## 2. Stufengeschichten (Design-Logik, nicht umgesetzt)

Pro Eintrag: 1) Geschichtskern · 2) Szene/Gegenstand · 3) tatsächlich bestehende Mechanik · 4) Bezug/Anker zur
vorigen Stufe. Die Geschichte soll über anklickbare Gegenstände und den Bildaufbau erlebbar werden, nicht über Textkarten.
Es werden keine Daten oder Status erfunden.

| # | Stufe | 1) Geschichtskern | 2) Szene / Gegenstand | 3) Bestehende Mechanik | 4) Bezug zur Vorstufe / Anker |
|---|---|---|---|---|---|
| 1 | HOUSE 0 Schlafplatz | Gestrandet: Du hast nur einen Ort zum Ausruhen. | Hängematte zwischen zwei Pfosten, Namensbrett am Pfosten | Profil & Einstellungen (Name, Emoji, Farbe), „Insel bearbeiten“ | Startpunkt; der linke Pfosten ist der Anker für alle späteren Stufen |
| 2 | HOUSE 1 Holzhütte | Die erste Nacht unter einem Dach. | Blockhütte; Namensbrett über der Tür, Spiegel innen = Profil | Profil & Einstellungen, Zählwerte | Der Pfosten wird zum Türbalken, die Hängematte hängt drinnen |
| 3 | HOUSE 2 Häuschen | Man richtet sich ein und zeigt, was man gesammelt hat. | Wohnwinkel `bg_house`: Fensterbank mit Schätzen, Sessel | Profil/Einstellungen, „Insel bearbeiten“, vier Zählwerte | Die Hängematte als Sessel-Decke; das Namensbrett jetzt als Türschild |
| 4 | HOUSE 3 Landhaus | Ein Zuhause, das Gäste sehen. | Steinhaus mit Turm; die Fahne trägt die eigene Profilfarbe | wie Stufe 2; Fahne antippen öffnet die Farbauswahl (bestehende Profilfarbe) | Die Fahne setzt Namensbrett und Türschild fort |
| 5 | POST 0 Briefbaum | Nachrichten werden an den Stamm geheftet. | Baum, an dem die echten Briefe als Zettel hängen | Briefe lesen, Brief schreiben, gesperrte Briefe (Nebel/Ton) | Bestehende Basis: echte Briefe am Baum |
| 6 | POST 1 Postkasten | Die Briefe bekommen ein trockenes Zuhause. | Holzkasten mit Dach am selben Baum; die Klappe öffnet ungelesene Briefe | Briefe lesen/schreiben, ungelesen-Badge | Der Baum bleibt sichtbar, der Kasten hängt daran |
| 7 | POST 2 Postfächer | Jeder Freund hat sein eigenes Fach. | Poststube, Fächer pro Freund mit echten Briefen | Briefe pro Freund, Brief schreiben pro Freund | Der Kasten wird zum ersten Fach in der Wand |
| 8 | POST 3 Postamt | Die Post der Insel läuft über einen Schalter. | Schalter mit Siegel; Stempel antippen = Brief verfassen | Brief schreiben, gesperrte Briefe, Nachweisdetails | Fächer hinter dem Schalter; das Siegel-Icon setzt die Fahne fort |
| 9 | LIGHTHOUSE 0 Aussichtspalme | Von der Palme aus Ausschau nach anderen halten. | Palme mit Fernrohr; offene Anfragen sind Segel am Horizont | Freundesanfragen annehmen/ablehnen, Freunde suchen | Startpunkt: das Fernrohr ist der Anker |
| 10 | LIGHTHOUSE 1 Ausguck | Ein fester Platz zum Ausschauhalten. | Hölzerner Ausguck; das Fernrohr ist auf dem Geländer montiert | Anfragen, Leute finden/hinzufügen | Das Fernrohr wandert von der Palme auf den Turm |
| 11 | LIGHTHOUSE 2 Leuchtfeuer | Ein Licht, damit Freunde den Weg finden. | Feuerkorb auf Felsen; der Lichtkegel zeigt auf eingehende Anfragen | Anfragen, Freundschaftseinstellungen (bilateral) | Das Fernrohr steht neben dem Feuer |
| 12 | LIGHTHOUSE 3 Leuchtturm | Das Licht verbindet die Insel mit allen Freunden. | Turm mit drehendem Strahl; jeder Freund ist ein Schiff im Strahl | Freundesliste, Anfragen, Freundschaftseinstellungen | Der Feuerkorb ist jetzt die Laterne oben |
| 13 | LIBRARY 0 Wörter am Treibholz | Angeschwemmte Kisten, in einer liegt ein Buch. | Treibholzbrett mit offenem Buch | Wörterbuch lesen, suchen, Wort hinzufügen | Startpunkt: das Buch ist der Anker |
| 14 | LIBRARY 1 Lesedach | Ein Dach schützt die Wörter vor Regen. | Zweigdach über dem Regal; offenes Buch | Suche, Kategorien, Wort hinzufügen | Dasselbe Buch liegt jetzt unter dem Dach |
| 15 | LIBRARY 2 Bücherstube | Aus losen Wörtern wird eine Sammlung. | Stube mit Wandbord; Lesepult mit dem Wörterbuch | Kategorien als Bücher am Bord, Lesen, Hinzufügen | Die Treibholzkiste wird zum Fuß des Lesepults |
| 16 | LIBRARY 3 Bibliothek | Die Sprache der Insel ist bewahrt. | Steinbibliothek; Ledereinband; Feder für „Wort hinzufügen“ | wie Stufe 2, unverändert | Das Lesepult steht nun im Turmzimmer |
| 17 | CAMPFIRE 0 Feuerkreis | Wer sich trifft, sitzt ums Feuer. | Steinkreis mit Feuer; Mitglieder der echten Gruppe sitzen darum | Gruppen ansehen/blättern, Gruppen-Quest | Bestehende Basis: echte Gruppen am Feuer |
| 18 | CAMPFIRE 1 Treffhütte | Die Runde zieht unter ein Dach. | Hütte `fief_build_closeup`; Kerzentisch `p_table`, Aushänge | Tisch antippen = Gruppen-Quest; Gruppen blättern | Die Kerze auf dem Tisch ist das Feuer von Stufe 0 |
| 19 | CAMPFIRE 2 Gemeindehaus | Mehrere Gruppen haben einen gemeinsamen Raum. | Saal mit Tischen; jede Gruppe ein Tisch | Gruppen, Gruppen-Quests | Der Kerzentisch bleibt der Haupttisch |
| 20 | CAMPFIRE 3 Festhalle | Gemeinsam Erreichtes wird gefeiert. | Saal mit Festfahne; erledigte Gruppen-Quests als Wimpel | Gruppen-Quests (erledigt/offen) | Fahne und Wimpel über dem Haupttisch |
| 21 | HARBOUR 0 Strandtreff | Ein Boot am Strand, bereit für die erste Aufgabe. | Ruderboot mit Wimpel **auf Sand** (Strandzone, Abschnitt 3) | Quests anlegen/erledigen/löschen, Themen | Startpunkt: der Wimpel ist der Anker |
| 22 | HARBOUR 1 Bootssteg | Vom Strand hinaus aufs Wasser. | Treibholzsteg vom Strand ins Wasser; Boot daran | Quests, Themen | Der Wimpel steckt am Stegende |
| 23 | HARBOUR 2 Anleger | Mehrere Boote können festmachen. | Anleger mit Pollern; jeder Poller = offene Quest | Quests (offen-Badge), Themen | Der Steg wird zum Anleger verbreitert |
| 24 | HARBOUR 3 Hafen | Briefe und Aufgaben laufen hier ein und aus. | Hafen mit Laterne; Kompass öffnet die Themen | Quests, Themen, Liegeplatz der Briefboote | Der Wimpel weht jetzt an der Laternenstange |

## 3. Nächste Phasen (nicht umgesetzt)

1. **Grundflächen (grid_version 6):** Kollision nur über die rechteckige Bodenfläche wie bei Clash of Clans.
   Dächer, Kronen und Türme dürfen sich überdecken. Die Tiefensortierung erfolgt nach Bodenlinie. v5
   (Vollsilhouette) bleibt versioniert erhalten. Server und App müssen dieselbe Regel nutzen, Altlayouts werden nie
   automatisch verschoben.
2. **Küste/Strand:** Echte Strandzone, deren Darstellung und Validierung übereinstimmen. HARBOUR 0 nur auf Strand,
   ab Stufe 1 Steg/Anleger nachvollziehbar am Wasser. Normale Gebäude dürfen nicht auf Strand stehen. Beim Verschieben
   werden nur passende Zielfelder gezeigt. Felsen im Wasser blockieren Routen. Das weiße sternförmige Weg-/Plaza-Muster
   wird entfernt, Wege werden aus den aktuellen Gebäuden und Zonen abgeleitet.
3. **Stufengeschichten:** Abschnitt 2 wird über anklickbare Gegenstände in den bestehenden Szenen umgesetzt, nicht
   über Textkarten.
4. **Schiffswege:** Sanfte, validierte Kurven mit Abstand zu Wasser und Felsen, Ankunft am aktuellen Hafen.
   Zeichnung und Boot folgen derselben Kurve, die Glättung darf nie durch Land führen. Keine dicken
   Fortschrittsbalken.
