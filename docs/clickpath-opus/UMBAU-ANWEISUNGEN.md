# Umbau-Anweisungen (Basis: Klickpfad-Analyse 7.7.1)

Ziele: (A) außerhalb der Insel neutrale, gut funktionierende Screens für ALLE Funktionen · (B) wichtige Funktionen schnell erreichbar vom Hauptmenü · (C) aus dem Inselmodus schnell zurück zum Hauptmenü.
Zwei Looks: **Insel-Look** (nur in der Inselwelt) · **Neutral-Look** = Stil der Chatliste (Creme + gedämpftes Teal). Kein Lavendel-Material mehr.

## C – Schnell raus aus der Insel (zuerst)
1. Insel-Leiste um **„Chats“** (mit Ungelesen-Badge) erweitern: Karte · Meine Insel · Hafen · Chats.
2. Zurück-Taste auf der Inselkarte → direkt **Chats**, nicht über Zwischenschritte.
3. In jedem Inselort: Zurück → Karte (1 Tap), nie tiefer verschachtelt.
4. Aus „Mein Haus → Einstellungen“: Zurück führt zur Insel, „← Inseln“-Knopf entfällt (ersetzt durch Leiste).
5. Lange auf „Karte“ drücken → Hauptmenü (Chats) als Abkürzung.

## B – Wichtige Funktionen präsenter
6. Chatliste: **Gruppen- und Themen-Chips immer zeigen** (auch bei 0, dann „+ Gruppe“/„+ Thema“).
7. Chatliste: totes `onGlossary`/`onValley` anschließen → Wörterbuch-Chip + Inseln-Einstieg.
8. „Mehr“: Briefe, Punkte, Wörterbuch **auch im Inselmodus** zeigen (nicht ausblenden).
9. Freundschaftsregeln direkt ins Chat-Menü ⋮ (1 Tap statt 3).
10. Insel-Karte: Punkte- und Brief-Pille **immer** zeigen (bei 0 gedämpft), nicht nur bei >0.
11. **Ein** Quest-Formular für alle Wege (Chat, Freund, Hafen, Gruppe) – `ChatQuestDialog` + `NewQuestSheet` zusammenlegen.
12. „Gruppe erstellen“ nur noch einmal (Gruppen-Screen), Dublette in Leute (`LayerHome.kt:1287`) entfernen.

## A – Neutrale Screens für alle Funktionen (außerhalb Insel)
13. **Neuer neutraler Freund-Screen** (aus Chat-Kopf antippen): Spitzname, Quests, Briefe, Themen, Regeln – ersetzt außerhalb der Insel das Freundes-Sheet.
14. **Spitzname** im Messenger-Modus setzbar (über 13).
15. **Quest anlegen/abhaken** im Messenger-Modus: Quest-Liste im Freund-Screen (13) + Gruppen-Screen.
16. Status „Ich bin hier“, Bauen, Deko bleiben **nur Insel** (rein Insel-Feature, kein neutraler Screen nötig).

## Screens auf Neutral-Look umstellen (Lavendel/Material → Chatlisten-Stil)
17. `14-more` Mehr-Hub
18. `37-ep` Punkte
19. `38-letter-room` Briefe mit Freund (dunkelbraun → hell)
20. `36-topics` Themen-Liste
21. `40-thread-info` Regeln & Info
22. `09-groups` Gruppen · `08-topics-room` Themen-Übersicht · `03-room`
23. `39-spark-room` Funken (blauer Knopf → Teal)
24. `13-login` Formular
25. Alle Dialoge: `41` Quest · `42` Begriff · `45` Spitzname · `46` Code · `47` Nachweis · `48` Punkte vorschlagen · `49` Anfrage → ein gemeinsamer Dialog-Stil
26. `06-thread` Kopfleiste: dunkelblau → Teal

## Screens im Insel-Look nachziehen (nur in der Inselwelt sichtbar)
27. `26-ort`, `25-bauen`, `24-deko-wahl`: weißer Hintergrund → Hafenpapier + Holzrahmen.
28. Wenn Mehr/Punkte/Briefe/Gruppen **aus der Insel** geöffnet werden: im Holzrahmen des Ortes zeigen (wie Post/Hafen), sonst Neutral-Look.

## Aufräumen
29. `BuildingCard` (`04-card`, alter Alpenstil) löschen – im App-Code unbenutzt.
30. Test-Render für `NewQuestSheet` reparieren (fehlt im Atlas).

## Abnahme
31. Klickpfade + Graph + Tabelle neu erzeugen (`build_clickpaths.py`, `build_overview.py`, `build_navgraph.py`).
32. Ziel: jede Funktion ≤ 2 Taps in **beiden** Modi, kein ✗ im Messenger-Modus außer Nr. 16, kein roter/oranger Rahmen mehr.
