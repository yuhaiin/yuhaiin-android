#!/usr/bin/env python3
"""Release gate: verify APK zip alignment and every 64-bit ELF LOAD segment for 16 KB pages."""
import argparse
import os
from pathlib import Path
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '.'))))
args = parser.parse_args()
zipalign_candidates = sorted((args.sdk / 'build-tools').glob('*/zipalign'), key=lambda p: tuple(int(n) for n in p.parent.name.split('.') if n.isdigit()))
if not zipalign_candidates:
    parser.error('No zipalign found; provide --sdk /path/to/Android/Sdk')
zipalign = zipalign_candidates[-1]
subprocess.run([str(zipalign), '-c', '-P', '16', '4', str(args.apk)], check=True)
with zipfile.ZipFile(args.apk) as archive:
    libraries = [name for name in archive.namelist() if name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and name.endswith('.so')]
    assert libraries, 'APK has no 64-bit native libraries'
    for name in libraries:
        data = archive.read(name)
        assert data[:4] == b'\x7fELF' and data[4] == 2, f'{name}: expected ELF64'
        endian = '<' if data[5] == 1 else '>'
        phoff = struct.unpack_from(endian + 'Q', data, 32)[0]
        entry_size, count = struct.unpack_from(endian + 'HH', data, 54)
        for i in range(count):
            p_type, _, offset, address, _, _, _, align = struct.unpack_from(endian + 'IIQQQQQQ', data, phoff + i * entry_size)
            if p_type == 1:
                assert align >= 16384 and (offset - address) % 16384 == 0, f'{name}: LOAD segment is not 16 KB aligned'
        print(f'PASS {name}')
print(f'PASS {args.apk}: APK and ELF alignment (runtime device test remains separate)')
