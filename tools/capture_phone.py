#!/usr/bin/env python3
"""Capture an unmodified connected Android app screen, with evidence metadata."""
import argparse, datetime, hashlib, io, json, re, subprocess
from pathlib import Path
from PIL import Image
p=argparse.ArgumentParser()
p.add_argument('name',help='e.g. 01-dashboard, 02-case-overview, 03-deadlines')
p.add_argument('--serial',required=True,help='exact adb devices serial')
p.add_argument('--sample-data',action='store_true',help='record that visible data belongs to built-in demo')
a=p.parse_args()
if not re.fullmatch(r'[a-zA-Z0-9_-]+',a.name): p.error('Use only letters, digits, underscore and hyphen')
adb=['adb','-s',a.serial]
def run(*args): return subprocess.check_output(adb+list(args))
pkg='net.softm.seolgyehannun'
windows=run('shell','dumpsys','window','windows').decode(errors='replace')
focused='\n'.join(line for line in windows.splitlines() if 'mCurrentFocus' in line)
if pkg not in focused: raise SystemExit('Open the actual 설계한눈 app and dismiss overlays before capture')
raw=run('exec-out','screencap','-p')
im=Image.open(io.BytesIO(raw)); w,h=im.size
if min(w,h)<320 or max(w,h)>3840 or max(w,h)>2*min(w,h):
    raise SystemExit(f'{w}x{h} is outside Play limits. Use a 1080x1920 emulator/device configuration; do not crop away app UI.')
root=Path(__file__).resolve().parents[1]
out=root/'store/captures'; out.mkdir(parents=True,exist_ok=True)
rawpath=out/(a.name+'-raw.png'); dst=out/(a.name+'.png'); meta=out/(a.name+'.json')
if any(f.exists() for f in [rawpath,dst,meta]): raise SystemExit('Choose a new name; existing evidence is not overwritten')
rawpath.write_bytes(raw)
im.convert('RGB').save(dst)  # format normalization only: no resizing, overlays, or content edits
info=run('shell','dumpsys','package',pkg).decode(errors='replace')
versions=[l.strip() for l in info.splitlines() if 'versionCode=' in l or 'versionName=' in l]
meta.write_text(json.dumps({'captured_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'package':pkg,'version':versions,'sample_data':a.sample_data,'dimensions':[w,h],'raw_sha256':hashlib.sha256(raw).hexdigest(),'image_sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'manual_review':'PENDING: check rendering, sample-data label, private data, and match to submitted release'},ensure_ascii=False,indent=2)+'\n')
print(dst)
print('Review image before copying into store/ko-KR/phoneScreenshots/. Never commit private production data.')
