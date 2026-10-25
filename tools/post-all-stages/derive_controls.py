"""Bounded derivatives of approved native brass; no generated/repainted scene."""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
from pathlib import Path
import os, json, io, subprocess
root = Path(__file__).resolve().parents[2]
art = root / 'android/app/src/main/res/drawable-nodpi'
def original(name):
    return Image.open(io.BytesIO(subprocess.check_output(['git','show',f'612b48d753b7131dfcdf966a12fa53b8000730bb:android/app/src/main/res/drawable-nodpi/{name}'],cwd=root))).convert('RGBA')
source = original('e_post_1_physical_back.webp')
# Exact canonical coordinates of four stationary knobs on the chest band.
bounds = [(x-24,1057,x+25,1106) for x in (260,422,584,746)]
clean = source.copy()
for box in bounds:
    patch = source.crop((310,1057,359,1106))
    mask = Image.new('L', patch.size, 0)
    ImageDraw.Draw(mask).rectangle((3,3,45,45), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(2))
    clean.paste(patch, box[:2], mask)
# Keep all unrelated pixels byte-identical after decoding (lossless WebP).
for suffix in ('back','holder'):
    path = art / f'e_post_1_physical_{suffix}.webp'
    before = original(path.name); after = before.copy()
    for box in bounds:
        after.paste(clean.crop(box), box[:2])
    delta = ImageChops.difference(before,after)
    for box in bounds:
        ImageDraw.Draw(delta).rectangle((box[0],box[1],box[2]-1,box[3]-1),fill=(0,0,0,0))
    assert all(hi == 0 for lo, hi in delta.getextrema())
    after.save(path, lossless=True)
# The track and moving handle come from the same brass band, with real texture.
clean.crop((205,1054,815,1110)).save(art/'post_time_track.webp', lossless=True)
knob = source.crop((236,1057,285,1106))
mask = Image.new('L',knob.size,0); ImageDraw.Draw(mask).ellipse((5,3,44,45),fill=255)
knob.putalpha(mask.filter(ImageFilter.GaussianBlur(.5)))
knob.save(art/'post_time_handle.webp',lossless=True)
report = {'canvas':[1024,1536], 'changed_rectangles':bounds, 'outside_decoded_rgba_unchanged':True,
          'track_crop':[205,1054,815,1110], 'handle_crop':[236,1057,285,1106]}
Path(os.environ['TMPDIR'],'post-control-derivation.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
