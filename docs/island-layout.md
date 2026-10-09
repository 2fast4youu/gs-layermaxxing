# Island building and layout

The starter is painted bare sand without menu buildings, ruins or primitive stand-ins.
The avatar remains visible; every application function is available from **Menü**.

**Bauen** opens the building inventory, land expansion and layout editing. A new building
is a local preview until **Bestätigen**. Drag or tap the island grid to change its location;
green means free, red disables confirmation. **Abbrechen**, the back arrow and Android
back discard the preview without spending points. Selecting a built structure shows the
bottom context toolbar (open, move, upgrade/info). Upgrading has the existing painted
before/after preview and server-enforced point/day gates. Moving is free and never changes
building level, spent points or unlocked progression. Normal buildings cannot be demolished;
creative buildings retain their separate free downgrade rules.

Coordinates are pairs of integer cells on a 20-step island-local grid, transformed using land
scale by the same renderer in home, overview, friend profile and visit. Land expansion or
creative shrink preserve local layout coordinates. `construction.positions` is stored
inside the existing JSON ledger, separately for normal and creative worlds, with the same
revision/atomic write protection as upgrades. Old ledgers need no migration; missing
positions use the prior coastal anchors. Existing buildings and paid progress are not reset.

The construction endpoint accepts `position` on first build or move. It validates land,
grid shape and collisions with built structures, life places and decoration. The minimum
spacing scales inversely with land size, keeping the painted sprite footprint consistent
with the placement grid; small islands naturally need more land before dense layouts. New places
and decoration perform the reverse collision check under the SQLite writer lock. Existing
legacy overlap remains intact when upgrading without relocation. Invalid or stale writes
never save; after failed confirmation the app reloads current state while retaining the
editable local draft.

Rendering tests exercise build selection, actual drag-and-confirm, moving, invalid
placement, cancel/back, upgraded home and actual friend islands. These are JVM simulations,
not evidence of device animation, multitouch or media behavior.
