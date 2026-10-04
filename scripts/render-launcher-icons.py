#!/usr/bin/env python3
"""Regenerate legacy launcher/Play Store PNGs from the checked-in upstream SVG."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
renderer = shutil.which('rsvg-convert')
if not renderer:
    raise SystemExit('Install librsvg (rsvg-convert) to render the SVG icon.')
source = root / 'branding/packet-rhythm.svg'
for density, pixels in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    for name in ['ic_launcher_v2.png', 'ic_launcher_v2_round.png']:
        output = root / f'app/src/main/res/mipmap-{density}/{name}'
        subprocess.run([renderer, '-w', str(pixels), '-h', str(pixels), '-o', str(output), str(source)], check=True)
subprocess.run([renderer, '-w', '512', '-h', '512', '-o', str(root / 'app/src/main/ic_launcher_v2-playstore.png'), str(source)], check=True)
