"""Fetch pinned Tesseract4Android + official fast language models; verify every binary."""
from pathlib import Path
import hashlib
import shutil
import urllib.request
import zipfile
import io
import tarfile

ROOT = Path(__file__).resolve().parents[1]
OCR = ROOT / '.tools/ocr'
ASSETS = OCR / 'assets/tessdata'
FILES = [
    ('https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar',
     OCR / 'tesseract.aar', 'bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a'),
    ('https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/chi_sim.traineddata',
     ASSETS / 'chi_sim.traineddata', 'a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730'),
    ('https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata',
     ASSETS / 'eng.traineddata', '7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2'),
]

def main():
    for url, path, expected in FILES:
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            temp = path.with_suffix(path.suffix + '.part')
            with urllib.request.urlopen(url, timeout=30) as response, temp.open('wb') as out:
                shutil.copyfileobj(response, out)
            if hashlib.sha256(temp.read_bytes()).hexdigest() != expected:
                raise RuntimeError('Checksum mismatch; refusing changed binary: ' + path.name)
            temp.replace(path)
        print(path.name + ' verified', flush=True)
    with zipfile.ZipFile(OCR / 'tesseract.aar') as archive:
        archive.extractall(OCR / 'aar')
    licenses = ROOT / 'app/src/main/assets/licenses'
    licenses.mkdir(parents=True, exist_ok=True)
    notices = {
        'Tesseract4Android.txt': 'https://raw.githubusercontent.com/adaptech-cz/Tesseract4Android/4.9.0/LICENSE',
        'Tesseract.txt': 'https://raw.githubusercontent.com/tesseract-ocr/tesseract/main/LICENSE',
        'Tessdata-fast.txt': 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/LICENSE',
        'Leptonica.txt': 'https://raw.githubusercontent.com/DanBloomberg/leptonica/master/leptonica-license.txt',
        'libpng.txt': 'https://raw.githubusercontent.com/pnggroup/libpng/libpng16/LICENSE',
    }
    for name, url in notices.items():
        destination = licenses / name
        if not destination.exists():
            with urllib.request.urlopen(url, timeout=30) as response:
                destination.write_bytes(response.read())
        print(name + ' ready', flush=True)
    if not (licenses / 'IJG-jpeg-README.txt').exists():
        with urllib.request.urlopen('https://www.ijg.org/files/jpegsrc.v9f.tar.gz', timeout=30) as response:
            data = response.read()
        with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
            reader = archive.extractfile('jpeg-9f/README')
            if reader is None:
                raise RuntimeError('Missing IJG license')
            (licenses / 'IJG-jpeg-README.txt').write_bytes(reader.read())

if __name__ == '__main__':
    main()
