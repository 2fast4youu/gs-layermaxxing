#!/usr/bin/env python3
"""Generate (or --check) server/app/geometry/island_ground.json, THE grid_version 6 contract (7.9.13).

Version 6 protects GROUND, not pictures (like Clash of Clans): every piece holds one rectangle of lattice cells, the
ground plate it stands on. Roofs, crowns, towers and sails may cover other pieces on screen; the renderer sorts by the
ground line (the spot row), so the piece in front is drawn in front. Server, app, drawn grid, green/✓ and targets all
read this one file. Version 5 (geometry/island_masks.json, whole silhouettes) stays as it was, as history.

Lattice and projection are unchanged (IslandGrid.kt / main.py grid_*): lattice point (i, j) lies at (i/20, j/20) of the
painted terrain, which is scaled about (.5, .55) with the land stage; a spot is where the sprite's foot stands; sizes
are the same sprite sizes as version 5 (read from island_masks.json), so nothing is drawn bigger, smaller or elsewhere.

- ground: [x0, y0, x1, y1] lattice offsets from the spot (inclusive, y1 = 0 = the spot row, y0 < 0 = further back).
  Measured from the painted WebP alpha: the plate is the lowest band of the opaque body (BAND of its height); its width
  is the 6..94 % pixel span of that band, its depth half that width on screen (an isometric ground plan). A cell counts
  when the plate covers at least COVER of it in both directions. The rowboat (HARBOUR 0) lies flat: its whole body.
- harbour stage 1..3 (pier, jetty, harbour): the spot is the pier head, sea = the spot row (must be open water),
  ground = the rows behind it (must be beach), at most PIER_COLS wide (the pier's root, not its posts), berth = the
  cell in front of the spot (open water, where ships moor).
- zones (21 rows x 21 columns, row j, column i), classified from the painted terrain_home.webp (terrain_coast.py made
  the beach): L land/meadow, B beach (the southern cove; only the harbour stands there), S other shore sand, R rock,
  W water. A normal piece's whole ground must be land: zone L and inside the version-2..5 build ellipse of the land
  stage (grid_buildable, so the shore margin of every stage is kept). The harbour: stage 0 all ground on B; 1..3 ground
  on B, sea and berth on W. Lattice cells outside 0..20 are water.
- coast: the painted island's solid outline (land, sand, rocks; box fractions at land scale 1) and the rocks standing
  in the water (circles), for the ship routes on the map (HarbourRoute / SeaChart).

Usage: python3 server/tools/island_ground.py [--check]   (needs Pillow, numpy, opencv; not a server dependency)
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import island_masks as im  # noqa: E402  (the same sprite compositing and projection as version 5)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "server/app/geometry/island_ground.json"
MASKS = ROOT / "server/app/geometry/island_masks.json"
TERRAIN = ROOT / "android/app/src/main/res/drawable-nodpi/terrain_home.webp"

BAND = .22        # the ground plate = the lowest 22 % of the opaque body
COVER = .35       # a cell belongs to the plate when the plate covers 35 % of it (in x and in y)
PIER_COLS = 2     # the harbour's ground (boat, pier root) is at most two cells wide
COVE = (8, 12, 15, 17)  # i0, i1, j0, j1: shore sand in this box is the beach (B), elsewhere shore sand (S)


def plate(pixels, flat=False):
    """(left, right, depth) of the ground plate in sprite-box px; left/right relative to the foot (box centre)."""
    xs = np.array([p[0] for p in pixels])
    ys = np.array([p[1] for p in pixels])
    top, bottom = ys.min(), ys.max()
    band = ys >= bottom - (1.0 if flat else BAND) * (bottom - top + 1)
    left, right = np.percentile(xs[band], 6), np.percentile(xs[band], 94)
    depth = (bottom - top + 1) * .8 if flat else .5 * (right - left)
    return left - im.N / 2, right - im.N / 2, depth


def span(lo, hi):
    """Lattice offsets k whose cell [k - .5, k + .5] the interval [lo, hi] (in cells) covers by >= COVER."""
    ks = [k for k in range(math.floor(lo) - 1, math.ceil(hi) + 2) if min(hi, k + .5) - max(lo, k - .5) >= COVER]
    return (ks[0], ks[-1]) if ks else (0, 0)


def rect(pl, size, land, cols_cap=None):
    s = im.LAND_SCALE[land]
    cell_x = im.N * (s / 20) / size
    cell_y = im.N * (s / 20) / (size * im.ASPECT)
    x0, x1 = span(pl[0] / cell_x, pl[1] / cell_x)
    x0, x1 = min(x0, 0), max(x1, 0)
    if cols_cap and x1 - x0 + 1 > cols_cap:
        # keep the cells around the plate's middle (an even count leans to the side the plate leans to)
        mid = (pl[0] + pl[1]) / 2 / cell_x
        x0 = max(x0, min(0, math.floor(mid + .5) - (cols_cap - 1) + (cols_cap - 1) // 2 if mid >= 0 else math.ceil(mid - .5) - (cols_cap - 1) // 2))
        x1 = x0 + cols_cap - 1
        if x1 < 0:
            x0, x1 = -(cols_cap - 1), 0
    y0, _ = span(-pl[2] / cell_y, 0)
    return [x0, min(y0, 0), x1, 0]


def pieces(masks):
    out = {}
    for key, p in masks["pieces"].items():
        harbour = key.startswith("building:HARBOUR:")
        stage = int(key.rsplit(":", 1)[1]) if key.startswith("building:") else 0
        px = im.body(p["sprite"], p["accent"], accent_top=key == "building:LIBRARY:0", flag=p["flag"])
        pl = plate(px, flat=harbour and stage == 0)
        lands = []
        for land in range(6):
            size = p["lands"][land]["size"]
            g = rect(pl, size, land, PIER_COLS if harbour else None)
            entry = {"size": size, "ground": g}
            if harbour and stage > 0:
                # the pier head row is over the water, the rows behind it are its root on the beach
                entry["sea"] = [g[0], 0, g[2], 0]
                entry["ground"] = [g[0], min(g[1], -1), g[2], -1]
            lands.append(entry)
        out[key] = {"sprite": p["sprite"], "lands": lands}
    return out


def classify(rgba):
    H, W = rgba.shape[:2]
    rgb = rgba[:, :, :3]
    hs = cv2.cvtColor(np.ascontiguousarray(rgb), cv2.COLOR_RGB2HSV).astype(float)
    S, V = hs[:, :, 1] / 255, hs[:, :, 2] / 255
    blue = (rgb[:, :, 2].astype(float) > rgb[:, :, 0].astype(float) + 8) | (rgba[:, :, 3] < 200)
    # surf: white foam right at the blue water is water too (bright rock tops further inland are not)
    foam = (S < .16) & (V > .78) & (cv2.dilate(blue.astype(np.uint8), np.ones((15, 15), np.uint8)) > 0)
    water = blue | foam
    rock = (S < .22) & (V < .80) & ~water
    sand = (S < .36) & (V >= .74) & ~water
    veg = (S > .30) & (rgb[:, :, 1].astype(float) > rgb[:, :, 2].astype(float) + 10) & ~water
    return water, rock, sand, veg


def zones(rgba):
    H, W = rgba.shape[:2]
    water, rock, sand, veg = classify(rgba)
    rows = []
    for j in range(21):
        row = ""
        for i in range(21):
            x, y, hw, hh = i / 20 * W, j / 20 * H, W / 40 * .8, H / 40 * .8
            x0, x1, y0, y1 = int(max(0, x - hw)), int(min(W, x + hw)), int(max(0, y - hh)), int(min(H, y + hh))
            n = max(1, (y1 - y0) * (x1 - x0))
            sl = (slice(y0, y1), slice(x0, x1))
            fw, fr, fs, fv = water[sl].sum() / n, rock[sl].sum() / n, sand[sl].sum() / n, veg[sl].sum() / n
            # a mixed cell at the water line (surf, wet sand) is never land
            z = "W" if fw > .40 else "R" if fr > .22 else "S" if fs > .5 else "L" if fv > .35 else "W" if fw > .2 else "S"
            if z == "S" and COVE[0] <= i <= COVE[1] and COVE[2] <= j <= COVE[3]:
                z = "B"
            row += z
        rows.append(row)
    return rows


def coast(rgba):
    H, W = rgba.shape[:2]
    water = classify(rgba)[0]
    solid = (~water & (rgba[:, :, 3] >= 200)).astype(np.uint8) * 255
    solid = cv2.morphologyEx(solid, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lab, st, cen = cv2.connectedComponentsWithStats(solid)
    main = 1 + int(np.argmax(st[1:, 4]))
    body = cv2.morphologyEx((lab == main).astype(np.uint8) * 255, cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8))
    contours, _ = cv2.findContours(body, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    outline = cv2.approxPolyDP(max(contours, key=cv2.contourArea), 3.0, True)[:, 0, :]
    # rocks under the surface: darker than the water around them (painted, not just deep water)
    V = cv2.cvtColor(np.ascontiguousarray(rgba[:, :, :3]), cv2.COLOR_RGB2HSV)[:, :, 2].astype(np.float32) / 255
    dark = water & (rgba[:, :, 3] >= 200) & (V < cv2.GaussianBlur(V, (0, 0), 18) - .10) & (body == 0)
    dark = cv2.morphologyEx(dark.astype(np.uint8) * 255, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    solid = np.where(body > 0, 0, solid) | dark
    n, lab, st, cen = cv2.connectedComponentsWithStats(solid)
    rocks = []
    for k in range(1, n):
        if st[k, 4] < 60:
            continue
        r = math.sqrt(st[k, 4] / math.pi) * 1.15
        rocks.append([round(cen[k][0] / W, 4), round(cen[k][1] / H, 4), round(r / W, 4)])
    return {"outline": [[round(x / W, 4), round(y / H, 4)] for x, y in outline.tolist()],
            "rocks": sorted(rocks, key=lambda c: (c[1], c[0]))}


def generate():
    masks = json.loads(MASKS.read_text())
    terrain = np.array(Image.open(TERRAIN).convert("RGBA"))
    return {
        "version": 6,
        "comment": "grid_version 6 ground contract, generated by server/tools/island_ground.py (do not edit by hand). "
                   "Key building:<B>:<stage> / decor:<item> like island_masks.json (unknown items = the signpost). "
                   "lands[L].size = sprite box side as a fraction of the island width (same as version 5); ground = "
                   "[x0, y0, x1, y1] inclusive lattice offsets from the spot (y1 = 0 = the spot row, y < 0 further back) "
                   "= the cells the piece occupies; sea (harbour 1..3) = the pier head row over open water. zones: row j, "
                   "column i of the lattice: L land, B beach, S shore sand, R rock, W water (outside 0..20 = W). A normal "
                   "piece's ground lies on L cells of the land stage's build ellipse; the harbour's ground on B, its sea "
                   "and berth (spot + berth) on W. Roofs may overlap; only grounds may not share a cell. coast = painted "
                   "solid outline and rocks in the water (box fractions at land scale 1) for ship routes.",
        "projection": masks["projection"],
        "berth": [0, 1],
        "zones": zones(terrain),
        "pieces": pieces(masks),
        "coast": coast(terrain),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="fail if the committed file differs from the assets")
    args = ap.parse_args()
    text = json.dumps(generate(), ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n"
    if args.check:
        same = OUT.exists() and json.loads(OUT.read_text()) == json.loads(text)
        print("island_ground.json up to date" if same else "island_ground.json differs from the assets")
        return 0 if same else 1
    OUT.write_text(text)
    print(f"wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
