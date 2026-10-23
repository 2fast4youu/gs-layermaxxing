#!/usr/bin/env python3
"""Style E asset pipeline (7.9.14): 24 island sprites and 24 stage scenes from the generated originals.

Inputs (NOT in the repo, never copied into it): the 48 original PNGs of layermaxxing-raster-7.9.14/style-e-preview/raw/
(<building>-<stage>-exterior.png 1254 x 1254 on flat #FF00FF, <building>-<stage>-detail.png 1024 x 1536) and their
generation receipts (style-e-preview/receipts/). Outputs (in the repo): only the optimised WebP assets, the generated
Kotlin table of the sprite frames and the provenance record:

  android/app/src/main/res/drawable-nodpi/e_<b>_<s>.webp        island sprite, square canvas, foot at the bottom middle
  android/app/src/main/res/drawable-nodpi/e_<b>_<s>_scene.webp  stage scene 900 x 1350
  android/app/src/main/java/at/gregor/layermaxxing/StyleEArt.kt  frame of every sprite (generated)
  docs/style-e-assets.json                                       provenance, sizes, decode budgets (generated)

Exterior cut-out (edge-connected chroma key, no magenta seams, no motif cut away):
  1. magenta-ness m = min(R, B) - G (pure #FF00FF = 255, natural wood/stone/leaves <= ~20);
  2. the background is every pixel with m >= LOOSE that is CONNECTED TO THE CANVAS EDGE (flood fill from the border), plus
     enclosed pockets of practically pure magenta (m >= STRICT, any size: the gaps between leaves, posts and railings). Paint
     inside the object that only leans to magenta is never removed, because it is not connected to the edge;
  3. the 3 px band of the object along the background gets its alpha from the remaining spill (a = 1 - m / 255, the key
     colour mixed in at the anti-aliased edge) and is de-spilled (the common excess of R and B over G is removed), so no
     pink fringe remains; band pixels with a < .2 are dropped (halo);
  4. the opaque body is trimmed to its bounding box.

Island frame (the grid_version 6 ground contract is kept EXACTLY, server/app/geometry/island_ground.json is not changed):
  the renderer draws a sprite in its box of `size` x island width (bottom centre on the spot, the foot). The ground
  rectangle of every building x stage x land stage was measured by server/tools/island_ground.py from the plate of the
  sprite (the lowest 22 % band of the opaque body, its 6..94 % width, depth = half that width). The new picture is
  scaled (k) and shifted (dx) inside a canvas S times the box so that THE SAME measurement on the new picture yields
  the SAME rectangle on all 6 land stages; k is the middle of the valid range (robust against resampling). S >= 1 leaves
  room for crowns and roofs above the box (overhang is wanted; only the ground is protected). The result of the check
  is printed and stored; a key without a valid k is an error.

Scenes: Lanczos to 900 x 1350 (exact 2:3 of the 1024 x 1536 original), WebP q 84.

Usage: python3 scripts/style_e_assets.py --raw <dir of the 48 PNGs> [--receipts <dir>] [--check]
       (needs only Pillow with WebP; not an app or server dependency)
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res/drawable-nodpi"
KT = ROOT / "android/app/src/main/java/at/gregor/layermaxxing/StyleEArt.kt"
DOC = ROOT / "docs/style-e-assets.json"
GROUND = ROOT / "server/app/geometry/island_ground.json"

BUILDINGS = ["HOUSE", "POST", "LIGHTHOUSE", "LIBRARY", "CAMPFIRE", "HARBOUR"]
RAW_NAME = {"HOUSE": "house", "POST": "post", "LIGHTHOUSE": "lighthouse", "LIBRARY": "library", "CAMPFIRE": "campfire", "HARBOUR": "harbour"}

LOOSE = 70         # magenta-ness of the edge-connected background (anti-aliased key colour included)
STRICT = 160       # practically pure key colour: background wherever it is (enclosed gaps too)
SPILL = 24         # key-tinted: an anti-aliased mix of key and object, alpha from the spill
FLOOR = 8          # magenta-ness natural paint may have (no transparency below it)
BAND = 3           # px of object edge that gets alpha from spill and is de-spilled
CANVAS = 512       # px side of the island sprite canvas for S = 1 (the box); grows with S up to CANVAS_MAX
CANVAS_MAX = 704   # so the box itself keeps >= ~400 px when a crown or pier reaches far over it
SCENE = (900, 1350)
SCENE_Q = 72       # measured: q84 7.6 MB for 24 scenes, q72 ~5.4 MB, no visible loss at 2x zoom
SPRITE_Q = 90

# server/tools/island_ground.py + island_masks.py, the measurement of the version 6 contract (copied, not imported:
# those need numpy/opencv)
ASPECT = 1367 / 1116
LAND_SCALE = (.46, .58, .70, .82, .92, 1.0)
N = 720
ALPHA = 128
SPECK = 24
PLATE_BAND = .22
COVER = .35
PIER_COLS = 2


# ---------------------------------------------------------------- cut-out

def magenta(img: Image.Image) -> Image.Image:
    r, g, b = img.convert("RGB").split()
    return ImageChops.subtract(ImageChops.darker(r, b), g)


def cut_out(src: Image.Image) -> Image.Image:
    rgb = src.convert("RGB")
    w, h = rgb.size
    m = magenta(rgb)
    loose = m.point(lambda v: 255 if v >= LOOSE else 0)
    # flood fill the edge-connected key colour with 128 (only from border pixels that are key colour)
    fill = loose.copy()
    px = fill.load()
    for x in range(w):
        for y in (0, h - 1):
            if px[x, y] == 255:
                ImageDraw.floodfill(fill, (x, y), 128)
    for y in range(h):
        for x in (0, w - 1):
            if px[x, y] == 255:
                ImageDraw.floodfill(fill, (x, y), 128)
    bg = fill.point(lambda v: 255 if v == 128 else 0)
    # enclosed key colour (gaps between leaves, posts and railings): practically pure key colour is background at any
    # size (the briefs forbid magenta in every object); key-tinted pixels (m >= SPILL) are anti-aliased mixes of
    # key and object and get their alpha from the spill below. Measured on the 24 originals: inside the objects the
    # magenta-ness is either >= 160 (key gaps) or a thin blend ramp; natural paint stays below ~10.
    bg = ImageChops.lighter(bg, m.point(lambda v: 255 if v >= STRICT else 0))
    keyish = ImageChops.lighter(bg, m.point(lambda v: 255 if v >= SPILL else 0))
    # band = object pixels within BAND px of the key colour: alpha from spill, de-spilled
    near = keyish.filter(ImageFilter.MaxFilter(2 * BAND + 1))
    band = ImageChops.subtract(near, bg)
    out = rgb.convert("RGBA")
    op = out.load(); bp = bg.load(); np_ = band.load(); mp = m.load()
    for y in range(h):
        for x in range(w):
            if bp[x, y]:
                op[x, y] = (0, 0, 0, 0)
            elif np_[x, y]:
                r, g, b, _ = op[x, y]
                e = mp[x, y]
                a = min(1.0, max(0.0, 1.0 - (e - FLOOR) / (255.0 - FLOOR)))
                if a < .2:
                    op[x, y] = (0, 0, 0, 0)
                    continue
                # un-mix the key colour (C = a F + (1 - a) K, K = (255, 0, 255)), then remove remaining spill
                fr = (r - (1 - a) * 255) / a; fg = g / a; fb = (b - (1 - a) * 255) / a
                fr, fg, fb = (min(255.0, max(0.0, v)) for v in (fr, fg, fb))
                ex = min(fr, fb) - fg
                if ex > 0:
                    fr -= ex; fb -= ex
                op[x, y] = (round(fr), round(fg), round(fb), round(a * 255))
    return out.crop(out.getchannel("A").getbbox())


# ---------------------------------------------------------------- the version 6 measurement (pure Python)

def opaque_body(img: Image.Image) -> list[tuple[int, int]]:
    """Opaque pixels (alpha >= ALPHA) without specks < SPECK px, like island_masks.silhouette (any picture size)."""
    a = img.getchannel("A").point(lambda v: 255 if v >= ALPHA else 0)
    W, H = a.size
    px = a.load()
    seen = bytearray(W * H)
    keep = []
    for y in range(H):
        for x in range(W):
            if px[x, y] and not seen[y * W + x]:
                comp, stack = [], [(x, y)]
                seen[y * W + x] = 1
                while stack:
                    cx, cy = stack.pop()
                    comp.append((cx, cy))
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < W and 0 <= ny < H and px[nx, ny] and not seen[ny * W + nx]:
                            seen[ny * W + nx] = 1
                            stack.append((nx, ny))
                if len(comp) >= SPECK:
                    keep.extend(comp)
    return keep


def percentile(values: list[float], q: float) -> float:
    """numpy.percentile (linear interpolation)."""
    v = sorted(values)
    k = (len(v) - 1) * q / 100
    lo = math.floor(k); hi = math.ceil(k)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def plate(pixels, flat=False, centre=N / 2):
    ys = [p[1] for p in pixels]
    top, bottom = min(ys), max(ys)
    lim = bottom - (1.0 if flat else PLATE_BAND) * (bottom - top + 1)
    xs = [p[0] for p in pixels if p[1] >= lim]
    left, right = percentile(xs, 6), percentile(xs, 94)
    depth = (bottom - top + 1) * .8 if flat else .5 * (right - left)
    return left - centre, right - centre, depth


def span(lo, hi):
    ks = [k for k in range(math.floor(lo) - 1, math.ceil(hi) + 2) if min(hi, k + .5) - max(lo, k - .5) >= COVER]
    return (ks[0], ks[-1]) if ks else (0, 0)


def rect(pl, size, land, cols_cap=None):
    s = LAND_SCALE[land]
    cell_x = N * (s / 20) / size
    cell_y = N * (s / 20) / (size * ASPECT)
    x0, x1 = span(pl[0] / cell_x, pl[1] / cell_x)
    x0, x1 = min(x0, 0), max(x1, 0)
    if cols_cap and x1 - x0 + 1 > cols_cap:
        mid = (pl[0] + pl[1]) / 2 / cell_x
        x0 = max(x0, min(0, math.floor(mid + .5) - (cols_cap - 1) + (cols_cap - 1) // 2 if mid >= 0 else math.ceil(mid - .5) - (cols_cap - 1) // 2))
        x1 = x0 + cols_cap - 1
        if x1 < 0:
            x0, x1 = -(cols_cap - 1), 0
    y0, _ = span(-pl[2] / cell_y, 0)
    return [x0, min(y0, 0), x1, 0]


def ground_of(pl, size, land, harbour, stage):
    g = rect(pl, size, land, PIER_COLS if harbour else None)
    if harbour and stage > 0:
        return {"ground": [g[0], min(g[1], -1), g[2], -1], "sea": [g[0], 0, g[2], 0]}
    return {"ground": g}


# ---------------------------------------------------------------- the frame of one sprite

def place(art: Image.Image, k: float, dx: float, scale: float, side: int) -> Image.Image:
    """The art drawn into a box of [side] px as the island draws it with canvas factor [scale]: the canvas is
    side * scale, its bottom middle on the foot; the art (already cropped) k times the box wide, foot at the bottom,
    shifted dx box widths. Returns the CANVAS (side * scale square)."""
    c = round(side * scale)
    canvas = Image.new("RGBA", (c, c), (0, 0, 0, 0))
    w = max(1, round(k * side))
    h = max(1, round(art.height * w / art.width))
    a = art.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
    # the canvas is empty: paste (clips at the canvas edge) is the same as compositing
    canvas.paste(a, (round(c / 2 + dx * side - w / 2), c - h), a)
    return canvas


def measure_canvas(canvas: Image.Image, scale: float, flat: bool):
    """The version 6 plate of the whole canvas in box px at measuring resolution (box = N px, foot = canvas bottom middle).
    The whole picture counts (a crown above the box belongs to the body like it did in the generator's composite)."""
    side = round(N * scale)
    img = canvas.convert("RGBa").resize((side, side), Image.LANCZOS).convert("RGBA")
    return plate(opaque_body(img), flat, centre=side / 2)


KMAX = 1.0      # the picture is at most as wide as the box (legacy sprites filled 75..95 % of it)
PIER_K = 1.0    # harbour 1..3 (pier anchor): the picture as wide as the box
MARGIN = .03    # distance (in box widths of picture width) kept from the end of the valid range


def fit_frame(key: str, art: Image.Image, contract: dict) -> dict:
    harbour = key.startswith("building:HARBOUR:")
    stage = int(key.rsplit(":", 1)[1])
    lands = contract["lands"]
    want = [{k: v for k, v in l.items() if k in ("ground", "sea")} for l in lands]
    flat = harbour and stage == 0
    # the plate of the picture alone, measured at N px picture width (box px at k = 1), then scaled by k
    pw = N; ph = round(art.height * pw / art.width)
    probe = art.convert("RGBa").resize((pw, ph), Image.LANCZOS).convert("RGBA")
    base = plate(opaque_body(probe), flat, centre=pw / 2)

    def plate_at(k, dx):
        l, r, d = base
        return l * k + dx * N, r * k + dx * N, d * k

    def ok(k, dx):
        pl = plate_at(k, dx)
        return all(ground_of(pl, lands[L]["size"], L, harbour, stage) == want[L] for L in range(6))

    def search(kstep, dxs):
        best = None
        n = round(2.0 / kstep)
        for dx in dxs:
            valid = [i * kstep for i in range(round(.2 / kstep), n) if ok(i * kstep, dx)]
            if not valid:
                continue
            runs, run = [], [valid[0]]
            for v in valid[1:]:
                if abs(v - run[-1] - kstep) < 1e-9:
                    run.append(v)
                else:
                    runs.append(run); run = [v]
            runs.append(run)
            for r in runs:
                m = min(MARGIN, (r[-1] - r[0]) * .3)  # a short valid range keeps its middle part
                lo, hi = r[0] + m, r[-1] - m
                if hi < lo:
                    continue
                # as close to KMAX as the contract allows (a sprite whose valid range starts above it gets the start)
                k = min(hi, max(lo, KMAX))
                cand = (-round(abs(k - KMAX), 3), -abs(dx), k, dx, round((r[-1] - r[0]) / kstep) + 1)
                if best is None or cand[:2] > best[:2]:
                    best = cand
        return best

    best = search(.005, [i / 100 for i in range(-60, 61)])
    # a narrow window (the pier of harbour 1 changes sides between land 3 and 4 in the contract): finer steps
    if best is None:
        best = search(.0005, [i / 400 for i in range(-240, 241)])
    if best is None and harbour and stage > 0:
        # PIER ANCHOR (harbour 1..3 only): the old jetty sprites had a plate band 0.4..0.7 box widths wide, the new
        # jetties run diagonally from the beach and their lowest band is only the pier head (about a fifth of the
        # picture): no scale reproduces those widths with a sane picture. The contract itself is NOT changed (the JSON
        # is the server's rule); the picture is anchored to it instead: the box's width, its pier head (the middle of
        # the lowest band) exactly over the spot column (the sea row where the berth [0, 1] lies in front), the
        # head's lowest pixel on the spot row. The painted pebble strip behind the pier reaches over beach cells only
        # harbours use (zone B) and the water; checked in place_pier below.
        l, r, _ = base
        k = PIER_K
        dx = round(-(l + r) / 2 * k / N, 4)
        return {"k": k, "dx": dx, "plate": plate_at(k, dx), "valid_run": 0, "mode": "pier-anchor"}
    if best is None:
        raise SystemExit(f"{key}: no scale reproduces the version 6 ground on all land stages")
    k, dx = best[2], best[3]
    return {"k": round(k, 4), "dx": dx, "plate": plate_at(k, dx), "valid_run": best[4], "mode": "same-measurement"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True, type=Path)
    ap.add_argument("--receipts", type=Path)
    ap.add_argument("--only", default=None, help="comma separated <b>-<s> ids (debug)")
    ap.add_argument("--preview", type=Path, help="write cut-out previews on a contrasting background here")
    args = ap.parse_args()
    contract = json.loads(GROUND.read_text())["pieces"]
    only = set(args.only.split(",")) if args.only else None
    doc = {"generator": "scripts/style_e_assets.py", "raw_dir_not_in_repo": str(args.raw), "sprites": {}, "scenes": {}}
    frames = {}
    for b in BUILDINGS:
        for s in range(4):
            rid = f"{RAW_NAME[b]}-{s}"
            if only and rid not in only:
                continue
            ext = args.raw / f"{rid}-exterior.png"
            det = args.raw / f"{rid}-detail.png"
            key = f"building:{b}:{s}"
            art = cut_out(Image.open(ext))
            frame = fit_frame(key, art, contract[key])
            k, dx = frame["k"], frame["dx"]
            # canvas factor: enough room for the art above, left and right of the box (overhang), at least 1
            w = k; h = k * art.height / art.width
            scale = max(1.0, h * 1.02, 2 * (abs(dx) + w / 2) * 1.02)
            side_px = min(CANVAS_MAX, max(CANVAS, round(400 * scale)))
            canvas = place(art, k, dx, scale, round(side_px / scale))
            if canvas.width != side_px:
                canvas = canvas.resize((side_px, side_px), Image.LANCZOS)
            # re-check on the final canvas exactly as the app sees it (box at N px)
            pl = measure_canvas(canvas, scale, b == "HARBOUR" and s == 0)
            lands = contract[key]["lands"]
            got = [ground_of(pl, lands[L]["size"], L, b == "HARBOUR", s) for L in range(6)]
            want = [{kk: v for kk, v in l.items() if kk in ("ground", "sea")} for l in lands]
            if frame["mode"] == "same-measurement" and got != want:
                raise SystemExit(f"{key}: final canvas measures {got}, contract {want}")
            if frame["mode"] == "pier-anchor":
                head = (pl[0] + pl[1]) / 2
                for L in range(6):
                    cell = N * (LAND_SCALE[L] / 20) / lands[L]["size"]
                    if abs(head / cell) > .25:
                        raise SystemExit(f"{key}: pier head {head / cell:.2f} cells off the spot column on land {L}")
            name = f"e_{RAW_NAME[b]}_{s}"
            out = RES / f"{name}.webp"
            canvas.save(out, "WEBP", quality=SPRITE_Q, method=6, exact=False)
            vb = canvas.getchannel("A").getbbox()
            c = side_px
            # visible bounds relative to the BOX (left, top, right, bottom); the box is the bottom-middle 1/scale
            side = c / scale
            bl = c / 2 - side / 2; bt = c - side
            bounds = [round((vb[0] - bl) / side, 4), round((vb[1] - bt) / side, 4), round((vb[2] - bl) / side, 4), round((vb[3] - bt) / side, 4)]
            frames[key] = {"res": name, "scale": round(scale, 4), "bounds": bounds}
            doc["sprites"][name] = {
                "key": key, "source": ext.name, "source_sha256": hashlib.sha256(ext.read_bytes()).hexdigest(),
                "cutout_px": list(art.size), "canvas_px": [c, c], "scale_of_box": round(scale, 4), "art_width_of_box": k,
                "shift_of_box": dx, "valid_scale_run": frame["valid_run"], "fit_mode": frame["mode"], "visible_bounds_of_box": bounds,
                "bytes": out.stat().st_size, "decoded_argb_bytes": c * c * 4, "ground_v6": want,
            }
            sc = Image.open(det).convert("RGB").resize(SCENE, Image.LANCZOS)
            sname = f"e_{RAW_NAME[b]}_{s}_scene"
            sout = RES / f"{sname}.webp"
            sc.save(sout, "WEBP", quality=SCENE_Q, method=6)
            doc["scenes"][sname] = {"source": det.name, "source_sha256": hashlib.sha256(det.read_bytes()).hexdigest(),
                                   "px": list(SCENE), "bytes": sout.stat().st_size, "decoded_argb_bytes": SCENE[0] * SCENE[1] * 4,
                                   "decoded_rgb565_bytes": SCENE[0] * SCENE[1] * 2}
            if args.receipts and (args.receipts / f"{rid}-exterior.json").exists():
                for kind, d in (("exterior", doc["sprites"][name]), ("detail", doc["scenes"][sname])):
                    rc = json.loads((args.receipts / f"{rid}-{kind}.json").read_text())
                    d["receipt_id"] = rc.get("id"); d["prompt_sha256"] = rc.get("prompt_sha256")
            if args.preview:
                args.preview.mkdir(parents=True, exist_ok=True)
                for bgc in ((40, 40, 40), (235, 235, 225), (60, 140, 70)):
                    pv = Image.new("RGBA", canvas.size, bgc + (255,)); pv.alpha_composite(canvas)
                    pv.convert("RGB").save(args.preview / f"{name}-{bgc[0]}.png")
            print(f"{key}: k={k} dx={dx} scale={scale:.3f} run={frame['valid_run']} bounds={bounds} "
                  f"{out.stat().st_size} B, scene {sout.stat().st_size} B", flush=True)
    if only:
        return 0
    write_kotlin(frames)
    doc["totals"] = {
        "sprite_bytes": sum(v["bytes"] for v in doc["sprites"].values()),
        "scene_bytes": sum(v["bytes"] for v in doc["scenes"].values()),
        "sprite_decoded_max": max(v["decoded_argb_bytes"] for v in doc["sprites"].values()),
        "sprite_decoded_all_24": sum(v["decoded_argb_bytes"] for v in doc["sprites"].values()),
        "scene_decoded_each": SCENE[0] * SCENE[1] * 4,
    }
    DOC.write_text(json.dumps(doc, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
    print("totals", doc["totals"])
    return 0


def write_kotlin(frames: dict):
    lines = []
    for b in BUILDINGS:
        row = []
        for s in range(4):
            f = frames[f"building:{b}:{s}"]
            bl, bt, br, bb = f["bounds"]
            row.append(f"Frame(R.drawable.{f['res']}, {f['scale']}f, floatArrayOf({bl}f, {bt}f, {br}f, {bb}f))")
        lines.append(f"        IsleBuilding.{b} to listOf(\n            " + ",\n            ".join(row) + ",\n        ),")
    KT.write_text(
        "package at.gregor.layermaxxing\n\n"
        "/**\n"
        " * GENERATED by scripts/style_e_assets.py (do not edit by hand): the 7.9.14 style E island sprites.\n"
        " * [Frame.scale] = canvas side / sprite box side: the canvas is drawn [scale] times the box, its bottom middle on the\n"
        " * foot (crowns and roofs may reach over the box; only the ground is protected, grid_version 6). The picture inside was\n"
        " * placed so that the version 6 plate measurement of server/tools/island_ground.py yields exactly the committed ground\n"
        " * rectangle on every land stage. [Frame.bounds] = visible part (left, top, right, bottom) as fractions of the BOX.\n"
        " */\n"
        "internal object StyleEArt {\n"
        "    class Frame(val res: Int, val scale: Float, val bounds: FloatArray)\n"
        "    private val table: Map<IsleBuilding, List<Frame>> = mapOf(\n"
        + "\n".join(lines) + "\n"
        "    )\n"
        "    fun of(b: IsleBuilding, stage: Int): Frame = table.getValue(b)[stage.coerceIn(0, 3)]\n"
        "    fun byRes(res: Int): Frame? = table.values.flatten().firstOrNull { it.res == res }\n"
        "}\n")


if __name__ == "__main__":
    sys.exit(main())
