"""Source/decoded-image evidence ONLY. Never substitutes for native production captures."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops
import re, os, json, subprocess, io
root=Path(__file__).resolve().parents[2]
art=root/'android/app/src/main/res/drawable-nodpi'
out=Path(os.environ['TMPDIR'])/'post-all-stages-source-probe'; out.mkdir(exist_ok=True)
source=(root/'android/app/src/main/java/at/gregor/layermaxxing/PhysicalPostalStorage.kt').read_text()
def pairs(text):
    ns=list(map(int,re.findall(r'-?\d+',text)));return list(zip(ns[::2],ns[1::2]))
def contains(poly,x,y):
    inside=False
    for (ax,ay),(bx,by) in zip(poly,poly[-1:]+poly[:-1]):
        if (ay>y)!=(by>y) and x<(bx-ax)*(y-ay)/(by-ay)+ax:inside=not inside
    return inside
slots={}
for level,part,nextpart in [(1,'chest','private fun cubby'),(3,'trays','fun slots')]:
    text=source.split(f'private val {part} = listOf(')[1].split(nextpart)[0]
    slots[level]=[pairs(m) for m in re.findall(r'slot\(points\(([^)]*)\)',text)]
slots[2]=[]
text=source.split('private val cubbies = listOf(')[1].split('private val counterLip')[0]
for l,t,r,b in re.findall(r'cubby\((\d+),(\d+),(\d+),(\d+)',text):
    l,t,r,b=map(int,(l,t,r,b));slots[2].append([(l,t),(r,t+8),(r+9,b),(l-9,b-5)])
report={'kind':'SOURCE GEOMETRY / DECODED ART ONLY; not native render evidence','levels':{}}
for level in (1,2,3):
    im=Image.open(art/f'e_post_{level}_physical_back.webp').convert('RGBA');holder=Image.open(art/f'e_post_{level}_physical_holder.webp').convert('RGBA')
    assert im.size==holder.size==(1024,1536)
    draw=ImageDraw.Draw(im); records=[]; samples=[]
    for i,poly in enumerate(slots[level]):
        xs,ys=zip(*poly); bounds=(min(xs),min(ys),max(xs),max(ys))
        exposed={(x,y) for y in range(min(ys),max(ys)+1) for x in range(min(xs),max(xs)+1) if contains(poly,x,y) and holder.getpixel((x,y))[3]<128}
        assert len(exposed)>1000,(level,i,len(exposed))
        assert all(exposed.isdisjoint(s) for s in samples);samples.append(exposed)
        draw.polygon(poly,outline='lime',width=3);draw.text((min(xs)+20,min(ys)+20),f'FACE {i+1}',fill='white')
        records.append({'index':i,'face_bounds':bounds,'exposed_pixels_against_actual_holder_alpha':len(exposed)})
    assert len(records)=={1:4,2:6,3:6}[level]
    # Same immutable 2:3 map + pan rule as FunctionStage, with the stage's painted
    # surface bounds. Check exposed face hulls against both requested root viewports.
    surfaces={1:[(.33,.74),(.35,.86),(.41,.59),(.30,.88)],2:[(.06,.79),(.12,.77),(.09,.88),(.23,.79)],3:[(.05,.62),(.26,.74),(.24,.81),(.11,.93)]}[level]
    pinned={1:(.35,.86),2:(.09,.88),3:(.11,.93)}[level]
    viewports=[]
    for width,height in ((411,891),(360,640)):
        iw=max(width,height*2/3); ih=iw*1.5; left=(width-iw)/2
        def fit(regions):
            l=min(x[0] for x in regions)*iw;r=max(x[1] for x in regions)*iw
            return max(8-l,min(left,width-8-r)) if r-l<=width-16 else None
        fitted=fit(surfaces)
        if fitted is None:fitted=fit([pinned])
        if fitted is not None:left=fitted
        left=max(width-iw,min(0,left)); top=(height-ih)/2
        for record in records:
            l,t,r,b=record['face_bounds']
            assert left+iw*l/1024>=-1 and left+iw*r/1024<=width+1,(level,width,record,left)
            assert top+ih*t/1536>=64 and top+ih*b/1536<=height,(level,width,record)
        viewports.append({'root_dp':[width,height],'picture_width_dp':iw,'picture_left_dp':left,'all_physical_face_hulls_inside_root':True})
    report['levels'][level]={'count':len(records),'slots':records,'source_viewports':viewports}
    im.resize((512,768)).save(out/f'post{level}-geometry-ONLY.png')
# Independent full-image outside-region comparison against pristine Git bytes.
rects=[(x-24,1057,x+25,1106) for x in (260,422,584,746)]
for suffix in ('back','holder'):
    name=f'e_post_1_physical_{suffix}.webp'
    before=Image.open(io.BytesIO(subprocess.check_output(['git','show',f'612b48d753b7131dfcdf966a12fa53b8000730bb:android/app/src/main/res/drawable-nodpi/{name}'],cwd=root))).convert('RGBA')
    after=Image.open(art/name).convert('RGBA')
    delta=ImageChops.difference(before,after)
    for l,t,r,b in rects:ImageDraw.Draw(delta).rectangle((l,t,r-1,b-1),fill=(0,0,0,0))
    assert all(hi==0 for lo,hi in delta.getextrema()),name
report['outside_four_knob_regions_all_rgba_channels_unchanged']=True
(out/'geometry.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2));print(out)
