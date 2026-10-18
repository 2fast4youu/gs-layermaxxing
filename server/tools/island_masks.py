#!/usr/bin/env python3
"""Generate (or --check) server/app/geometry/island_masks.json, the grid_version 5 occupancy masks.

7.9.12: a piece occupies exactly the grid cells its RENDERED sprite covers on screen, measured from the painted
WebP alpha (transparent margins are not space), composited like the app draws it (IslandEvolution.kt
PaintedIslandPlace: Fit + BottomCenter, accent 38 % at CenterEnd / TopCenter, tier-3 flag 30 % TopEnd; trees and
palms that sway are measured at both sway extremes). The size of every sprite on every land stage is part of the
fixture, and the app's renderer is tested against it (IslandMasksTest), so drawing, targets, green/✓ and the server
use one geometry.

Projection (IslandGrid.kt / IslandTerrain.kt): the island box is W wide and H = W / ASPECT high. Lattice point
(i, j) on land stage L lies at x = CX + (i/20 - CX) * s, y = CY + (j/20 - CY) * s (fractions of W and H,
s = constructionScale(L)). A piece's spot is the lattice point where its sprite's foot (bottom centre) stands;
its square sprite box is `size` * W wide and high. A screen point belongs to the lattice cell whose rectangle
(half a step to every side) contains it; those rectangles tile the plane, so two pieces whose masks share no cell
can never share a painted pixel.

- mask: every cell holding opaque sprite pixels (alpha >= ALPHA, specks below SPECK px dropped, EPS cell margin).
- base: the ground part = the cells of the lowest silhouette band whose height is half the silhouette width (the
  depth of an isometric ground plan). It must lie on buildable land; roofs may reach over the painted shore.
- harbour: its base above the spot row must be land, the spot is a coast cell and the cell in front of it (towards
  the viewer, where the pier points and ships berth) is water.

Usage: python3 server/tools/island_masks.py [--check]   (needs Pillow; not a server dependency)
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res/drawable-nodpi"
OUT = ROOT / "server/app/geometry/island_masks.json"

ASPECT = 1367 / 1116
LAND_SCALE = (.46, .58, .70, .82, .92, 1.0)
N = 720            # px of the square sprite box while measuring
ALPHA = 128        # opaque enough to be a body (soft painted shadows stay below)
SPECK = 24         # connected components smaller than this (px at N) are stray paint, not a body
EPS = .03          # a pixel this close (in cells) to a cell border counts for both cells (resolution safety)
SWAY_DEG = 2.4     # DynamicIsland: sway * (1.4 + (i % 3) * .5), |sway| <= 1

# (building, stage) -> sprite, accent, base size; IslandStages.kt + IslandPlans.homeBuildings
STAGES = {
    "POST": ["n_tree", "lm_post_ruin", "lm_post", "b_post"],
    "LIGHTHOUSE": ["decor_palm", "fief_tower_small", "lm_lighthouse", "b_lighthouse"],
    "LIBRARY": ["n_crates", "lm_library_ruin", "lm_library", "b_library"],
    "CAMPFIRE": ["lm_campfire", "b_workshop", "b_hall", "b_hall"],
    "HARBOUR": ["n_rowboat", "lm_jetty_ruin", "b_jetty", "lm_jetty"],
    "HOUSE": ["decor_hammock", "fief_hut_stage1", "b_house", "isle_manor"],
}
ACCENT0 = {"POST": "p_env", "LIBRARY": "p_book", "HARBOUR": "decor_flag"}
BASE_SIZE = {"HOUSE": .11, "LIGHTHOUSE": .105, "POST": .105, "CAMPFIRE": .10, "LIBRARY": .10, "HARBOUR": .115}
SWAY_STAGE0 = {"POST", "LIGHTHOUSE"}
DECOR = {"flowers": "decor_flowers", "bench": "decor_bench", "lantern": "decor_lantern", "flag": "decor_flag",
         "palm": "decor_palm", "campfire": "decor_campfire", "hammock": "decor_hammock", "fountain": "decor_fountain",
         "windmill": "decor_windmill", "maibaum": "decor_maibaum"}
SPECIAL = {"place_home": "place_home", "place_bude": "place_bude", "place_uni": "place_uni", "place_hut": "place_hut",
           "place_club": "place_club", "place_station": "place_station", "place_city": "place_city",
           "place_desk": "place_desk", "place_work": "place_desk", "place_cafe": "place_city"}
UNKNOWN = "fief_signpost"


def piece_scale(land):
    """IslandPlans.pieceScale(constructionScale(land)): things shrink a little on a small island (7.9.12: buildings too)."""
    return .6 + .4 * LAND_SCALE[land]


def building_size(b, stage, land):
    return round(min(BASE_SIZE[b] * (.9 + stage * .16), .145) * piece_scale(land), 5)


def decor_share(item):
    if item.startswith("place_"):
        return .14
    return {"windmill": .12, "maibaum": .12, "palm": .12, "flowers": .075, "campfire": .075}.get(item, .09)


def decor_size(item, land):
    return round(decor_share(item) * piece_scale(land), 5)


def _fit(img, box_w, box_h):
    w, h = img.size
    k = min(box_w / w, box_h / h)
    return img.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)


def composite(sprite, accent=None, accent_top=False, flag=False):
    """The sprite box exactly like PaintedIslandPlace draws it, N x N RGBA."""
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    main = _fit(Image.open(RES / f"{sprite}.webp").convert("RGBA"), N, N)
    canvas.alpha_composite(main, ((N - main.width) // 2, N - main.height))
    def overlay(name, frac, x0, y0):
        side = round(N * frac)
        im = _fit(Image.open(RES / f"{name}.webp").convert("RGBA"), side, side)
        canvas.alpha_composite(im, (x0 + (side - im.width) // 2, y0 + (side - im.height) // 2))
    if accent:
        side = round(N * .38)
        overlay(accent, .38, (N - side) // 2 if accent_top else N - side, 0 if accent_top else (N - side) // 2)
    if flag:
        side = round(N * .30)
        overlay("decor_flag", .30, N - side, 0)
    return canvas


_cache = {}


def body(sprite, accent=None, accent_top=False, flag=False, sway=False):
    """The opaque body of one composited sprite box (measured once per picture)."""
    key = (sprite, accent, accent_top, flag, sway)
    if key not in _cache:
        _cache[key] = silhouette(composite(sprite, accent, accent_top, flag), sway)
    return _cache[key]


def silhouette(img, sway):
    """Opaque body pixels (set of (x, y)) at rest and, for swaying sprites, at both sway extremes."""
    layers = [img] + ([img.rotate(a, resample=Image.BICUBIC, center=(N / 2, N)) for a in (SWAY_DEG, -SWAY_DEG)] if sway else [])
    alpha = Image.new("L", (N, N), 0)
    for layer in layers:
        a = layer.getchannel("A").point(lambda v: 255 if v >= ALPHA else 0)
        alpha = ImageChops.lighter(alpha, a)
    return _despeckle(alpha)


def _despeckle(alpha):
    px = alpha.load()
    seen = set()
    keep = set()
    for y in range(N):
        for x in range(N):
            if px[x, y] and (x, y) not in seen:
                comp, stack = [], [(x, y)]
                seen.add((x, y))
                while stack:
                    cx, cy = stack.pop()
                    comp.append((cx, cy))
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < N and 0 <= ny < N and px[nx, ny] and (nx, ny) not in seen:
                            seen.add((nx, ny))
                            stack.append((nx, ny))
                if len(comp) >= SPECK:
                    keep.update(comp)
    return keep


def cells_of(pixels, size, land):
    """Lattice offsets (di, dj) of the cells holding [pixels] of a sprite box `size` * W wide on land stage [land]."""
    s = LAND_SCALE[land]
    cell_x = N * (s / 20) / size
    cell_y = N * (s / 20) / (size * ASPECT)
    out = set()
    for x, y in pixels:
        u = (x + .5 - N / 2) / cell_x
        v = (y + .5 - N) / cell_y
        for du in (-EPS, EPS):
            for dv in (-EPS, EPS):
                out.add((round(u + du), round(v + dv)))
    return out


def measure(pixels, size, land):
    mask = cells_of(pixels, size, land)
    xs = [x for x, _ in pixels]
    ys = [y for _, y in pixels]
    depth = (max(xs) - min(xs) + 1) / 2
    bottom = max(ys)
    base = cells_of([(x, y) for x, y in pixels if y >= bottom - depth], size, land)
    # The spot itself always belongs to the piece (its foot stands there, the sign hangs there).
    mask.add((0, 0))
    base.add((0, 0))
    return {"size": size, "mask": sorted([list(c) for c in mask], key=lambda c: (c[1], c[0])),
            "base": sorted([list(c) for c in base], key=lambda c: (c[1], c[0]))}


def generate():
    pieces = {}
    for b, sprites in STAGES.items():
        for stage, sprite in enumerate(sprites):
            px = body(sprite, ACCENT0.get(b) if stage == 0 else None, accent_top=b == "LIBRARY", flag=stage == 3,
                      sway=stage == 0 and b in SWAY_STAGE0)
            pieces[f"building:{b}:{stage}"] = {"sprite": sprite, "accent": ACCENT0.get(b) if stage == 0 else None,
                                               "flag": stage == 3,
                                               "lands": [measure(px, building_size(b, stage, land), land) for land in range(6)]}
    def decor(key, item, sprite, sway=False):
        px = body(sprite, sway=sway)
        pieces[key] = {"sprite": sprite, "accent": None, "flag": False,
                       "lands": [measure(px, decor_size(item, land), land) for land in range(6)]}
    for item, sprite in DECOR.items():
        decor(f"decor:{item}", item, sprite, sway=item in ("palm", "flag"))
    for item, sprite in SPECIAL.items():
        decor(f"decor:{item}", item, sprite)
    decor("decor:?special", "place_?", UNKNOWN)
    decor("decor:?", "?", UNKNOWN)
    return {
        "version": 5,
        "comment": "grid_version 5 occupancy masks, generated by server/tools/island_masks.py from the painted WebP alpha "
                   "(do not edit by hand). Key building:<B>:<stage> / decor:<item>; decor:?special = an unknown place_* "
                   "item, decor:? = any other unknown item (both drawn as the signpost). lands[L].size = sprite box side as "
                   "a fraction of the island width on land stage L; mask = [di, dj] lattice offsets from the spot (the "
                   "sprite's foot, dj < 0 = further up the screen) holding opaque pixels; base = the ground part that "
                   "must stand on buildable land (for the harbour: rows above the spot; the spot is a coast cell with "
                   "water in front, berth = [0, 1]).",
        "projection": {"aspect": ASPECT, "land_scale": list(LAND_SCALE), "alpha": ALPHA, "speck_px": SPECK,
                       "eps_cells": EPS, "sway_deg": SWAY_DEG, "box_px": N},
        "berth": [0, 1],
        "pieces": pieces,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="fail if the committed fixture differs from the assets")
    args = ap.parse_args()
    data = generate()
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n"
    if args.check:
        same = OUT.exists() and json.loads(OUT.read_text()) == json.loads(text)
        print("island_masks.json up to date" if same else "island_masks.json differs from the assets")
        return 0 if same else 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text)
    print(f"wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
