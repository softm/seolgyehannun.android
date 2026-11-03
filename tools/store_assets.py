#!/usr/bin/env python3
"""Render the Android vector source to Play assets; never synthesize screenshots."""
import argparse
import io
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
OUT = ROOT / 'store/ko-KR'

def render():
    vector = ET.parse(ROOT / 'app/src/main/res/drawable/ic_launcher_foreground.xml').getroot()
    assert vector.get(A+'viewportWidth') == vector.get(A+'viewportHeight') == '108'
    bg = ET.parse(ROOT / 'app/src/main/res/values/colors.xml').find("color[@name='launcher_background']").text
    im = Image.new('RGBA', (2048, 2048), bg)
    draw = ImageDraw.Draw(im)
    # Android exposes the central 72 of the 108dp adaptive canvas. The store
    # image uses that same viewport, without baked-in rounded corners/shadows.
    for path in vector:
        d = path.get(A+'pathData')
        match = re.fullmatch(r'M([\d.]+),([\d.]+)H([\d.]+)V([\d.]+)H([\d.]+)Z', d)
        if not match:
            raise ValueError('Unsupported vector path; extend renderer before changing artwork')
        x1,y1,x2,y2,x3 = map(float,match.groups())
        assert x1 == x3
        for x in (x1,x2):
            for y in (y1,y2):
                assert (x-54)**2+(y-54)**2 <= 33**2, 'Outside adaptive safe circle'
        xy = tuple((n-18)/72*2048 for n in (x1,y1,x2,y2))
        draw.rectangle(xy,fill=path.get(A+'fillColor'))
    im = im.resize((512,512),Image.Resampling.LANCZOS)
    buf = io.BytesIO(); im.save(buf,format='PNG')
    return buf.getvalue()

def check_screenshots(folder):
    files = sorted(Path(folder).glob('*.png'))
    assert 2 <= len(files) <= 8, 'Supply 2–8 genuine phone screenshots'
    for file in files:
        with Image.open(file) as im:
            w,h = im.size
            assert im.mode == 'RGB', f'{file}: RGB PNG without alpha required'
            assert min(w,h)>=320 and max(w,h)<=3840 and max(w,h)<=2*min(w,h), f'{file}: invalid dimensions {im.size}'
    print(f'{len(files)} screenshot files pass format checks; authenticity and content require manual review')

if __name__ == '__main__':
    p=argparse.ArgumentParser(); p.add_argument('--check',action='store_true'); p.add_argument('--screenshots')
    args=p.parse_args(); asset=OUT/'icon-512.png'; data=render()
    if args.check:
        assert asset.read_bytes()==data, 'Store icon drift: regenerate from Android vector'
        manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().find('application')
        assert manifest.get(A+'label')=='설계한눈'
        for attr,name in [('icon','ic_launcher'),('roundIcon','ic_launcher_round')]:
            assert manifest.get(A+attr)==f'@mipmap/{name}'
            icon=ET.parse(ROOT/f'app/src/main/res/mipmap-anydpi-v26/{name}.xml').getroot()
            assert icon.find('foreground').get(A+'drawable')=='@drawable/ic_launcher_foreground'
            assert icon.find('background').get(A+'drawable')=='@color/launcher_background'
        with Image.open(asset) as im:
            assert im.size==(512,512) and im.mode=='RGBA'
        assert len(data)<=1024*1024
        print('PASS: label, both adaptive icons, safe zone, deterministic 512px RGBA PNG and size')
    else:
        OUT.mkdir(parents=True,exist_ok=True); asset.write_bytes(data); print(asset)
    if args.screenshots: check_screenshots(args.screenshots)
