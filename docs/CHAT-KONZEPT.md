# Chat-Konzept: Layermaxxing als unser privater Messenger

Stand 2026-10-07 · Verantwortlich Chat: Gerfried · Insel/Grafik: Gregor
Branch: `gerfried-erweiterung`

## Ziel in einem Satz

Unsere gesamte Kommunikation (bisher WhatsApp, Telegram, Instagram-DMs) läuft über
Layermaxxing. Dafür muss der Chat **schneller und vollständiger als WhatsApp** sein.
Die Insel ist der emotionale Mehrwert obendrauf und kein Umweg zum Chat.

## 0. Grundprinzip: Der Chat bleibt Chat

Wunsch von Gerfried (2026-10-07): **Nicht alles ist Chat.** Briefe, Quests, EP und
andere Zusatzfunktionen dürfen weder den Chatverlauf noch die Freundeskarte überladen.

Heute landen im Chatverlauf zusätzlich zu den Nachrichten:
Briefzeilen (`LetterActivityRow`), das Siegel-Band (`SealBandRow`), EP-Karten
(`EpOpportunityCard`) und Anfrage-Karten (`ThreadRequestCard`).

Regeln ab jetzt:

1. **Im Verlauf stehen nur Nachrichten** (Text, Foto, Sprache, Sticker, Datei,
   später Umfrage). Keine Briefe, Quests oder EP-Karten mehr dazwischen.
2. **Zusatzfunktionen haben ihren eigenen Ort:** Briefe, Quests und EP liegen in
   der Freundeskarte und auf der Insel.
3. **Im Chat gibt es höchstens eine schmale Hinweiszeile** unter dem Kopf, und nur
   wenn etwas zu tun ist (z. B. „✉ 1 Brief bereit · ⭐ 1 Quest“). Ein Tap darauf
   öffnet die Freundeskarte an der richtigen Stelle. Gibt es nichts zu tun, ist
   die Zeile weg.
4. **Die Freundeskarte bleibt ruhig:** oben das Wichtigste (wer, Stufe,
   Hauptaktionen). Alles Weitere ist in Abschnitten zugeklappt und nur mit
   Inhalt sichtbar. Leere Abschnitte werden nicht angezeigt.
5. **Erstellen bleibt erreichbar, ohne zu stören:** „Brief“ und „Quest“ im
   „+“-Menü öffnen ein eigenes Blatt. Nach dem Senden erscheint im Chat nichts
   Zusätzliches.

## 1. Ist-Stand (geprüft im Code, 7.7.1)

Vorhanden im 1:1-Chat:

- Text, Antworten (Zitat), Kopieren, Bearbeiten, Löschen
- Fotos, aber stark verkleinert (max. 1280 px, JPEG 82, Limit ca. 2,2 MB)
- Sprachnachrichten, Sticker, Reaktionen, Anpinnen
- „Tippt…" und Online-Status
- Chat → Quest, Chat → Brief, Flaschenpost, Wörterbuch

Fehlt für einen echten Messenger:

| Lücke | Warum kritisch |
|---|---|
| **Gruppen-Chats** | Gruppen gibt es nur als Behälter für Themen und Quests, nicht als Chat. |
| **Echte Push-Benachrichtigungen** | Aktuell Abfrage alle 15 Minuten. Für einen Messenger unbrauchbar. |
| **Medien-Speicher** | Anhänge liegen als Base64 in SQLite. Für Videos, Dateien und Originalqualität ungeeignet. |
| Videos, Dateien, Umfragen, Bots | Fehlen komplett. |
| Weiterleiten, Suche, Medien-Galerie, Stumm/Archiv pro Chat | Fehlen oder sind nur teilweise vorhanden. |
| Link-Vorschau, Reels/X teilen, Teilen aus anderen Apps | Fehlen. |

Fazit: Die Chat-Oberfläche ist schon gut, aber **das Fundament (Push, Medien,
Gruppen) muss zuerst gebaut werden**. Sonst bleibt alles andere Kosmetik.

## 2. Design-Konzept: „Ein Freund, ein Ort"

Die Freundesansicht der Insel gefällt sehr gut. Sie soll ins Chatten integriert
werden, ohne den Weg zum Chat zu verlängern.

### Grundregeln für wenige Klicks

1. **Die App startet in der Chatliste.** Ein Tap öffnet den Chat. Mehr Schritte gibt es nie.
2. **Die Freundesansicht liegt im Chat, nicht davor.** Ein Tap auf den Chat-Kopf öffnet sie.
3. **Alles Seltene liegt hinter genau einem „+".** Die Eingabeleiste bleibt ruhig.
4. **Gesten statt Menüs:** Wischen zum Antworten, Gedrückthalten für Aktionen.
5. **Von außen teilen in zwei Taps:** Android „Teilen" → Freund oder Gruppe → gesendet.

### 2.1 Chatliste (Start)

```
┌──────────────────────────────────┐
│ Chats                    🔍  ✏️  │
│ [Alle] [Ungelesen] [Gruppen] [📌]│
├──────────────────────────────────┤
│ 🦊 Gerfried        12:41    (2)  │
│    Passt, dann Samstag 👍        │
│ 👥 Wanderrunde     11:02    (5)  │
│    Anna: 📊 Umfrage: Samstag?    │
│ 🐻 Anna            gestern  ✉️    │
│    Brief für dich · bereit       │
└──────────────────────────────────┘
  [💬 Chats]  [🏝 Insel]  [☰ Mehr]
```

- Tap öffnet den Chat sofort.
- **Langes Drücken** zeigt eine **Vorschau der Freundeskarte**. Loslassen schließt sie wieder.
- Nach links wischen: Stumm / Archiv. Nach rechts wischen: als gelesen markieren.
- Kleine Insel-Symbole (Brief bereit, offene Quest) stehen als Zeichen in der Zeile, ohne eigenen Tap.

### 2.2 Chat mit integrierter Freundeskarte

```
┌──────────────────────────────────┐
│ ←  🦊 Gerfried   🏝 Stufe 3 ▾  📞 │  ← Tap auf Kopf = Karte
│    online · 1 Quest offen        │
├──────────────────────────────────┤
│        Nachrichten …             │
├──────────────────────────────────┤
│ [+]  Nachricht …        😊  🎤   │
└──────────────────────────────────┘
```

Ein Tap auf den Kopf oder Wischen nach unten öffnet die **Freundeskarte** als Blatt.
Sie ist identisch mit der Inselansicht, damit es nur **eine** Freundesansicht gibt:

```
┌──────────────────────────────────┐
│ 🦊 Gerfried · Vertraut · Stufe 3 │  [Mini-Insel]
│ ███████░░░  noch 40 Punkte       │
│ [🏝 Insel besuchen]  [✉ Brief]   │
│ ── Quests ──  + Neue             │
│ ☐ Brennerhaus Samstag            │
│ ── Briefe ──  Empfangen|Gesendet │
│ ── Medien · Dateien · Links ──   │
│ [▣][▣][▣][▣]  Alle ansehen →     │
│ ── Einstellungen ──              │
│ 🔕 Stumm · 📌 Angepinnt · 🔍 Suche │
└──────────────────────────────────┘
```

- **Technisch:** `FriendSheet` wird aus `IslandWorld.kt` in eine eigene Datei
  `FriendCard.kt` ausgelagert. Insel und Chat nutzen dieselbe Komponente. Das
  vermeidet Merge-Konflikte mit Gregor: Er gestaltet die Karte, ich hänge die
  Chat-Bereiche (Medien, Einstellungen) an.
- Von der Insel aus wird **„Chat" zum Hauptknopf** (groß). „Brief" ist zweitrangig.

### 2.3 Eingabeleiste und „+"-Blatt

```
[+]  Nachricht …                 😊  🎤
 │
 └─►  📷 Kamera     🖼 Galerie     📄 Datei
      📊 Umfrage    ✉ Brief        ⭐ Quest
      📍 Ort*       👤 Kontakt*    🤖 Bot-Befehl
```
`*` später

- Fotos und Videos aus der Galerie mit Schalter **„HD / Original"**.
- Mehrfachauswahl mit Bildunterschrift vor dem Senden.
- Mikrofon gedrückt halten = Sprachnachricht, nach oben wischen = einrasten.
- Tippt man `/`, erscheinen Bot-Befehle.

### 2.4 Nachricht-Aktionen

- **Doppeltipp** = ❤️-Reaktion. **Nach rechts wischen** = Antworten.
- **Gedrückt halten** = Reaktionsleiste plus Aktionen: Antworten, Weiterleiten,
  Kopieren, Bearbeiten, Anpinnen, → Quest, → Brief, Löschen.

## 3. Funktionskonzept in Phasen

Jede Phase ist für sich nutzbar und wird als APK ausgeliefert.

### Phase 0: Fundament (ohne das bleibt es ein Spielzeug)

1. **Push-Benachrichtigungen in Echtzeit.** Empfehlung: Firebase (FCM) nur als
   inhaltsloser „Weckruf". Die App holt die Nachricht dann selbst vom Server.
   Google sieht nur, dass etwas kam, nicht was. Alternative: selbst gehostetes
   ntfy/UnifiedPush (privater, aber auf manchen Handys unzuverlässig).
2. **Medien-Speicher auf der Festplatte** statt Base64 in SQLite, mit Upload in
   Teilen, Fortsetzen nach Abbruch und Vorschaubildern. Voraussetzung für Videos,
   Dateien und Originalqualität.
3. **Live-Verbindung (WebSocket)**, solange die App offen ist: Nachrichten,
   Tippt-Anzeige und Lesestatus sofort statt per Abfrage.
4. **Gruppen-Chats** auf Basis der vorhandenen Gruppen: Nachrichten, Lesestatus
   pro Mitglied, Admin, Hinzufügen und Entfernen.

### Phase 1: WhatsApp-Gleichstand

- Bilder in **Originalqualität** (optional), Videos, Dateien/Dokumente
- Weiterleiten, @Erwähnungen in Gruppen
- **Medien-Galerie** pro Chat (Fotos, Videos, Dateien, Links)
- Suche in Chats und über alle Chats
- Chats stumm schalten, archivieren, anpinnen
- Teilen aus anderen Apps (Android-„Teilen" → Layermaxxing)
- Einladungslink für Freunde mit einfacher Registrierung

### Phase 2: Telegram-Extras

- **Umfragen** in Gruppen und 1:1: Einfach- oder Mehrfachauswahl, anonym oder
  offen, Ergebnis live. Passt zu den Quests: „Umfrage → Quest daraus machen".
- **Bots:** Bots sind eigene Konten auf unserem Server mit `/`-Befehlen und
  Knöpfen unter Nachrichten. Erste Bots, die wirklich nützen:
  - Erinnerungs-Bot (`/erinnere morgen 18:00 Grillen`)
  - Quest-Bot (fasst offene Quests der Gruppe zusammen)
  - Wörterbuch-Bot (`/was ist …`)
  - später ein KI-Bot über die vorhandene Agenten-Infrastruktur
- Geplante Nachrichten, Nachrichten mit Ablaufzeit (die Briefe können das schon)

### Phase 3: Inhalte aus Instagram, X und Co.

- **Link-Vorschau:** Der Server lädt Titel und Vorschaubild (Open Graph).
- **Instagram-Reels und X-Posts als Karte** mit Vorschaubild und Absender. Ein
  Tap öffnet direkt die Instagram- oder X-App. Der Rückweg funktioniert über
  „Teilen" aus Instagram in Layermaxxing.
- Eingebettete Player später, falls die Plattformen das erlauben.

### Phase 4: Kompletter Umzug

- Desktop- und Web-Zugang (zweites Gerät)
- Backup und Export der eigenen Chats
- Stabiler Produktionsserver mit täglichem Backup (aktuell nur Testserver)

## 4. Entscheidungen, die wir treffen müssen

1. **Push:** FCM als Weckruf (Empfehlung) oder selbst gehostetes ntfy?
2. **Verschlüsselung:** Aktuell hält der Server die Schlüssel. Das ist ehrlich
   dokumentiert, aber keine Ende-zu-Ende-Verschlüsselung. Für „alles läuft hier"
   ist echte E2E langfristig wichtig, aber ein eigenes, großes Projekt. Vorschlag:
   erst Funktionen, E2E danach als eigene Phase.
3. **Start-Tab:** Chatliste als Start (Vorschlag) und Insel als zweiter Tab.
   Das muss mit Gregor abgestimmt werden.
4. **Server:** Wann bekommt der echte Betrieb einen eigenen Produktionsserver?

## 5. Umsetzung: sofort und später

### Sofort (ohne Server-Umbau, je eine APK)

1. **Chat entrümpeln:** Briefe, Siegel-Band und EP-Karten raus aus dem Verlauf.
   Stattdessen die schmale Hinweiszeile (nur bei Bedarf).
2. **Freundeskarte im Chat:** Tap auf den Kopf öffnet die Karte. Sie wird
   gemeinsam mit der Insel genutzt (`FriendCard.kt`) und ist aufgeräumt nach Regel 4.
3. **„+“-Menü:** Kamera, Galerie, Sticker, Brief, Quest. Brief und Quest öffnen
   eigene Blätter.

### Später (braucht Server-Umbau oder Entscheidung)

- Gruppen-Chat
- Push-Benachrichtigungen in Echtzeit (Entscheidung 4.1)
- Medien-Speicher, Fotos in Originalqualität, Videos, Dateien
- Umfragen, Bots, Weiterleiten, Suche, Medien-Galerie
- Link-Vorschau, Reels und X-Posts
- Ende-zu-Ende-Verschlüsselung, Produktionsserver
