# Island building and layout

The starter is painted bare sand without menu buildings, ruins or primitive stand-ins.
The avatar remains visible; every application function is available from **Menü**.

## Screen structure (7.9.4, controls like Clash of Clans since 7.9.5, one context at a time since 7.9.6)

- The app's bottom navigation (Chats · Inseln · Mehr) is the same composable
  (`MainNavigationBar`) on every tab, the island included, and always reachable. Settings stay reachable via Mehr.
- **Own island (7.9.6 "flow")**: no second row any more. At rest there is only the bottom navigation,
  one narrow column of round buttons at the right edge
  above the navigation: *Menü* and the bigger primary **Bauen**
  button with its label and a price chip (cheapest next step, e.g. "ab 25 P"; "gratis" in creative mode).
  At most one contextual element is shown above the navigation; they share one bottom container and
  slide/fade into each other (180–260 ms, FastOutSlowIn), so nothing stacks and nothing jumps.
- On the overview (7.9.8) only **Menü** remains above the app navigation. Its menu offers
  **Meine Insel** to return home; functional buildings remain accessible there. When visiting,
  *Übersicht · Meine Insel* stays available.
- The own island opens zoomed in (zoom depends on land size) and cannot be zoomed out to overview
  size. Its header is a painted brass plate "Meine Insel" with a creative badge when active; the
  overview entry is available only in **Menü**. The overview is labelled "Übersicht · alle Inseln".

## Building (7.9.5, flow 7.9.6)

**Bauen** (shop button) opens a slim bottom sheet (≤ 28 % of the height; it replaces the side column
and any open actions) with painted cards in one continuous strip:
*Gebäude* (the six function buildings, price or "gratis"), *Deko*, *Besondere Deko* (max 3,
120–200 points, free in creative mode), and *Land* (expand the island). The tabs follow scrolling and animate to their section. Tapping a card puts the piece on the island as a local
preview: the real sprite stands raised over a green/red footprint on the grid, and the round **✗ / ✓
bubbles hang right at the piece** (under its footprint, above it near the shore) and follow it. ✓ saves,
✗ discards (and brings the sheet back when the piece came from it); on an impossible spot the footprint
turns red and ✓ is greyed out. **The preview never leaves the land grid** (`IslandGrid.nearestLandCell`):
a finger in the water keeps it on the nearest shore cell, a tap beside the island is ignored, and a new
piece starts in the middle of the visible land. While a piece is held every other control fades out except
the short hint at the top ("Ziehen oder tippen · Grün = frei, Rot = belegt", fades after ~2.6 s) and ✓/✗
at the piece; the bottom navigation stays. The back arrow and
Android back discard too. The piece can be dragged straight away: a drag that starts on it carries it
with the same offset to the finger, a touch somewhere else puts it there first; the camera scrolls on
its own when the finger nears the screen edge and the piece stays under the finger.

A **short tap** on a building or decoration shows its **actions right at the piece** (7.9.6, no bottom
card any more): a small name sign and one pill of round icons (Öffnen, Ausbauen with the price on the
button / Info, Verschieben, Abreißen; decoration: Verschieben, Entfernen). It pops in with scale + fade,
the footprint is outlined, the camera pans smoothly only as far as needed to keep piece and actions on
screen, and a tap on empty ground closes it.
A **long press** (`LONG_PRESS_MS` = 350 ms) lifts the piece straight into move mode (haptic tick, the
sprite rises, the grid fades in) and the **same finger** carries it on without lifting; on release the
preview stays with ✓/✗. One touch surface (`IslandTouchSurface`) handles tap, long press, edit and
placement, so the gesture never changes hands. "Verschieben" in the actions remains the alternative.. There is no separate edit mode or empty life plot any more (7.9.7).

**Abreißen** always asks first. Normal mode: allowed, no points are refunded. Creative mode: free.
Former life places are special decoration and use the same remove confirmation. Moving is free and never changes level or points.

## Creative mode switch (7.9.6)

Where the account may use it (test server role + entitlement), the island menu starts with one plainly
labelled switch "Kreativmodus: Ein/Aus" with a one-line explanation. It acts at once (the menu closes, the
header shows exactly one chip "Kreativmodus · alles gratis", the Bauen button and the sheet say "gratis");
nothing disappears. The first time it is switched on, a short note explains it once (stored in the
`island_hints` preferences, gone after ~7 s or "Verstanden"). The switch in Settings stays as well.

## Decoration

Decoration is no longer bound to six fixed "+" slots. Each object is stored in the construction
ledger as `{id, item, position}` and validated like buildings: on land, not on a building/place
(2.4 cells / land scale), not on other decoration (1.5 cells / land scale), unlocked by score in
normal mode (free in creative mode), at most `decor_limits[land]` objects in normal mode.
Existing slot decoration migrates lazily and losslessly: on first read it becomes free objects at
its old slot positions (`slot` remembered); `island_decor` rows are never deleted and the legacy
`PUT /api/island/decor` keeps working for old clients. The top-level `decor` slot map still
lists objects that stand on their original slot, for old renders.

## Grid and persistence

Coordinates are integer cells on a 20-step island-local grid (server: `position=[x,y]`, unchanged since
7.9.4, no migration), transformed using land scale by the same renderer in home, overview, friend
profile and visit. `IslandGrid` is the grid's side of that one projection: `project` (cell → screen,
identical to what `IslandPlans.placedBuildings`/`decorPieces` draw), `unproject` (finger → nearest cell),
`footprint` (diamond around a cell whose corners are cells) and the mesh. The grid is drawn as an
**isometric diamond mesh**: its lines run along the diagonals of the lattice, so they are never
axis-parallel and follow the oblique view of the painted island; a footprint lies exactly on that mesh.
It is only shown on the shared stage-specific land grid (`IslandGrid.buildableCells`, server fixture `island_grid.json`), fades in while moving or placing and is stronger near
the held piece. The diamonds have the height/width ratio of the lattice (about .82): the stored lattice
is square in plan and cannot be rotated without moving existing layouts, so the mesh is the lattice's own
diagonal view. `construction.positions` and `construction.decor` live in the existing JSON ledger with
the same revision/atomic write protection as upgrades. Invalid or stale writes never save; after a failed
confirmation the app reloads current state while keeping the local draft for a retry.

Camera note (7.9.6 fix): fingers and fractions are in island-box coordinates; the camera box is as tall
as the viewport with the island box centred in it. 7.9.5 mixed both, so edge scrolling fired mid-screen while
dragging and a new piece could start in the sea.

Tests: `IslandFlowTest` (land clamp, tap in the water, long press + same finger, short tap, rest state,
actions at the piece, slim sheet never together with the actions, Bauen always there, price/gratis, placement
chrome + hint, creative chip and menu switch, full labels), `IslandGridTest` (projection, round trip, mesh slope, footprint, edge scroll),
`IslandCocControlsTest` (✓/✗ at the building, info bar and tap on ground, edit mode, drag round trip,
shop button, camera keeps the selection above the bar) and VillageVisualReview `coc-*`. These are JVM
simulations, not evidence of device animation, haptics or multitouch.

## 7.9.9-start

The island menu has no bottom pills any more (no Übersicht/Bauen/Einstellungen); the own island has a painted brass back plate "← Übersicht" at the top left (≥48 dp) and Android back leads to the overview too. A tapped building offers Öffnen, Ausbauen (price) and Abreißen; moving is the long press only. The build strip holds cards only (tabs above still follow the scroll); what cannot be placed now is greyscale with a short reason. Details: docs/insel-ausbau.md.
