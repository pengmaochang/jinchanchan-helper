"""Prepare verified portable build tools inside the project; no global installation."""
from pathlib import Path
import hashlib
import urllib.request
import shutil
import zipfile
from prepare_sdk import main as prepare_sdk

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
PACKAGES = [
    ('https://downloads.gradle.org/distributions/gradle-8.9-bin.zip', 'gradle.zip', TOOLS,
     'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'),
    ('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip',
     'jdk17.zip', TOOLS / 'jdk17', '98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b'),
]

def main():
    TOOLS.mkdir(exist_ok=True)
    for url, name, destination, expected in PACKAGES:
        archive = TOOLS / name
        if not archive.is_file() or hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            temp = archive.with_suffix('.part')
            with urllib.request.urlopen(url, timeout=30) as response, temp.open('wb') as out:
                shutil.copyfileobj(response, out)
            if hashlib.sha256(temp.read_bytes()).hexdigest() != expected:
                raise RuntimeError('Checksum mismatch: ' + name)
            temp.replace(archive)
        with zipfile.ZipFile(archive) as package:
            package.extractall(destination)
        print(name + ' verified and extracted', flush=True)
    prepare_sdk()

if __name__ == '__main__':
    main()
