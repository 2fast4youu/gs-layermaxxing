# Clickpath-Opus Source Material

Stand: aktueller Checkout `gregor-erweiterung`, Version 7.7.1-uebersicht. Dieses Paket dokumentiert den Ist-Zustand für einen späteren Opus-Redesign-Schritt. Das alte Menüplan-Artefakt war im Checkout nicht vorhanden; die Navigation ist daher aus dem aktuellen Quelltext rekonstruiert.

## Render-Ergebnis

- **44** Testmethoden ausgeführt: 30 in `VillageVisualReview.kt` plus 14 in der separaten `ClickpathVisualReview.kt`.
- **43** eindeutige, per Pixelstatistik nicht-weiße PNGs ausgeliefert; Dialogfenster werden in ihrer tatsächlichen Popup-Fenstergröße gespeichert (Robolectric SDK 34, `w411dp-h891dp-xxhdpi`).
- **1** Fläche fehlt bewusst: `NewQuestSheet` wurde nicht ausgeliefert, weil Robolectric für das `ModalBottomSheet`-Popup kein gemaltes Fenster exponierte; ein schwarzer/weißer Platzhalter wäre irreführend.
- Fixtures sind synthetisch; es wurden keine Serverdaten oder Secrets verwendet.
- Ausgabe: `docs/clickpath-opus/rendered/`.
- `:app:testDebugUnitTest --tests at.gregor.layermaxxing.ClickpathVisualReview` — **BUILD SUCCESSFUL**, 14/14 Tests.
- `pixel-evidence.json` enthält pro ausgelieferter Datei Dimensionen, Mittelwerte, Kanalvarianz und `nonwhite_pixels`; Ergebnis: **43 nonblank, 1 missing**.

## Bekannte Einschränkung

Die früher behaupteten Kollisionen waren keine echten Dateikollisionen: verglichen werden müssen die vollständigen Namen, nicht nur die Nummern. Die aktuellen 43 PNG-Dateinamen sind exakt eindeutig; `13-login` und `13-chat-extras` sowie die anderen ähnlich nummerierten Paare überschreiben sich nicht.

## Coverage

`screens.json` enthält **44** rekonstruierte Inventar-Einträge: **43 rendered**, **1 missing**. Die 13 ausgelieferten vormals fehlenden Oberflächen werden mit synthetischen, netzwerkfreien Fixtures in `ClickpathVisualReview.kt` gerendert. `ProofDialog` ist ausdrücklich als Legacy-Fixture markiert; ein kryptographisch validierter Proof-Variant ist nicht behauptet.

`edges.json` enthält verifizierte Navigationskanten mit Dateireferenzen und die neu abgedeckten Surface-Ziele. Bounds/semantics sind nur bei den im Test tatsächlich ausgeführten Gesten belastbar: Post-Content-Description-Tap, Pinch-Zoom und Double-Tap. Für die direkten Surface-Fixtures sind sichtbare Button-/Text-Ziele dokumentiert; pixelgenaue Bounds wurden nicht aus dem Compose-Semantics-Tree exportiert.

## Dateien

- `index.html` — menschliche Galerie
- `screens.json` — Inventar mit rendered/missing und Quellen
- `edges.json` — rekonstruierte Navigation mit Evidence
- `OPUS-HANDOFF.md` — deutscher Prompt für späteren Opus-Schritt
- `rendered/*.png` — Screenshots
- `clickpath-rendered.zip` — komprimiertes Paket der 43 ausgelieferten PNGs
- `pixel-evidence.json` — maschinenlesbare Pixelstatistik und Missing-Evidence
- `ClickpathVisualReview.kt` — 14 zusätzliche Screen-/Sheet-/Dialog-Fixtures
