#!/usr/bin/env python3
"""7.9.13: rework the painted island terrain (android/app/src/main/res/drawable-nodpi/terrain_home.webp).

The 7.9.12 terrain carried a white star-shaped plaza with six spokes painted into the meadow (IslandPlans.paths was
already empty, the star was part of the texture), and its only sandy shore was a thin rim under the coast row of the
build grid. This script rebuilds the asset from the 7.9.12 original (git object BASE), reproducibly:

1. the star plaza and its dried-grass fringes are found by colour (pale, unsaturated pixels inside the meadow) and filled
   with real painted meadow from the same picture: block-wise texture copies from nearby grass (no flat colour, no
   blur), lightly colour-matched to the surrounding lawn and feathered;
2. the southern cove becomes a real beach: sand from the painted sand island (terrain_sand.webp, same base), colour
   matched to this island's own shore sand, composited over the vegetation inside a soft, noisy shoreline; rocks and
   water are never painted over;
3. a few pebbles where the pier root stands, one rock right at the water line in front of it and one submerged rock in
   the bay are replaced by the painted sand / water right next to them (explicit, reviewed offsets). All other rocks
   in the water stay: they are part of the coast geometry ships must avoid (island_ground.py).

Usage: python3 server/tools/terrain_coast.py   (needs Pillow, numpy, opencv; not a server dependency)
"""
from __future__ import annotations

import io
import subprocess
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = "android/app/src/main/res/drawable-nodpi"
BASE = "fa0e0f6"  # 7.9.12, the last commit with the original painted terrain


def original(name):
    raw = subprocess.run(["git", "show", f"{BASE}:{RES}/{name}.webp"], cwd=ROOT, capture_output=True, check=True).stdout
    return np.array(Image.open(io.BytesIO(raw)).convert("RGBA"))


def hsv(rgb):
    h = cv2.cvtColor(np.ascontiguousarray(rgb), cv2.COLOR_RGB2HSV).astype(float)
    return h[:, :, 1] / 255, h[:, :, 2] / 255


def star_mask(im):
    H, W = im.shape[:2]
    S, V = hsv(im[:, :, :3])
    yy, xx = np.mgrid[0:H, 0:W]
    inner = ((xx / W - .5) / .43) ** 2 + ((yy / H - .46) / .38) ** 2 <= 1
    path = ((S < .33) & (V > .56) & inner).astype(np.uint8) * 255
    path = cv2.morphologyEx(path, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    path = cv2.morphologyEx(path, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(path)
    keep = np.zeros_like(path)
    for k in range(1, n):
        if st[k, 4] > 1500:
            keep[lab == k] = 255
    # the fringes of dried grass along the old paths go too (else they draw the star as a ghost)
    keep = cv2.dilate(keep, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (41, 41)))
    lim = (((xx / W - .5) / .41) ** 2 + ((yy / H - .45) / .335) ** 2 <= 1) & (yy < 668)  # never the shore
    return np.where(lim, keep, 0)


def fill_meadow(im, M):
    H, W = im.shape[:2]
    rgb = im[:, :, :3].astype(np.float32)
    S, _ = hsv(im[:, :, :3])
    yy, xx = np.mgrid[0:H, 0:W]
    inner = ((xx / W - .5) / .39) ** 2 + ((yy / H - .46) / .35) ** 2 <= 1
    valid = inner & ~(cv2.dilate(M, np.ones((9, 9), np.uint8)) > 0) & (S > .28)
    low = cv2.inpaint(cv2.resize(im[:, :, :3], (W // 4, H // 4)), cv2.resize(M, (W // 4, H // 4)), 9, cv2.INPAINT_TELEA)
    low = cv2.resize(low, (W, H)).astype(np.float32)
    rng = np.random.default_rng(13)
    B, PAD = 26, 12
    acc = np.zeros_like(rgb)
    wsum = np.zeros((H, W), np.float32)
    r = np.minimum(np.arange(B + 2 * PAD) + 1, np.arange(B + 2 * PAD)[::-1] + 1).astype(np.float32)
    win = np.clip(np.minimum.outer(r, r) / PAD, 0, 1) ** 1.5
    ii = cv2.integral(valid.astype(np.uint8))

    def all_valid(x0, y0, x1, y1):
        if x0 < 0 or y0 < 0 or x1 > W or y1 > H:
            return False
        return ii[y1, x1] - ii[y0, x1] - ii[y1, x0] + ii[y0, x0] == (x1 - x0) * (y1 - y0)

    for by in range(0, H, B):
        for bx in range(0, W, B):
            if not M[by:by + B, bx:bx + B].any():
                continue
            x0, y0, x1, y1 = bx - PAD, by - PAD, bx + B + PAD, by + B + PAD
            if x0 < 0 or y0 < 0 or x1 > W or y1 > H:
                continue
            want = low[by:by + B, bx:bx + B].reshape(-1, 3).mean(0)
            best = None
            for _ in range(500):
                ang = rng.uniform(0, 2 * np.pi)
                d = rng.uniform(50, 300)
                dx, dy = int(d * np.cos(ang)), int(d * np.sin(ang) * .8)
                if not all_valid(x0 + dx, y0 + dy, x1 + dx, y1 + dy):
                    continue
                src = rgb[y0 + dy:y1 + dy, x0 + dx:x1 + dx]
                sc = np.abs(src.reshape(-1, 3).mean(0) - want).sum() + rng.uniform(0, 6)
                if best is None or sc < best[0]:
                    best = (sc, dx, dy)
            if best is None:
                continue
            _, dx, dy = best
            src = rgb[y0 + dy:y1 + dy, x0 + dx:x1 + dx]
            src = src + (want - src.reshape(-1, 3).mean(0)) * .6
            acc[y0:y1, x0:x1] += src * win[:, :, None]
            wsum[y0:y1, x0:x1] += win
    fill = np.where(wsum[:, :, None] > 0, acc / np.maximum(wsum, 1e-6)[:, :, None], low)
    a = np.clip(cv2.GaussianBlur((M > 0).astype(np.float32), (0, 0), 4) * 1.6, 0, 1)
    out = im.copy()
    out[:, :, :3] = np.clip(rgb * (1 - a[:, :, None]) + fill * a[:, :, None], 0, 255).astype(np.uint8)
    return out


def copy_disc(img, x, y, r, dx, dy):
    R = r + 8
    yy, xx = np.mgrid[-R:R, -R:R]
    d = np.sqrt(xx ** 2 + yy ** 2)
    tgt = img[y - R:y + R, x - R:x + R].astype(np.float32)
    src = img[y + dy - R:y + dy + R, x + dx - R:x + dx + R].astype(np.float32)
    a = np.clip((R - d) / (R - r), 0, 1)[:, :, None]
    img[y - R:y + R, x - R:x + R] = (tgt * (1 - a) + src * a).astype(np.uint8)


def beach(im, sand):
    H, W = im.shape[:2]
    rgb = im[:, :, :3].copy()
    S, V = hsv(rgb)
    water = rgb[:, :, 2].astype(float) > rgb[:, :, 0].astype(float) + 8
    rock = (S < .20) & (V < .80) & ~water
    poly = np.array([(452, 700), (440, 672), (452, 656), (420, 650), (470, 640), (520, 636), (570, 640), (612, 652),
                     (640, 672), (652, 700), (652, 805), (452, 805)], np.int32)
    area = np.zeros((H, W), np.uint8)
    cv2.fillPoly(area, [poly], 255)
    rng = np.random.default_rng(7)
    noise = cv2.GaussianBlur(rng.normal(0, 1, (H, W)).astype(np.float32), (0, 0), 6)
    noise /= noise.std()
    dist = cv2.distanceTransform(area, cv2.DIST_L2, 5)
    edge = np.clip((dist - 3 + noise * 4) / 12, 0, 1) * (area > 0)
    # the cove's left corner: the sand reaches a little further into the surf, so the beach is three cells wide at
    # the water line too (the rowboat of a small island needs that)
    reach = np.zeros((H, W), np.uint8)
    cv2.fillPoly(reach, [np.array([(468, 734), (532, 732), (552, 768), (528, 790), (474, 784)], np.int32)], 255)
    reach = cv2.GaussianBlur(reach.astype(np.float32) / 255, (0, 0), 5)
    keep = ~water & ~cv2.dilate(rock.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)
    paint = np.maximum(edge * keep, reach * (area > 0))
    shore = (area > 0) & ~water & (S < .32) & (V > .70)
    ref = rgb[shore].reshape(-1, 3).mean(0) if shore.sum() > 200 else np.array([214, 198, 160.])
    src = sand + (ref - sand[area > 0].reshape(-1, 3).mean(0))
    a = cv2.GaussianBlur(paint.astype(np.float32), (0, 0), 2.2)
    out = rgb.astype(np.float32) * (1 - a[:, :, None]) + src * a[:, :, None]
    wet = cv2.dilate(water.astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool) & (paint > 0)
    out[wet] *= .93
    fin = np.ascontiguousarray(np.clip(out, 0, 255).astype(np.uint8))
    for (x, y, r, dx, dy) in [(575, 707, 10, -50, 0), (603, 733, 10, -50, 0), (516, 748, 11, 30, 0),
                              (613, 786, 18, -40, -21), (520, 825, 22, 70, 0)]:
        copy_disc(fin, x, y, r, dx, dy)
    res = im.copy()
    res[:, :, :3] = fin
    return res


def build():
    home = original("terrain_home")
    sand = original("terrain_sand")[:, :, :3].astype(np.float32)
    return beach(fill_meadow(home, star_mask(home)), sand)


def main():
    out = ROOT / RES / "terrain_home.webp"
    Image.fromarray(build()).save(out, "WEBP", quality=90, method=6)
    print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
