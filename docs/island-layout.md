# Island building and layout

The starter is painted bare sand without menu buildings, ruins or primitive stand-ins.
The avatar remains visible; every application function is available from **Menü**.

## Screen structure (7.9.4, controls like Clash of Clans since 7.9.5)

- The app's bottom navigation (Chats · Inseln · Mehr) is the same composable
  (`MainNavigationBar`) on every tab, the island included. Settings stay reachable via Mehr.
- Directly above it the island has its own second row of round icon buttons with labels: on the
  overview *Meine Insel · Hafen · Menü*, on the own island *Übersicht · Bearbeiten · Menü*, when
  visiting *Übersicht · Meine Insel*. On the own island **Bauen** is the big round shop button at the
  bottom right (it steps aside while a shelf, an info bar or the edit mode is open).
- The own island opens zoomed in (zoom depends on land size) and cannot be zoomed out to overview
  size. Its header is a painted brass plate "Meine Insel" with a creative badge when active; the
  back pill reads "‹ Übersicht". The overview is labelled "Übersicht · alle Inseln".

## Building (7.9.5)

**Bauen** (shop button) opens a horizontal shelf above the second row with painted cards in three tabs:
*Gebäude* (the six function buildings, price or "gratis"), *Deko* (every decoration item) and
*Land* (expand the island, new life place). Tapping a card puts the piece on the island as a local
preview: the real sprite stands raised over a green/red footprint on the grid, and the round **✗ / ✓
bubbles hang right at the piece** (under its footprint, above it near the shore) and follow it. ✓ saves,
✗ discards; on an impossible spot the footprint turns red and ✓ is greyed out. The back arrow and
Android back discard too. The piece can be dragged straight away: a drag that starts on it carries it
with the same offset to the finger, a touch somewhere else puts it there first; the camera scrolls on
its own when the finger nears the screen edge and the piece stays under the finger.

A **short tap** on a building or decoration opens the **info bar**: name, stage, next stage with price
(or "Meisterbau erreicht") and four big round buttons (Öffnen, Ausbauen with the price on the button /
Info, Verschieben, Abreißen; decoration: Verschieben, Entfernen). The selected piece's footprint is
outlined, the camera keeps it above the bar, and a tap on empty ground closes the bar.
A **long press** lifts the piece into move mode (haptic tick). **Bearbeiten** puts the island in edit
mode: the grid fades in, pieces wiggle, touching a piece lifts it and a drag carries it right away;
empty life plots are shown. *Fertig* leaves it.

**Abreißen** always asks first. Normal mode: allowed, no points are refunded. Creative mode: free.
Life places can be demolished from their sheet. Moving is free and never changes level or points.

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
It is only shown on land (`IslandPlans.inside`), fades in while editing or placing and is stronger near
the held piece. The diamonds have the height/width ratio of the lattice (about .82): the stored lattice
is square in plan and cannot be rotated without moving existing layouts, so the mesh is the lattice's own
diagonal view. `construction.positions` and `construction.decor` live in the existing JSON ledger with
the same revision/atomic write protection as upgrades. Invalid or stale writes never save; after a failed
confirmation the app reloads current state while keeping the local draft for a retry.

Tests: `IslandGridTest` (projection, round trip, mesh slope, footprint, edge scroll),
`IslandCocControlsTest` (✓/✗ at the building, info bar and tap on ground, edit mode, drag round trip,
shop button, camera keeps the selection above the bar) and VillageVisualReview `coc-*`. These are JVM
simulations, not evidence of device animation, haptics or multitouch.
