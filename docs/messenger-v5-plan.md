# Messenger v5 — Zwischenrelease-Plan

Stand: 2026-09-16 · Branch `gerfried` · baut auf `docs/gerfried-v4-spec.md` auf.

Dieses Dokument beschreibt den Umbau von GS Layermaxxing von einer Funktionssammlung mit
sieben Reitern zu einem normalen Messenger mit drei Bereichen. Der V4-Produktvertrag bleibt
vollständig gültig: Alle dort zugesagten Fähigkeiten existieren weiter, sie werden nur anders
erreicht. Das Empire-/Spielkonzept am Ende ist ausdrücklich **Zukunft ohne Implementierung**.

---

## 1. Ausgangslage

| Bereich | Ist |
| --- | --- |
| Navigation | 7 Tabs: Briefe, Chat, Neu, EP, Themen, Leute, Mehr |
| Normale Nachricht | eigener Tab `Chat`, eigene Liste, eigener Thread |
| Versiegelter Brief | eigener Tab `Briefe` mit Empfangen/Gesendet-Umschalter |
| Brief schreiben | eigener Tab `Neu` |
| EP | eigener Tab; eingehende Vorschläge nur dort sichtbar |
| Freundschaftsregeln | aufklappbarer Block in der Freundeskarte im Tab `Leute` |
| Themen | eigener Tab |
| Einstellungen | Tab `Mehr` mit einem Schalter „Passwort, Geräte und Blockierungen" |

Zwei Personen, die miteinander schreiben, verteilen sich damit auf mindestens vier Reiter.
Ein eingehender EP-Vorschlag und ein eingehender Einstellungsvorschlag sind nur sichtbar,
wenn man aktiv in den richtigen Reiter navigiert.

Serverseitig ist die Lage deutlich besser als die Oberfläche: Briefe und Sofortnachrichten
liegen in derselben Tabelle `messages`, unterschieden durch `message_class`
(`server/app/main.py:143`). Die bilateralen Gates sind vorhanden und werden durchgesetzt
(`require_chat_enabled`, `require_letters_enabled`, `letter_pair_allowed`,
`server/app/main.py:1311-1332`). EP kann nur nach beidseitig angenommener Einstellung
vorgeschlagen und nur von der begünstigten Person angenommen werden
(`server/app/main.py:1024-1070`).

**Folgerung:** Das Problem ist fast vollständig ein Oberflächen- und Navigationsproblem.
Der Umbau kommt mit einer einzigen, rein additiven Servererweiterung aus (Abschnitt 6).

---

## 2. Gewählte Informationsarchitektur: drei Bereiche

```
┌──────────────────────────────────────────────┐
│  [Kontowechsel]   GS Layermaxxing   [⟳]      │
├──────────────────────────────────────────────┤
│                                              │
│              Bereichsinhalt                  │
│                                              │
├──────────────────────────────────────────────┤
│   💬 Chats      👥 Leute       ⚙ Mehr        │
└──────────────────────────────────────────────┘
```

### Tab 1 — Chats (Start)

Die Gesprächsübersicht. **Eine Zeile pro Person**, nicht eine Zeile pro Nachrichtentyp.
Jede Zeile führt in den gemeinsamen Thread, der normale Nachrichten und versiegelte Briefe
chronologisch mischt.

### Tab 2 — Leute

Freunde finden, anfragen, bestätigen, verwalten. Gruppen. Von hier springt man in Threads.

### Tab 3 — Mehr

Einstellungen und Archive. Häufig Gebrauchtes zuerst, Technisches gruppiert und eingeklappt.

### Begründung

- **Drei statt vier.** Ein vierter Reiter wäre nur für EP oder Themen entstanden. Beides sind
  keine eigenständigen Kommunikationskanäle, sondern Begleitfunktionen zu einer Freundschaft.
  Sie gehören kontextuell in den Thread und archivisch nach `Mehr`.
- **Kein eigener Reiter für „Neu".** Ein Messenger schreibt aus dem Thread heraus. Der
  Briefkomponist wird ein Blatt über dem Thread mit bereits gesetzter Empfängerin oder
  Empfänger; der Mehrfach-/Gruppenversand bleibt über die Empfängerauswahl im Blatt erhalten.
- **Kein eigener Reiter für „Briefe".** Ein Brief ist eine Nachricht an eine bestimmte Person.
  Er gehört in deren Thread. Das global sortierte Briefarchiv verschwindet nicht, es wandert
  als „Briefe & Prüfdateien" nach `Mehr` — dort, wo man es für den Nachweis-Export braucht.
- **Anfragen werden zugestellt, nicht abgelegt.** EP-Vorschläge und Einstellungsvorschläge
  sind bilaterale Anfragen. Sie erscheinen als Dialog und als Karte, nicht als Unterseite.

---

## 3. Der gemeinsame Thread

Der Kern des Umbaus. Ein Thread pro Person enthält chronologisch:

| Eintrag | Herkunft | Darstellung |
| --- | --- | --- |
| Normale Nachricht | `message_class='instant'` | gewöhnliche Sprechblase, links/rechts |
| Versiegelter Brief | `message_class='letter'` | abgesetzte Briefkarte mit Siegelrand |
| EP-Gelegenheit | abgeleitet, siehe 4.5 | kontextuelle Karte unter dem Brief |

Briefe sind damit ein **Nachrichtentyp im selben Gespräch**, keine getrennte App-Welt.
Sie sehen anders aus (Siegel, Wachsfarbe, Umschlagtext), stehen aber in derselben Zeitachse.

### 3.1 Briefkarte im Thread — Zustände

| Zustand | Bedingung | Anzeige |
| --- | --- | --- |
| `LOCKED_TIMED` | `!unlocked`, `mode=timed` | 🔒 „Öffnet in 2 Std 14 Min" + absoluter Zeitpunkt |
| `LOCKED_RANDOM` | `!unlocked`, `mode=random` | 🔒 „Öffnet zufällig bis TT.MM., HH:MM" |
| `LOCKED_MANUAL` | `!unlocked`, `mode=manual` | eingehend: „Wartet auf Freigabe durch X"; ausgehend: Knopf „Jetzt freigeben" |
| `LOCKED_MUTUAL_WAITING_ME` | `!unlocked`, `mode=mutual`, eigene Zustimmung fehlt | Knopf „Meine Freigabe bestätigen" |
| `LOCKED_MUTUAL_WAITING_PEER` | `!unlocked`, `mode=mutual`, eigene Zustimmung liegt vor | „Wartet auf Zustimmung von X" |
| `LOCKED_PRESENCE` | `!unlocked`, `mode=presence` | 🔒 „Öffnet, sobald ihr beide online seid" |
| `READY` | `unlocked`, eingehend, noch nicht geöffnet | ✦ „Bereit zum Öffnen" + Knopf |
| `OPENED` | lokal entschlüsselt | Klartext, Anhang, Reaktionen |
| `DELIVERED` | ausgehend, `unlocked`, `readAt == null` | „✓ Freigegeben" |
| `READ` | ausgehend, `readAt != null` | „✓✓ Gelesen" |

Gesperrte Briefe behalten das Antippen mit Peitschengeräusch und das lange Drücken für
Versiegelungs-Hash, Prüfschlüssel und Prüfdatei-Export.

### 3.2 Zustände der Übersicht und des Threads

| Zustand | Übersicht | Thread |
| --- | --- | --- |
| Laden | Ladeanzeige über der Liste, vorhandene Zeilen bleiben stehen | Ladeanzeige im Nachrichtenbereich |
| Leer | „Noch keine Gespräche. Füge unter *Leute* jemanden hinzu." mit Sprung nach `Leute` | „Noch nichts geschrieben." |
| Fehler | rote Karte oben mit Meldung und „Schließen", Liste bleibt bedienbar | rote Zeile über dem Eingabefeld |
| Ungelesen | fette Zeile, Zähler-Chip rechts, Zeile steht oben | ungelesene Nachrichten markiert |
| Bereit | ✦-Chip „1 Brief bereit" | Briefkarte hervorgehoben |
| Gesperrt | 🔒-Chip „2 versiegelt" | Briefkarte gedämpft mit Restzeit |
| Gesperrte Funktion | Zeile bleibt sichtbar, Hinweis „Chat ist in dieser Freundschaft aus" | Eingabefeld deaktiviert mit Begründung und Knopf „Regeln vorschlagen" |

**Wichtig:** Eine Freundschaft mit deaktiviertem Chat und deaktivierten Briefen verschwindet
nicht aus der Übersicht. Sie zeigt ihren Zustand und den Weg zurück — einen bilateralen
Regelvorschlag. Keine stille Sackgasse.

### 3.3 Mehrfreundeszenarien von Anfang an

Der Thread ist nach `friendId` geschlüsselt, nicht nach einer Gerät-zu-Gerät-Sitzung.
Gruppenbriefe werden serverseitig pro Empfänger materialisiert (`server/app/main.py:1411`)
und erscheinen daher heute schon korrekt in jedem Einzelthread mit ihrem `group_id`-Vermerk.
Die Übersichtsberechnung nimmt eine Liste von Gesprächspartnern entgegen und ist nicht auf
Zweiergespräche festgelegt; ein späterer Gruppenthread ist ein weiterer Gesprächstyp in
derselben Liste, kein Umbau. Siehe Abschnitt 7 für das, was bewusst nicht jetzt kommt.

---

## 4. User Flows

### 4.1 Freund hinzufügen

1. `Leute` → Feld „Exakter Benutzername" → **Suchen**, oder Auswahl aus der sichtbaren Nutzerliste.
2. **Anfragen** → `POST /api/friend-requests`. Die Zeile wandert unter „Gesendet" mit „Zurückziehen".
3. Bei der Gegenseite: Karte ganz oben im Tab `Chats` **und** Zähler am Tab `Leute` **und**
   Hintergrundbenachrichtigung (unverändert `NotificationWorker`).
4. **Bestätigen** → Freundschaft wird materialisiert, Standardregeln greifen
   (Briefe an, Chats an, EP aus, Mindestverzögerung 0 — `server/app/main.py:205-213`).
5. Die Person erscheint sofort als leeres Gespräch in `Chats`.

### 4.2 Normal chatten

1. `Chats` → Zeile antippen → Thread.
2. Text eingeben → **Senden** → `POST /api/chats/{id}/messages`, clientseitig AES-256-GCM.
3. Der Thread aktualisiert alle 5 Sekunden, die Übersicht alle 30 Sekunden.
4. Gelesen-Status: Das Abrufen des Threads setzt `read_at` für die eingehenden Nachrichten
   (`server/app/main.py:1366-1369`). Die Gegenseite sieht „✓ gelesen".
5. Ist `chats_enabled` aus, ist das Eingabefeld deaktiviert und nennt den Grund.

### 4.3 Versiegelten Brief schreiben

1. Im Thread auf **✦ Brief** → Briefblatt öffnet sich, Empfängerin/Empfänger bereits gesetzt.
2. Optional Titel, **Öffentlicher Umschlagtext** (offen sichtbar) und **Versiegelter Inhalt**
   (verschlüsselt) — die bestehende, klar getrennte Darstellung bleibt.
3. Optional weitere Empfänger oder eine Gruppe auswählen; optional Anhang bis 2 MB.
4. Freigaberegel wählen: Dauer, Zeitpunkt, manuell, beidseitig, Präsenz, Zufallsfenster.
   Die vereinbarte Mindestverzögerung der Freundschaft wird angezeigt und automatisch angehoben.
5. **Verschlüsselt senden** → `EvidenceProtocol.seal` erzeugt Commitment, SHA-256, P-256-Signatur;
   `POST /api/messages`.
6. Das Blatt schließt, der Brief steht als ausgehende Briefkarte im Thread.

### 4.4 Freigabe und Öffnen

**Empfängerseite**

1. Solange gesperrt: Briefkarte mit Restzeit oder Regeltext; Antippen erzeugt das Peitschengeräusch;
   langes Drücken öffnet den kryptografischen Nachweis mit Prüfdatei-Export.
2. Ist die Freigabebedingung erfüllt, wird die Karte zu ✦ **Bereit zum Öffnen**.
3. **Öffnen** → `GET /api/messages/{id}/content`, lokale AES-GCM-Entschlüsselung,
   Klartext erscheint in der Karte. Der Server setzt dabei `read_at`.
4. Bei `one_time` steht deutlich „Einmal-Brief — nach dem Schließen nicht mehr abrufbar".

**Absenderseite**

- `manual`: Knopf **Jetzt freigeben** in der eigenen Briefkarte, mit Rückfrage.
- `mutual`: Knopf **Meine Freigabe bestätigen**.
- Nach dem Lesen zeigt die eigene Karte „✓✓ Gelesen".

### 4.5 EP-Vorschlag — beide Seiten, kontextuell

Ausgelöst wird das **nicht** in den Einstellungen, sondern am Brief selbst:

| Auslöser | Wer sieht die Karte |
| --- | --- |
| Ich habe einen eingehenden Brief geöffnet | ich (Empfängerin/Empfänger) |
| Mein ausgehender Brief wurde gelesen (`read_at != null`) | ich (Absenderin/Absender) |

Beide Seiten bekommen damit aus demselben Ereignis heraus die Gelegenheit — genau wie gefordert.

Bedingungen, damit die Karte erscheint:

- `ep_enabled` ist in dieser Freundschaft beidseitig bestätigt aktiv;
- für genau diesen Brief existiert noch **kein** EP-Vorschlag (weder offen noch angenommen);
- die Karte wurde in dieser Sitzung nicht mit „Nicht jetzt" weggelegt.

Ablauf:

1. Karte unter der Briefkarte: „Hat dieser Brief zu Ebenen-Punkten geführt?"
   mit **EP vorschlagen** und **Nicht jetzt**.
2. **EP vorschlagen** öffnet einen Dialog: Punkte 1–1000, Titel (Pflicht), Beschreibung (optional).
   Begünstigt ist die **andere** Person, vorausgewählt und nicht auf sich selbst umstellbar
   (der Server lehnt Selbstvergabe ohnehin ab, `server/app/main.py:1025`).
   Der Brief ist sichtbar verknüpft.
3. **Senden** → `POST /api/ep/proposals` mit `letter_id`.
4. Bei der Gegenseite erscheint der Vorschlag als **Dialog** (Abschnitt 4.7), zusätzlich als
   Karte oben in `Chats` und mit Zähler am Tab `Mehr`.
5. Erst **Annehmen** durch die begünstigte Person zählt. Ablehnen und Offenes zählen nie.

EP heißt durchgängig **Ebenen-Punkte**. Die Bezeichnung „IP" kommt nirgends vor.

### 4.6 Bilaterale Einstellungsanfrage

1. Thread-Menü **⋮ → Freundschaftsregeln** (oder `Leute` → Person → Regeln).
2. Schalter für Briefe, Chats, EP sowie Mindest-Briefverzögerung erzeugen einen **Entwurf**.
   Direktes Speichern gibt es nicht und darf es nicht geben.
3. **Als Vorschlag senden** → `POST /api/friendship-settings/proposals`.
   Der eigene Vorschlag ist danach nur noch zurückziehbar, nicht annehmbar
   (`server/app/main.py:942`).
4. Bei der Gegenseite: **Dialog** mit einer Vorher-/Nachher-Gegenüberstellung, plus Karte in `Chats`.
5. **Annehmen** wendet die Regeln atomar an; bei abgeschaltetem EP werden offene EP-Vorschläge
   derselben Freundschaft serverseitig storniert (`server/app/main.py:959-964`).
6. Wirkt die Änderung einschränkend, ändert sich die Oberfläche sofort und sichtbar:
   deaktiviertes Eingabefeld statt verschwundener Person.

### 4.7 Zustellung bilateraler Anfragen

Eine Anfrage, die auf **meine** Antwort wartet, wird auf drei Wegen sichtbar:

1. **Dialog** — die dringlichste offene Anfrage erscheint beim Öffnen der App bzw. beim
   Eintreffen als Dialog mit „Annehmen", „Ablehnen" und „Später".
   Reihenfolge: Einstellungsvorschlag vor EP-Vorschlag vor Freundschaftsanfrage.
2. **Karte** — alle offenen Anfragen stehen als Karten oben im Tab `Chats`.
   „Später" entfernt nur den Dialog für diese Sitzung, nie die Karte.
3. **Zähler** — am Tab-Symbol.

Der Dialog erscheint pro Vorschlags-ID höchstens einmal je Sitzung. Er kann nicht versehentlich
mit einem unbeabsichtigten „Annehmen" quittiert werden: „Annehmen" ist der bestätigende Knopf,
der Dialog schließt bei Klick daneben mit „Später".

---

## 5. Visuelle Hierarchie

1. **Ebene 1 — Antwort erforderlich.** Anfragekarten und Anfragedialoge. Farbe: `secondaryContainer`,
   volle Breite, Aktionsknöpfe. Steht immer ganz oben.
2. **Ebene 2 — Gespräche.** Die Liste. Ungelesene Zeilen fett, mit Zähler-Chip, oben einsortiert.
3. **Ebene 3 — Zustandsauskunft.** ✦ bereit, 🔒 versiegelt, „Chat aus". Chips, gedämpft.
4. **Ebene 4 — Archiv und Technik.** Tab `Mehr`. Keine Farbe, keine Abzeichen außer offenen Anfragen.

Im Thread ist die Hierarchie: Briefkarte (Siegel, kräftiger Rahmen) sticht bewusst gegenüber der
normalen Sprechblase hervor. Die normale Sprechblase ist der ruhige Standard.

Der Testserver-Banner bleibt unverändert oberhalb von allem
(`ServerProfilePolicy.TEST_WARNING`, `android/.../ServerProfile.kt:22`).

---

## 6. Serveränderung: genau eine, rein additiv

`GET /api/chats` liefert bisher nur `last_message_at` und `unread_count`. Ohne einen Vorschautext
wäre die Gesprächsübersicht deutlich schlechter als bei jedem normalen Messenger — und eine
erfundene Vorschau kommt nicht infrage.

Ergänzt werden daher die Felder der **letzten Sofortnachricht** des Threads:
`last_ciphertext`, `last_nonce`, `last_encryption_key`, `last_sender_id`.

Begründung und Grenzen:

- Sofortnachrichten sind per Definition sofort freigegeben; der Server gibt denselben Schlüssel
  bereits über `GET /api/chats/{id}/messages` heraus (`server/app/main.py:1370-1376`).
  Es entsteht **keine** neue Offenlegung.
- Die bestehende Filterung des Threads (`chats_enabled`, `blocked`) bleibt davor
  (`server/app/main.py:1346-1348`).
- **Versiegelte Briefe bekommen keine Vorschau.** Ihr Schlüssel bleibt bis zur Freigabe gesperrt.
  Die Übersicht zeigt für Briefe nur Titel, Umschlagtext und Zustand.

Alle übrigen benötigten Daten existieren bereits: `read_at` im Postausgang
(`server/app/main.py:1466`), `letter_id` in EP-Vorschlägen und -Verlauf (`server/app/main.py:992`),
eingehende und ausgehende Einstellungsvorschläge (`server/app/main.py:896-897`).

---

## 7. Bewusst vertagte Messenger-Funktionen

Nicht in dieser Fassung, und ohne Platzhalterknopf in der Oberfläche:

| Vertagt | Grund |
| --- | --- |
| Gruppen-Thread als eigenes Gespräch | Gruppen existieren als Empfängerauswahl; ein echter Gruppenthread braucht serverseitige Gruppennachrichten-Semantik und Lesestatus pro Mitglied |
| Umfragen | setzt Gruppenthread voraus |
| Bilder und Videos als eigener Nachrichtentyp im Chat | Anhänge existieren am Brief bis 2 MB; Medien im Sofortchat brauchen Upload-Streaming, Vorschaubilder und ein Größenlimit jenseits von Base64-JSON |
| Sprachnachrichten | dito |
| Chat-Notizen als eigener Typ | Gesprächsthemen decken den Bedarf bereits ab und bleiben erhalten; ein zweites, ähnliches Konstrukt wäre unklar — genau das soll nicht gebaut werden |
| Nachrichtensuche | erst sinnvoll mit lokalem Klartext-Zwischenspeicher |
| Tippanzeige, Push in Echtzeit | braucht dauerhafte Verbindung; heute Abruf im Intervall |
| Bearbeiten und Löschen normaler Nachrichten | Rückzug existiert für gesperrte Briefe; für Sofortnachrichten fehlt die Serverseite |

Die Vorschau der Gesprächsübersicht arbeitet ausschließlich mit vorhandenen Daten. Wo nichts
vorliegt, steht „Noch keine Nachrichten" statt einer erfundenen Zeile.

---

## 8. Akzeptanzkriterien der Zwischenrelease

Erfüllt, wenn alle Punkte zutreffen:

**Navigation**

1. Die untere Leiste hat im Standard **genau drei** Einträge: `Chats`, `Leute`, `Mehr`. Ein
   vierter Eintrag `Burgen` erscheint **nur**, wenn das versteckte Experiment-Flag (Standard AUS)
   aktiviert ist; siehe Abschnitt 10.
2. Keine bestehende Fähigkeit ist unerreichbar geworden. Nachweis: Abschnitt 9.
2a. Bei ausgeschaltetem Experiment-Flag ist das Verhalten bitidentisch zur Fassung ohne
    Burgen: kein Tab, keine Schaltfläche, keine Spur. Nachgewiesen über `NavigationTest`.

**Gespräche**

3. `Chats` zeigt eine Zeile je Freundschaft, sortiert nach letzter Aktivität, Freundschaften
   ohne Aktivität alphabetisch darunter.
4. Der Thread einer Person enthält Sofortnachrichten und Briefe in einer gemeinsamen Zeitachse.
5. Ein gesperrter Brief zeigt im Thread seinen Zustand, spielt beim Antippen das Peitschengeräusch
   und öffnet bei langem Drücken den kryptografischen Nachweis mit Export.
6. Ein Brief kann aus dem Thread heraus geschrieben werden, mit gesetzter Empfängerin oder
   gesetztem Empfänger; Mehrfach- und Gruppenversand bleiben möglich.
7. Ist `chats_enabled` oder `letters_enabled` aus, bleibt die Person sichtbar, die jeweilige
   Aktion ist deaktiviert und nennt den Grund.

**EP**

8. Nach dem Öffnen eines eingehenden Briefes erscheint für die öffnende Person eine EP-Karte.
9. Sobald ein ausgehender Brief gelesen wurde, erscheint für die absendende Person eine EP-Karte.
10. Keine EP-Karte erscheint bei ausgeschaltetem EP oder wenn der Brief bereits einen
    EP-Vorschlag hat.
11. Ein eingehender EP-Vorschlag erscheint als Dialog und als Karte, nicht nur in einer Unterseite.
12. EP wird nirgends „IP" genannt; die Erklärzeile „Ebenen-Punkte (wie Experience Points, nur auf
    mehreren Ebenen)" bleibt erhalten.

**Bilaterale Regeln**

13. Freundschaftsregeln sind nur als Vorschlag änderbar; es gibt keinen direkt speichernden Weg.
14. Ein eingehender Einstellungsvorschlag erscheint als Dialog mit Vorher-/Nachher-Vergleich.
15. Der eigene Vorschlag ist nur zurückziehbar.

**Einstellungen**

16. `Mehr` beginnt mit Profil, Darstellung und Benachrichtigungen.
17. Serverprofil, Sitzungen, Blockierungen, Passwort, Wiederherstellungscode, Briefarchiv,
    Prüfdatei-Export, Gesprächsthemen und EP-Verlauf sind vorhanden und gruppiert.

**Wahrheit und Sicherheit**

18. Keine Schaltfläche ohne Funktion.
19. Keine Behauptung von Ende-zu-Ende-Verschlüsselung gegenüber dem Betreiber. Der Hinweis, dass
    der Server den AES-Schlüssel bis zur Freigabe hält, bleibt im Briefkomponisten sichtbar.
20. Versiegelte Inhalte erscheinen nirgends in einer Vorschau.

**Technik**

21. Konto- und Serverprofilisolierung unverändert: Ein Kontowechsel verwirft entschlüsselte
    Inhalte, offene Anfragedialoge und EP-Entwürfe.
22. JVM-Unit-Tests decken Übersichtsaufbau, Briefzustände, EP-Gelegenheitsregel und
    Anfragenreihenfolge ab.
23. `./scripts/verify-agent-change.sh` läuft vollständig durch.

---

## 9. Nachweis: keine Fähigkeit geht verloren

| V4-Fähigkeit | Bisher | Künftig |
| --- | --- | --- |
| Brief empfangen/öffnen | Tab `Briefe` | Thread der Person |
| Brief senden | Tab `Neu` | Briefblatt aus dem Thread |
| Empfangen/Gesendet-Archiv | Tab `Briefe` | `Mehr → Briefe & Prüfdateien` |
| Nachweis-Dialog (langes Drücken) | Briefkarte | Briefkarte im Thread und im Archiv |
| Alle offenen Prüfdateien exportieren | Tab `Briefe` | `Mehr → Briefe & Prüfdateien` |
| Reaktionen | Briefkarte | Briefkarte im Thread |
| Brief zurückziehen | Tab `Briefe`, Gesendet | eigene Briefkarte im Thread |
| Manuelle Freigabe / beidseitige Zustimmung | Tab `Briefe` | Briefkarte im Thread |
| Sofortchat | Tab `Chat` | Thread der Person |
| EP vorschlagen | Tab `EP` | Kontextkarte am Brief und Thread-Menü |
| EP annehmen/ablehnen | Tab `EP` | Dialog, Karte, `Mehr → Ebenen-Punkte` |
| EP-Verlauf, Summen, Level | Tab `EP` | `Mehr → Ebenen-Punkte` |
| Freundschaftsregeln vorschlagen | Tab `Leute`, aufgeklappt | Thread-Menü und `Leute` |
| Gesprächsthemen | Tab `Themen` | `Mehr → Gesprächsthemen`, Thread-Menü mit vorgewählter Person |
| Freunde suchen/anfragen/antworten | Tab `Leute` | Tab `Leute` |
| Entfernen und Blockieren | Tab `Leute` | Tab `Leute` und Thread-Menü |
| Gruppen anlegen | Tab `Leute` | Tab `Leute` |
| Profil, Theme, Biometrie | Tab `Mehr` | `Mehr`, oben |
| Passwort, Wiederherstellungscode | Tab `Mehr`, eingeklappt | `Mehr → Konto & Sicherheit` |
| Sitzungen, Blockierte | Tab `Mehr`, eingeklappt | `Mehr → Geräte`, `Mehr → Blockierte` |
| Serverprofil | Tab `Mehr` | `Mehr → Server` |
| Kontowechsel, Konto hinzufügen | Kopfzeile | Kopfzeile, unverändert |

---

## 10. Empire-/Burgkonzept — v0 hinter Flag + Zukunftsplan

> **Statuswechsel.** Auf ausdrücklichen späteren Nutzerwunsch ist ein **v0-Burgenexperiment
> jetzt implementiert**, aber **standardmäßig unsichtbar**: hinter einem versteckten Schalter
> „Rückstands-Modus (Beta)" in `Mehr → Fortgeschrittene Einstellungen`, Standard **AUS**.
> Flag aus = bitidentisches Verhalten, exakt drei Tabs. Flag an = ein vierter Reiter „Burgen".
> Der v0 arbeitet **rein clientseitig, abgeleitet + lokal, mit null Serveränderung**. Alles,
> was über diesen v0 hinausgeht (Serverpersistenz, echte Ressourcenmechanik, Weltkarte,
> Gruppenburgen, Saisons), bleibt **reine Planung** und wird in dieser Fassung **nicht** gebaut.

### 10.1 Die Idee

Jedes Freundesnetz bekommt eine eigene Welt: Burgen pro Person, ein Tal pro Freundschaft mit
Boten mit echter Laufzeit (Reiter, Brieftaube, Eule — unterschiedlich schnell und zuverlässig),
gehortete versiegelte Briefe in Schatzkammern, Siegel als Zeichen von Herkunft und Rang, EP als
Währung für den Ausbau der eigenen Burg, und ein freundschaftlicher Wettstreit darüber, wer die
Lage früher durchschaut — mit absichtlich zeitversetzter Information und gutmütigem Ködern.

### 10.2 Was der v0 heute umsetzt (hinter dem Flag)

Umgesetzt als reine JVM-Logik (`Castles.kt`) plus ein Screen (`CastlesScreen.kt`), Ökonomie
**Modell A** (siehe 10.5):

- **Ein Tal pro Freundschaft, genau zwei Burgen.** Kein globaler Reichtum über Freundeskreise
  hinweg — das Datenschutzproblem löst sich per Konstruktion. Platzierung deterministisch
  (niedrigere User-ID links), damit beide Handys dasselbe Bild zeigen.
- **Burgstufen** abgeleitet aus angenommenen EP dieser Freundschaft (symmetrisch für beide):
  Zeltlager · Palisadenhof ≥25 · Wachturm ≥75 · Steinburg ≥150 · Festung ≥300 · Königsburg ≥500.
- **Schatzkammer** = angenommene EP − lokal ausgegebene EP, nie negativ. Bauen zieht nur aus
  diesem abgeleiteten Guthaben; das Ausgaben-Ledger liegt gerätelokal
  (`SessionStore.castleBuilds`, Schlüssel pro Serverprofil und Konto).
- **Boten** sind ehrliche Häute über die bestehenden Freigabemodi, **keine neuen Modi**: Reiter =
  `timed`, Brieftaube = `random`, Eule = `presence`, Herold am Tor = `mutual`, Bote im Hof =
  `manual`. Der Boten-Strip visualisiert die gesperrten Briefe der Freundschaft.
- **Ehrliche Asymmetrie, beschriftet:** Eigene Bauten sind gerätelokal; die Burg der Gegenseite
  wird als EP-abgeleitete Silhouette gezeichnet. Kein vorgetäuschtes Spiegeln.
- **Experiment-Banner** auf jedem Burgen-Bildschirm; unangetasteter roter Testserver-Banner bleibt darüber.

**Harte Grenzen des v0 (eingehalten):** keine Serveränderung, kein neuer Endpunkt, kein Schema;
keine Vorschau versiegelter Inhalte; kein Leak des Zufallszeitpunkts (nur Fenstergrenzen); kein
Präsenz-Leak; kein Freikaufen unter die Mindestverzögerung; kein Gate vor dem Messenger (ein Brief
geht immer raus); keine Spiel-Benachrichtigungen; keine E2E-Behauptung.

### 10.3 Was die heutige Architektur bereits richtig macht

- **Freigabe ist ein serverseitiges Gate, kein Client-Trick.** `unlocked()`
  (`server/app/main.py:1436`) entscheidet zentral. Ein Bote mit Laufzeit ist ein weiterer
  Freigabemodus — später serverseitig, nicht client-erfunden.
- **`minimum_release_at` existiert bereits** und ist bilateral vereinbart: die „Wegstrecke".
- **Der Thread ist nach Person geschlüsselt, nicht nach Kanal.** Eine Burg gehört zu einer
  Freundschaft; ein Bote läuft zwischen zwei Burgen. Die Zuordnung existiert als `friendships`.
- **EP sind bereits eine bilateral bestätigte, nachvollziehbare Größe.** Der v0 leitet daraus
  ab, ohne den Erwerbsmechanismus zu verändern.
- **Kryptografische Nachweise pro Brief** tragen Commitment und Signatur. Ein „Siegel" im
  Spielsinn kann auf dem vorhandenen `public_key_fingerprint` aufsetzen.

### 10.4 Invarianten für spätere Serverfassungen

1. **Freigabemodi bleiben serverseitig aufzählbar.** Neue Modi ergänzen `VALID_MODES` und `unlocked()`.
2. **Der Gesprächsaufbau nimmt eine Liste von Teilnehmenden entgegen**, nicht genau zwei.
3. **EP bleiben bilateral erworben.** Ausgeben ist einseitig und braucht ein **getrenntes** Konto:
   „erworben" (unveränderlich) versus „ausgegeben" (einseitig). `ep_proposals` wird nie umgedeutet.
4. **Kein Spielzustand im Nachrichtenpfad.** Burg, Karte und Boten gehören in eigene Tabellen und
   Endpunkte. `messages` bleibt Nachrichtentransport.
5. **Zeitversetzte Information ist eine Freigaberegel, keine Sichtbarkeitslüge.**
6. **Ehrliches Threat Model bleibt.** Kein „unknackbares Siegel".

### 10.5 EP-/Fortschrittsmodelle zur Auswahl

Ausgangslage: angenommene EP sind heute **unveränderlich** und überleben das Freundschaftsende
(`/api/ep` filtert nur nach Beteiligten, `server/app/main.py:1004`); Punkte 1–1000, nie negativ;
offene Vorschläge werden bei Entfernen/Blockieren/EP-aus storniert. Vier tragfähige Modelle:

#### Modell A — „Chronik & Schatzkammer" (empfohlen, im v0 umgesetzt)
Unveränderlich erworbene EP **plus** getrennte, ausgebbare Schatzkammer.
- *Besitz:* Erworbenes und Schatzkammer gehören der Person, geführt **pro Freundschaft**.
- *Rücknahme:* nie; Korrekturen laufen vorwärts über einen neuen Gegen-EP-Vorschlag.
- *Gebautes:* bleibt bestehen. *Negative Salden:* per Konstruktion unmöglich (Ausgabe ≤ Guthaben).
- *Griefing:* kein einseitiger Hebel; Zuwachs braucht Annahme, Ausgeben trifft nur die eigene Burg.
- *Ausgeben:* Burgausbau, Kosmetik. *Status/Verlauf:* das bestehende globale Level (given+received).
- *Passt zur Bilateralität:* am besten — ändert den heutigen EP-Vertrag **nicht**.

#### Modell B — „Lebendige Burg" (reversible Beziehungs-EP + permanente Meilensteine)
Die Burg spiegelt den aktuellen Paar-Stand; bilaterale Korrekturvorschläge können EP senken,
Bauten oberhalb der Schwelle verfallen zur Ruine; erreichte Meilensteine bleiben unzerstörbar.
- *Besitz:* gemeinsamer Paar-Stand. *Rücknahme:* über einen neuen bilateralen Korrekturmechanismus
  (eigene Tabelle) — **kündigt als einziges Modell die Unveränderlichkeit angenommener EP auf**.
- *Gebautes:* verfällt sichtbar, Denkmäler nie. *Negativ:* bei 0 geklemmt.
- *Griefing:* kein einseitiger, aber Korrektur-Verhandlungskriege möglich; Verfall wirkt strafend.

#### Modell C — „Lehen & Vermächtnis" (EP pro Freundschaft + persönliches Erbe)
Jeder angenommene EP speist das **Lehen** dieser Freundschaft (nur daraus wird dieses Tal gebaut)
und das nie ausgebbare persönliche **Vermächtnis** (Titel, Rang — faktisch das heutige globale Level).
- *Rücknahme:* keine. Freundschaftsende friert das Lehen ein („verlassene Burg"), Vermächtnis bleibt.
- *Gebautes:* friert mit dem Tal ein. *Negativ:* unmöglich. *Griefing:* keiner einseitig.

#### Modell D — „Saisonburg" (Saison/Treuhand)
EP fließen in einen Saisontopf pro Freundschaft; Bauten sind temporär; zum Saisonende wird die
Stufe dauerhaft in die Chronik geschrieben, das Brett setzt sich zurück.
- *Rücknahme:* erübrigt sich (Reset). *Gebautes:* absichtlich vergänglich, Chronik dauerhaft.
- *Griefing:* niedrigster Einsatz; dafür kann der Reset frustrieren, und Saisondruck beißt sich
  mit dem Langsamer-Briefe-Geist der App.

#### Vergleichsmatrix

| Kriterium | A Chronik | B Lebendig | C Lehen | D Saison |
| --- | --- | --- | --- | --- |
| Fortschritt gehört | Person (pro Freundschaft) | Paar | Freundschaft + Person | Freundschaft/Saison |
| Angenommene EP | unveränderlich | reversibel | unveränderlich | in Saison, dann eingefroren |
| Gebautes bei EP-Entzug | bleibt | verfällt (Ruine) | n/a (kein Entzug) | Reset am Saisonende |
| Negative Konten | unmöglich | geklemmt | unmöglich | unmöglich |
| Fremdzerstörung | nein | nur bilateral | nein | nein |
| EP-Vertrag heute | unverändert | **geändert** | unverändert | unverändert |
| Serveraufwand v1 | gering | hoch | mittel | mittel–hoch |

**Empfehlung (offen gelassen): Modell A mit Schatzkammer pro Freundschaft.** Es kommt ohne jede
Änderung am heutigen EP-Vertrag aus und liefert die Vermächtnis-Idee aus C gratis mit (das globale
Level existiert schon). D lässt sich später als Überbau ergänzen. B nur wählen, wenn die
Unveränderlichkeit angenommener EP bewusst neu verhandelt werden soll — das ist eine
Produktvertragsänderung, keine Spielfunktion. Die Entscheidung bleibt dem Nutzer überlassen.

### 10.6 Weltobjekte mit echtem Nutzen

Jedes Objekt trägt eine reale Funktion; kein reines Deko-Slop. Drei Kategorien:
**[B]** bestehende Funktion in neuer Metapher · **[M]** spätere echte Mechanik · **[K]** rein kosmetisch.

| Objekt | Kat. | Trägt welche Funktion / warum es Spaß macht |
| --- | --- | --- |
| Burg | [B] | die Freundschaft selbst; Stufe = angenommene EP. Sichtbarer Fortschritt statt einer Zahl |
| Schatzkammer / Tresor | [B] | ausgebbares EP-Guthaben (Modell A). Macht „Danke" greifbar |
| Postamt / Poststelle | [B] | der Personen-Thread: Ein- und Ausgang an einem Ort |
| Archiv / Bibliothek | [B] | Briefarchiv + Prüfdateien; Regale = Nachweisexport |
| Reiter/Taube/Eule/Herold/Hofbote | [B] | die fünf Freigabemodi als Boten; macht Wartezeit sichtbar |
| Stall / Eulenturm / Vogelhaus | [M] | Boten als Komponisten-Presets (v1): sechsteiliges Freigabeformular auf einen Tipp |
| Werkstatt | [M] | Bauen: EP-Guthaben → Bauten. Der eigentliche Ausgabe-Loop |
| Wachturm | [B] | „Wartet auf deine Antwort": offene bilaterale Anfragen als Signalfeuer |
| Tor / Zugbrücke | [B] | `mutual`-Freigabe: beide öffnen das Tor. Auch das EP-Gate der Freundschaft |
| Kartenraum | [M] | Talübersicht; später optional dekorative Reichskarte (Positionen aus IDs abgeleitet) |
| Banner / Wappen | [K] | Profilfarbe + Avatar-Emoji als Heraldik. Reine Personalisierung |
| Siegel | [B]/[K] | Nachweis-Fingerabdruck als Wachssiegel (echt) plus kosmetischer Siegelrang |
| Markt | [M] | späterer Ort für Kosmetik gegen Schatzkammer-EP; nie für Reichweite/Tempo von Briefen |
| Gesprächsthemen-Tafel | [B] | Themen/Notizen als Aushang im Hof; deckt den Notiz-Bedarf ohne neues Konstrukt |
| Hafen / Karawane | [M] | Platzhalter für spätere Gruppen-/Allianzverbindungen; heute bewusst leer |

Ausdrücklich **nicht** als Objekt erfunden: alles, was Briefe teurer, langsamer oder unmöglich
machen würde, und jedes Objekt, das eine falsche Sicherheitsbehauptung transportiert.

### 10.7 Einbettung der Messenger-Funktionen

Der Spielmodus ist eine **zweite Sicht auf dieselben Daten**, nie ein Ersatz. Die Bedienbarkeit
des Messengers bleibt unberührt (er funktioniert mit Flag aus identisch):

- **Normale Chats** bleiben im Postamt/Thread; sie sind Alltagsgeplauder im Hof, kein Bote nötig.
- **Versiegelte Briefe** sind die Boten-Sendungen; Freigabemodus = Botentyp.
- **Freigabemodi** = Boten (10.2). **Freundschaften** = Täler. **Gruppen** = spätere Allianz-Orte.
- **EP-Anfragen** erscheinen weiter als sichtbare Karten/Dialoge im Messenger; die Burg **zeigt**
  nur das Ergebnis, sie ersetzt die bilaterale Bestätigung nicht.
- **Themen/Notizen** = Aushang. **Medien/Umfragen** (später) = Marktstände/Ratsversammlung.
- **Benachrichtigungen** bleiben Messenger-Benachrichtigungen; das Spiel erzeugt in v0 keine eigenen.

### 10.8 Mehrfreundeszenarien und Gruppen

- **Individuelle Karten:** je Freundschaft ein Tal; kein globaler Reichtum. Überlappende
  Freundesgraphen bleiben getrennt — Veits Tal mit A zeigt nie Wohlstand aus Veits Tal mit B.
- **Sichtbarkeit fremder Burgen:** nur die eigene Gegenseite im gemeinsamen Tal, niemand Dritter.
- **Neue Freunde einladen** ist der bestehende Freundschaftsfluss; ein neues Tal entsteht bei
  beidseitiger EP-Freischaltung.
- **Gruppenburgen/Allianzen** (später): eine Gruppe bekäme eine eigene Karte. Braucht Gruppen-EP-
  Semantik, die nicht existiert (es gibt nicht einmal Gruppenthreads, §7). Vertagt.
- **Vollständig abschaltbarer Spielmodus:** der Flag ist pro Serverprofil; aus = kein Tab, keine
  Spuren. Später serverseitig zusätzlich bilateral vereinbar wie EP, damit niemand ins Spiel
  gezwungen wird.

### 10.9 Offene Entwurfsfragen für die Servervariante

- Wer „bezahlt" einen echten Botenlauf, und was passiert bei Freundschaftsende während des Laufs?
- Wie wird Wettstreit *freundschaftlich* gehalten (Nutzerwunsch), ohne echtes Ärgern?
- Wie sieht die Gegenseite ihre eigenen Bauten, sobald Bauten serverpersistent werden?
- Saison-Datenform (Modell D) so wählen, dass Saisons ohne Migration nachrüstbar sind.

**Über den v0 hinaus wird in dieser Zwischenrelease nichts davon umgesetzt.**

### 10.10 Ortsanker, Poststellen-Schild und Tafelseiten (umgesetzt)

- Jede Ebene trägt ein knappes Ortsschild plus Zurück-Token: „Tal mit X" → „Dein Hof" →
  „Poststelle" / „Schatzkammer" / „Baustelle". Android-Back läuft dieselbe Kette.
- Auf Tal (an der eigenen Hütte) und Hof (an der Tafel) hängt ein Zähl-Täfelchen
  „✉ gesamt" plus „✦ bereit/wartet", abgeleitet aus denselben Buckets wie die Tafel
  (`Conversations.postStatus`). Kein Täfelchen bei 0 Briefen.
- Die Tafel ist blätterbar: 2 Spalten (Erhalten links, Gesendet rechts) × 4 Nägel je
  Seite; `FiefScenes.boardPages` verteilt jeden Brief auf genau eine Seite, Seite 1
  trägt Dringendes. Wischen oder ‹ ›-Token blättern; „Seite k/n" steht an der Tafel,
  die Gesamtzahl am Schild. Das Briefverzeichnis (Liste) bleibt als Schublade.

### 10.11 Kreativmodus (Testserver, umgesetzt)

Zweck: berechtigte Testkonten bauen kostenlos und testen Briefe/Animationen schnell —
ohne Fake-Konten und ohne ein einziges umgangenes Gate.

- **Drei Schlüssel, alle nötig:** `/api/status` liefert `server_role` und
  `creative_entitled`; dazu ein lokaler Schalter (`Mehr → Erweiterte Einstellungen →
  Kreativmodus (Testserver)`, nur sichtbar wenn Rolle=test und Entitlement). Das Gerät
  speichert nur den Schalter (`CreativeMode` in `CreativeMode.kt`).
- **Entitlement:** `users.creative_entitled`, Default 0. Einmalige, idempotente
  Migration beim Serverstart (`meta`-Marker `creative_entitlement_migrated_v1`), nur bei
  `SERVER_ROLE=test`: bestehende Nutzer werden berechtigt, spätere nicht. Produktion
  bleibt unberührt (`server/tests/test_v6_creative.py`).
- **Freies Bauen:** eigenes lokales Ledger (`castles_creative_…`); `Fief.buildFree`
  hält die Kettenreihenfolge, ignoriert nur den Preis. Schatzkammer zeigt weiter die
  echten angenommenen EP; nichts wird ausgegeben, nichts wird negativ, Modus aus =
  ehrlicher Stand. Der EP-Schalter der Freundschaft gilt auch im Kreativmodus.
- **Kennzeichnung:** Schild „⚗ Kreativ" auf Tal/Hof/Poststelle/Baustelle; Baupläne
  tragen „frei" statt Münzen.
- **Testbriefe:** ein Preset-Chip „⚗ 2 Min" im Briefblatt setzt nur Dauer/Einheit —
  Versand, Krypto, Freundschafts- und Freigabe-Gates bleiben der echte Flow.
