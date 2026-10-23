#!/usr/bin/env python3
"""Stage UI redesign assets (7.9.14, POST stage scenes): generated close-up / state images -> optimised WebP in the app.

Input (NOT in the repo): layermaxxing-raster-7.9.14/stage-ui-redesign/generated/<id>.png with its receipt <id>.json.
Only a receipt with success == true and an image of the receipt's size is used. Output: the WebP in
android/app/src/main/res/drawable-nodpi (900 x 1350, q 80, like the stage scenes) and docs/stage-ui-assets.json
(provenance: receipt id, prompt sha256, source sha256, bytes).

Usage: python3 scripts/stage_ui_assets.py --generated <dir>   (Pillow only)
"""
import argparse, hashlib, json, sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res/drawable-nodpi"
DOC = ROOT / "docs/stage-ui-assets.json"
# request id -> app resource name
SCENES = {"R0-post0-sheet-closeup": "e_post_0_sheet"}
SIZE = (900, 1350)
# 1074 letter-tree sprites on #FF00FF: cut out with the style E key (edge-connected chroma, spill, alpha), cropped to the
# object, scaled (aspect kept) to at most SPRITE_MAX px on the long side, WebP with alpha
SPRITES = {"B1-tree-envelope-sealed": "e_post_0_env_sealed", "B2-tree-envelope-opened": "e_post_0_env_opened",
           "B5-tree-fountain-pen": "e_post_0_pen", "B6-tree-time-arrow": "e_post_0_time_arrow"}
CLOSEUPS = {"B3-tree-envelope-sealed-closeup": "e_post_0_env_sealed_close", "B4-tree-envelope-opening": "e_post_0_env_opening_close"}
SPRITE_MAX = 420
# pixel review 2 (1074): B4 shows two red wax blobs on the opened flap's OUTER corners that belong to no seal (the sealed
# envelope B3 has one seal at the flap's tip) – during the opening they appeared next to the still whole central seal.
# They are retouched out locally (only red pixels inside these picture-fraction boxes, filled from the paper around).
RETOUCH_WAX = {"e_post_0_env_opening_close": [(.16, .19, .37, .32), (.66, .20, .85, .34)]}


def paper_mask(img, base):
    """Where the close-up's paper / envelope / wax is (plus R0's own sheet), as a feathered L mask."""
    from PIL import ImageFilter, ImageDraw
    def paperish(rgb):
        hsv = rgb.convert("HSV"); h, s_, v = hsv.split()
        paper = Image.eval(v, lambda x: 255 if x > 150 else 0)
        low = Image.eval(s_, lambda x: 255 if x < 120 else 0)
        r, g, b = rgb.split()
        wax = Image.eval(Image.merge("RGB", (r, g, b)).convert("L"), lambda x: 0)
        red = ImageChops_sub(r, g)
        wax = Image.eval(red, lambda x: 255 if x > 70 else 0)
        from PIL import ImageChops
        return ImageChops.lighter(ImageChops.multiply(paper, low), wax)
    from PIL import ImageChops
    m = ImageChops.lighter(paperish(img), paperish(base))
    m = m.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9))
    # keep the component around the middle (flood fill from the centre)
    w, h = m.size
    m2 = m.copy(); ImageDraw.floodfill(m2, (w // 2, h // 2), 128)
    m = m2.point(lambda x: 255 if x == 128 else 0)
    return m.filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.GaussianBlur(5))


def retouch_wax(img, boxes):
    """Removes red wax inside [boxes] (fractions): the wax and its rim (red mask, dilated) are filled by diffusion from the
    paper around them, then the paper's own grain (high-pass of a clean paper patch nearby) is laid over the fill."""
    from PIL import ImageChops, ImageFilter
    img = img.copy(); w, h = img.size; report = []
    for (l, t, r, b) in boxes:
        box = (int(l * w), int(t * h), int(r * w), int(b * h))
        crop = img.crop(box)
        # wax: red against green at least 1.5 : 1 (the paper is ~1.2 : 1, its shadows too), the dark rim included
        red = Image.new("L", crop.size); rp = red.load(); cp = crop.load()
        for y in range(crop.height):
            for x in range(crop.width):
                r_, g_, b_ = cp[x, y]
                if r_ > 45 and r_ >= 1.5 * g_ + 8: rp[x, y] = 255
        n = sum(1 for v in red.get_flattened_data() if v) if hasattr(red, "get_flattened_data") else sum(1 for v in red.getdata() if v)
        hole = red.filter(ImageFilter.MaxFilter(15))
        fill = crop.copy()
        for _ in range(120):
            fill = Image.composite(fill.filter(ImageFilter.GaussianBlur(2)), crop, hole)
        # grain from the clean paper of the inner sheet (B4: the sheet's middle, measured), the same size as the crop
        gx, gy = int(.38 * w), int(.36 * h)
        patch = img.crop((gx, gy, gx + crop.width, gy + crop.height))
        grain = ImageChops.subtract(patch, patch.filter(ImageFilter.GaussianBlur(3)), 1, 128)
        textured = ImageChops.add(fill, grain, 1, -128)
        soft = hole.filter(ImageFilter.GaussianBlur(2))
        img.paste(Image.composite(textured, crop, soft), box[:2])
        report.append({"box": box, "red_px": n, "filled_px": sum(1 for v in hole.point(lambda v: 1 if v else 0).histogram()[1:2])})
    return img, report


def ImageChops_sub(a, b):
    from PIL import ImageChops
    return ImageChops.subtract(a, b)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--generated", required=True, type=Path)
    ap.add_argument("--only", nargs="*", help="resource names to (re)build; default: all"); a = ap.parse_args()
    doc = json.loads(DOC.read_text()) if DOC.exists() else {"generator": "scripts/stage_ui_assets.py", "assets": {}}
    for rid, name in SCENES.items():
        if a.only and name not in a.only: continue
        png, rec = a.generated / f"{rid}.png", a.generated / f"{rid}.json"
        if not png.exists() or not rec.exists():
            print("missing", rid); continue
        r = json.loads(rec.read_text())
        im = Image.open(png)
        if r.get("success") is not True or list(im.size) != list(r.get("size", [])):
            print("not usable", rid); continue
        out = RES / f"{name}.webp"
        im.convert("RGB").resize(SIZE, Image.LANCZOS).save(out, "WEBP", quality=80, method=6)
        doc["assets"][name] = {"request": rid, "prompt_sha256": r.get("prompt_sha256"), "imagegen_request_id": r.get("provider_result", {}).get("imagegen_request_id"),
                               "source_sha256": hashlib.sha256(png.read_bytes()).hexdigest(), "px": list(SIZE), "bytes": out.stat().st_size,
                               "decoded_rgb565_bytes": SIZE[0] * SIZE[1] * 2}
        print(name, out.stat().st_size)
    sys.path.insert(0, str(ROOT / "scripts"))
    import style_e_assets as E
    for rid, name in {**SPRITES, **CLOSEUPS}.items():
        if a.only and name not in a.only: continue
        png, rec = a.generated / f"{rid}.png", a.generated / f"{rid}.json"
        if not png.exists() or not rec.exists():
            print("missing", rid); continue
        r = json.loads(rec.read_text())
        if r.get("success") is not True:
            print("not usable", rid); continue
        im = Image.open(png)
        out = RES / f"{name}.webp"
        if rid in SPRITES:
            art = E.cut_out(im)
            k = SPRITE_MAX / max(art.size)
            art = art.convert("RGBa").resize((round(art.width * k), round(art.height * k)), Image.LANCZOS).convert("RGBA")
            art.save(out, "WEBP", quality=90, method=6, exact=False)
            alpha = art.getchannel("A"); px = art.load()
            # QA: no magenta left in any visible pixel
            mag = sum(1 for y in range(art.height) for x in range(art.width) if px[x, y][3] > 40 and min(px[x, y][0], px[x, y][2]) - px[x, y][1] > 60)
            extra = {"cutout_px": list(art.size), "magenta_px_left": mag, "decoded_argb_bytes": art.width * art.height * 4}
        else:
            # the generated close-up's bark differs from R0's (mean |diff| 10–20 per channel): only its paper / envelope /
            # wax is laid over R0, so the bark never changes while the views cross-fade (mask: paper-like or wax pixels of
            # the component around the middle, closed and feathered; the R0 sheet's own area is covered too)
            base = Image.open(a.generated.parent.parent / "stage-ui-redesign/generated/R0-post0-sheet-closeup.png").convert("RGB")
            mask = paper_mask(im.convert("RGB"), base)
            comp = Image.composite(im.convert("RGB"), base, mask)
            retouched = None
            if name in RETOUCH_WAX:
                comp, retouched = retouch_wax(comp, RETOUCH_WAX[name])
            comp.resize(SIZE, Image.LANCZOS).save(out, "WEBP", quality=80, method=6)
            extra = {"px": list(SIZE), "decoded_rgb565_bytes": SIZE[0] * SIZE[1] * 2, "composited_over": "R0-post0-sheet-closeup",
                     "mask_share": round(sum(1 for v in mask.getdata() if v > 128) / (mask.width * mask.height), 3)}
            if retouched: extra["retouched_wax"] = retouched
        doc["assets"][name] = {"request": rid, "prompt_sha256": r.get("prompt_sha256"), "source_sha256": hashlib.sha256(png.read_bytes()).hexdigest(),
                               "source_px": list(im.size), "bytes": out.stat().st_size, **extra}
        print(name, out.stat().st_size, extra)
    DOC.write_text(json.dumps(doc, ensure_ascii=False, indent=1, sort_keys=True) + "\n")


if __name__ == "__main__":
    sys.exit(main())
