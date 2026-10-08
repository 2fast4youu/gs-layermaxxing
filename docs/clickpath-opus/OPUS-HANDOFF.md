# OPUS-HANDOFF — GS Layermaxxing Clickpath Redesign

Du arbeitest **nicht** am Produktcode. Nutze ausschließlich dieses Source-Material als Ist-Zustand: `index.html`, `screens.json`, `edges.json` und `rendered/*.png`.

## Ziel

Entwirf später eine task-orientierte Clickpath-Dokumentation als **Filmstrips**. Zeige nicht alle Screens in einem spaghettiartigen Gesamtgraphen. Gruppiere stattdessen nach Aufgaben, zum Beispiel:

1. **Mit Freunden chatten**: Chats → Thread → Thread-Menü/Extras → Brief oder Quest.
2. **Freundschaft organisieren**: Chats/Mehr → Personen → Gruppe/Themen.
3. **Insel erkunden und handeln**: Inselkarte → eigene Insel → Ort → Post/Hafen/Leuchtturm/Bibliothek/Gemeindehaus/Haus.
4. **Insel gestalten**: eigene Insel → Bauplatz → Ort-Sheet → Deko-Auswahl.
5. **Einstieg und Konto**: Login → Mehr → Einstellungen/Sicherheit.

## Darstellungsregeln

- Jeder Filmstrip ist eine lineare Aufgabe mit Start, nummerierten Taps und Ergebnis.
- Verwende pro Schritt den passenden Screenshot aus `rendered/`; `screens.json` enthält 43 gerenderte Einträge und 1 bewusst fehlende Inventarfläche. `NewQuestSheet` ist nicht als Bild vorhanden. Behaupte keine nicht getesteten Produktionszustände.
- Zeichne pro Tap einen gut sichtbaren nummerierten Marker direkt an die Zielregion. Ergänze, wenn vorhanden, Semantik/Content-Description und ungefähre Bounds; trenne Laufzeitbeobachtung von Source-Evidence.
- Zeige höchstens wenige Alternativen neben dem Hauptpfad. Kein großes Kreuzungsdiagramm, keine ungerichteten Linien, kein spaghettiartiger Graph.
- Nutze die deutsche UI-Beschriftung aus den Screens, aber behalte technische IDs in kleinen Quellenhinweisen.
- Kennzeichne alle Pfade als **rekonstruiert aus aktuellem Source**, weil der alte Menüplan nicht verfügbar war.
- `ProofDialog` ist nur als Legacy-Fixture gerendert; ein kryptographisch validierter Proof-Zustand bleibt eine explizite Residual-Limitation.
- Synthetic fixtures only: keine echten Profile, Tokens, Serverantworten oder privaten Daten ergänzen.

## Prüfpunkte

- Vor der Präsentation: stimmen die 43 Dateinamen mit `pixel-evidence.json` überein? Sind die unterschiedlichen Dialogfenstergrößen und die eine Missing-Fläche sichtbar? Sind die ähnlichen Nummern als vollständige, kollisionsfreie Namen geprüft? Stimmen die 25 Kanten mit `edges.json` und den Quellenreferenzen überein? Ist die Legacy-Proof-Limitation sichtbar?
