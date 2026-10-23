#!/usr/bin/env python3
"""Letter tree, native scene (22.10.2026): the material layers the app animates itself, cut from the delivered N1–N4
(briefbaum-native-20261022/IMAGE-REQUESTS.json) – keyed, cropped, uniformly scaled; never stretched, never rotated.

Layers (android/app/src/main/res/drawable-nodpi, WebP with alpha):
 - e_post_0_lay_body.webp      N2 below its fold line: the open, empty envelope (inside of the back panel + the pocket);
                               the app draws it whole (back), the letter, then the same picture clipped to the pocket
                               (one picture: back and pocket can never be out of register);
 - e_post_0_lay_flap_in.webp   N2 above its fold line: the flap folded up, its inner face (base = the body's width);
 - e_post_0_lay_flap_out.webp  N1's closed flap (outer face), cut along its measured outline, uniformly scaled so its
                               base (N1's body width) is the body's width – registered on the fold line;
 - e_post_0_lay_seal.webp      N4 keyed, centred on a square canvas (not resized unevenly);
 - e_post_0_lay_sheet.webp     N3 keyed (deckled edge kept), uniformly scaled.
The measured geometry (body aspect, flap lengths, pocket opening, the sheet's aspect and folds) goes to
docs/letter-layer-assets.json with the source SHA-256, the crop rectangles and the scale factors; the app's constants
(EnvelopeArt) are these numbers. A registration proof (closed / open composites) goes to --proof.

Usage: python3 scripts/letter_layers.py --generated <briefbaum-native-20261022/generated> [--proof <dir>]   (Pillow only)
"""
import argparse, hashlib, json, sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res/drawable-nodpi"
DOC = ROOT / "docs/letter-layer-assets.json"
sys.path.insert(0, str(ROOT / "scripts"))
import style_e_assets as E  # noqa: E402  (the style E key: thresholds and de-spill)

# ---- measured on the originals (pixels; see the registration proof)
N2_FOLD_Y = 337                      # N2: first row at the body's full width (the flap's fold line)
N2_BODY = (180, N2_FOLD_Y, 1331, 955)  # N2 body: x 180..1330, y 337..954
N2_FLAP = (180, 30, 1331, N2_FOLD_Y)   # N2 flap above the fold (tip at y 33)
N1_BODY = (104, 146, 1409, 879)        # N1 body: x 104..1408, y 146..878
# N1's closed flap outline (N1 px): fold corners, shoulders, rounded tip – traced on the picture
N1_FLAP = [(104, 146), (104, 148), (205, 312), (753, 643), (1302, 312), (1408, 148), (1408, 146)]
N1_FLAP_BOTTOM = 650
# N2's pocket opening (N2 px): the side flaps' upper edges and the bottom flap's notch, traced on the picture
N2_POCKET = [(180, 344), (560, 564), (707, 621), (800, 621), (940, 564), (1330, 344)]
# N3: the two folds of the sheet (it was folded in three), y in N3 px
N3_FOLDS = (476, 930)
BODY_W = 900                          # app width of the body layer (px)


def key(src: Image.Image) -> Image.Image:
    """style_e_assets.cut_out without its final crop: the same edge-connected key, spill alpha and de-spill, so the
    keyed picture keeps the original's pixel coordinates (needed for the registration)."""
    rgb = src.convert("RGB"); w, h = rgb.size
    m = E.magenta(rgb)
    loose = m.point(lambda v: 255 if v >= E.LOOSE else 0)
    fill = loose.copy(); px = fill.load()
    for x in range(w):
        for y in (0, h - 1):
            if px[x, y] == 255: ImageDraw.floodfill(fill, (x, y), 128)
    for y in range(h):
        for x in (0, w - 1):
            if px[x, y] == 255: ImageDraw.floodfill(fill, (x, y), 128)
    bg = fill.point(lambda v: 255 if v == 128 else 0)
    bg = ImageChops.lighter(bg, m.point(lambda v: 255 if v >= E.STRICT else 0))
    keyish = ImageChops.lighter(bg, m.point(lambda v: 255 if v >= E.SPILL else 0))
    near = keyish.filter(ImageFilter.MaxFilter(2 * E.BAND + 1))
    band = ImageChops.subtract(near, bg)
    out = rgb.convert("RGBA"); op = out.load(); bp = bg.load(); np_ = band.load(); mp = m.load()
    for y in range(h):
        for x in range(w):
            if bp[x, y]: op[x, y] = (0, 0, 0, 0)
            elif np_[x, y]:
                r, g, b, _ = op[x, y]; e = mp[x, y]
                a = min(1.0, max(0.0, 1.0 - (e - E.FLOOR) / (255.0 - E.FLOOR)))
                if a < .2: op[x, y] = (0, 0, 0, 0); continue
                fr = (r - (1 - a) * 255) / a; fg = g / a; fb = (b - (1 - a) * 255) / a
                fr, fg, fb = (min(255.0, max(0.0, v)) for v in (fr, fg, fb))
                ex = min(fr, fb) - fg
                if ex > 0: fr -= ex; fb -= ex
                op[x, y] = (round(fr), round(fg), round(fb), round(a * 255))
    return out


def polygon_mask(size, pts, offset, ss=4):
    """An anti-aliased polygon mask (drawn 4× larger, scaled down)."""
    big = Image.new("L", (size[0] * ss, size[1] * ss), 0)
    ImageDraw.Draw(big).polygon([((x - offset[0]) * ss, (y - offset[1]) * ss) for x, y in pts], fill=255)
    return big.resize(size, Image.LANCZOS)


def scaled(img: Image.Image, f: float) -> Image.Image:
    return img.resize((max(1, round(img.width * f)), max(1, round(img.height * f))), Image.LANCZOS)


def save(img: Image.Image, name: str, q: int = 88) -> Path:
    out = RES / f"{name}.webp"; img.save(out, "WEBP", quality=q, method=6, exact=False); return out


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--generated", required=True, type=Path); ap.add_argument("--proof", type=Path)
    a = ap.parse_args()
    ver = {x["id"]: x for x in json.loads((a.generated / "HERMES-VERIFICATION.json").read_text())["assets"]}
    src, sha = {}, {}
    for rid in ("N1-envelope-landscape-back-sealed", "N2-envelope-landscape-back-open-empty", "N3-letter-sheet-portrait", "N4-wax-seal-intact"):
        png = a.generated / f"{rid}.png"; rec = json.loads(png.with_suffix(".json").read_text())
        h = hashlib.sha256(png.read_bytes()).hexdigest()
        if rec.get("success") is not True or ver[rid]["sha256"] != h:
            raise SystemExit(f"not usable (receipt / verification): {rid}")
        src[rid[:2]] = Image.open(png); sha[rid[:2]] = (png.name, h)
    doc = {"generator": "scripts/letter_layers.py", "interim": False, "assets": {}, "geometry": {}}

    def record(name, sid, box, transform, out, extra=None):
        doc["assets"][name] = {"source": sha[sid][0], "source_sha256": sha[sid][1], "crop_px": list(box), "transform": transform,
                               "px": list(Image.open(out).size), "bytes": out.stat().st_size, **(extra or {})}
        print(name, Image.open(out).size, out.stat().st_size)

    # ---- N2: body (back + pocket) and the flap's inner face, one picture, one scale
    n2 = key(src["N2"])
    body = n2.crop(N2_BODY)
    f2 = BODY_W / body.width
    body_out = scaled(body, f2)
    record("e_post_0_lay_body", "N2", N2_BODY, f"keyed, uniform scale {f2:.5f}", save(body_out, "e_post_0_lay_body"))
    flap_in = n2.crop(N2_FLAP)
    flap_in_out = scaled(flap_in, f2)
    record("e_post_0_lay_flap_in", "N2", N2_FLAP, f"keyed, uniform scale {f2:.5f} (same as the body)", save(flap_in_out, "e_post_0_lay_flap_in"))
    bw, bh = body.size
    # ---- N1: the closed flap's outer face, cut on its outline, scaled so its base is the body's width
    n1 = key(src["N1"])
    fbox = (N1_BODY[0], N1_BODY[1], N1_BODY[2], N1_FLAP_BOTTOM)
    flap = n1.crop(fbox)
    flap.putalpha(ImageChops.multiply(flap.getchannel("A"), polygon_mask(flap.size, N1_FLAP, fbox[:2])))
    f1 = bw / (N1_BODY[2] - N1_BODY[0])
    flap_out = scaled(scaled(flap, f1), f2)
    record("e_post_0_lay_flap_out", "N1", fbox, f"keyed, cut on the traced outline, uniform scale {f1:.5f} (N1 body width -> N2 body width) then {f2:.5f}",
           save(flap_out, "e_post_0_lay_flap_out"), {"outline_px": N1_FLAP})
    # ---- N4: the seal on a square canvas
    n4 = key(src["N4"]); bb = n4.getchannel("A").getbbox(); seal = n4.crop(bb)
    side = max(seal.size); sq = Image.new("RGBA", (side, side)); sq.paste(seal, ((side - seal.width) // 2, (side - seal.height) // 2))
    record("e_post_0_lay_seal", "N4", bb, f"keyed, centred on a square {side}px canvas, uniform scale {256 / side:.5f}", save(scaled(sq, 256 / side), "e_post_0_lay_seal", 90))
    # ---- N3: the sheet with its deckled edge
    n3 = key(src["N3"]); sb = n3.getchannel("A").getbbox(); sheet = n3.crop(sb)
    f3 = 720 / sheet.width
    record("e_post_0_lay_sheet", "N3", sb, f"keyed, uniform scale {f3:.5f}", save(scaled(sheet, f3), "e_post_0_lay_sheet", 84))

    # ---- the geometry the app uses (fractions)
    pocket = [((x - N2_BODY[0]) / bw, (y - N2_BODY[1]) / bh) for x, y in N2_POCKET]
    tip_out = (N1_FLAP[3][1] - N1_BODY[1]) * f1 / bh
    doc["geometry"] = {
        "body_aspect": round(bw / bh, 4), "requested_body_aspect": 1.45,
        "n1_body_aspect": round((N1_BODY[2] - N1_BODY[0]) / (N1_BODY[3] - N1_BODY[1]), 4),
        "flap_out_height": round(flap.height * f1 / bh, 4), "flap_out_tip": round(tip_out, 4),
        "flap_in_height": round(flap_in.height / bh, 4), "flap_in_tip": round((N2_FOLD_Y - 33) / bh, 4),
        "pocket_opening": [[round(x, 4), round(y, 4)] for x, y in pocket],
        "sheet_aspect": round(sheet.height / sheet.width, 4), "requested_sheet_aspect": 1.41,
        "sheet_folds": [round((y - sb[1]) / sheet.height, 4) for y in N3_FOLDS],
        "note": "N1/N2/N3 deviate from the requested 1.45 / 1.41 (see RESULT-MATERIAL.md): used as delivered, nothing stretched.",
    }
    DOC.write_text(json.dumps(doc, indent=1, ensure_ascii=False) + "\n")
    print(json.dumps(doc["geometry"], indent=1))
    if a.proof: proof(a.proof, body_out, flap_in_out, flap_out, pocket)


def proof(out: Path, body, flap_in, flap_out, pocket):
    """Registration proof: closed (body + outer flap) and open (inner flap above, body) with the pocket line and the fold."""
    out.mkdir(parents=True, exist_ok=True)
    w, h = body.size; top = flap_in.height
    for name, open_ in (("registration-closed", False), ("registration-open", True)):
        c = Image.new("RGBA", (w + 40, h + top + 40), (58, 70, 52, 255))
        c.alpha_composite(body, (20, 20 + top))
        if open_: c.alpha_composite(flap_in, (20, 20 + top - flap_in.height))
        else: c.alpha_composite(flap_out, (20, 20 + top))
        d = ImageDraw.Draw(c)
        d.line([(20, 20 + top), (20 + w, 20 + top)], fill=(0, 255, 255, 255), width=1)
        d.line([(20 + x * w, 20 + top + y * h) for x, y in pocket], fill=(255, 220, 0, 255), width=2)
        c.convert("RGB").save(out / f"{name}.png"); print("proof", out / f"{name}.png")


if __name__ == "__main__":
    main()
