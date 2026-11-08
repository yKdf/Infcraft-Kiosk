#!/usr/bin/env python3
"""Download pinned official Android build tools into this checkout."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile
import tempfile
root=Path(__file__).resolve().parent/'sdk'
root.mkdir(exist_ok=True)
packages=[('platform-28_r06.zip','9a4e52b1d55bd2e24216b150aafae2503d3efba6'),('build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc')]
for name,sha1 in packages:
    with tempfile.TemporaryDirectory() as directory:
        archive=Path(directory)/name
        print('Downloading',name)
        urllib.request.urlretrieve('https://dl.google.com/android/repository/'+name,archive)
        if hashlib.sha1(archive.read_bytes()).hexdigest()!=sha1:
            raise RuntimeError('Official repository checksum mismatch: '+name)
        with zipfile.ZipFile(archive) as z:
            z.extractall(root)
            for entry in z.infolist():
                mode=entry.external_attr>>16
                if mode & 0o111: (root/entry.filename).chmod(0o755)
print('SDK ready.')
