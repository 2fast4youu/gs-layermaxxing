# Island building and layout

The starter is painted bare sand without menu buildings, ruins or primitive stand-ins.
The avatar remains visible; every application function is available from **Menü**.

## Screen structure (7.9.4)

- The app's bottom navigation (Chats · Inseln · Mehr) is the same composable
  (`MainNavigationBar`) on every tab, the island included. Settings stay reachable via Mehr.
- Directly above it the island has its own second row: on the overview *Meine Insel · Hafen ·
  Menü*, on the own island *Übersicht · Bauen · Bearbeiten · Menü*, when visiting
  *Übersicht · Meine Insel*.
- The own island opens zoomed in (zoom depends on land size) and cannot be zoomed out to overview
  size. Its header is a painted brass plate "Meine Insel" with a creative badge when active; the
  back pill reads "‹ Übersicht". The overview is labelled "Übersicht · alle Inseln".

## Building

**Bauen** opens a horizontal shelf above the second row with painted cards in three tabs:
*Gebäude* (the six function buildings, price or "gratis"), *Deko* (every decoration item) and
*Land* (expand the island, new life place). Tapping a card starts a local placement preview on the
island: the real sprite stands raised on a green/red footprint over the grid; drag or tap to
move, ✓ **Bestätigen** saves, ✗ **Abbrechen**, the back arrow and Android back discard it.

A **short tap** on a building or decoration opens the compact action bar (Öffnen, Ausbauen/Info,
Verschieben, Abreißen; decoration: Verschieben, Entfernen). A **long press** lifts the piece
immediately into move mode. **Bearbeiten** makes every tap a move and shows empty life plots.

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

Coordinates are integer cells on a 20-step island-local grid, transformed using land scale by the
same renderer in home, overview, friend profile and visit. `construction.positions` and
`construction.decor` live in the existing JSON ledger with the same revision/atomic write
protection as upgrades. Invalid or stale writes never save; after a failed confirmation the app
reloads current state while keeping the local draft for a retry.

Rendering tests (VillageVisualReview 80–92) exercise the shelf, placement, long-press edit,
demolish dialog and result, creative interaction, the navigation layout, the menu and free
decoration. These are JVM simulations, not evidence of device animation, haptics or multitouch.
