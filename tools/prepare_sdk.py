"""Download the two official SDK archives needed by the standalone build."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
JOBS = [
    ('platform-35_r02.zip', 'platform', '0bb560a90a7a2cbd0dd8348224d518b638fe7949'),
    ('build-tools_r35_windows.zip', 'buildtools', 'af059bb67cf7786f45ee0db85e2d24985df1b4b6'),
]

def download(job):
    name, directory, digest = job
    archive = TOOLS / name
    TOOLS.mkdir(exist_ok=True)
    if not archive.exists():
        with urllib.request.urlopen('https://dl.google.com/android/repository/' + name, timeout=60) as response:
            with archive.open('wb') as output:
                import shutil
                shutil.copyfileobj(response, output)
    if hashlib.sha1(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError('SDK archive checksum mismatch: ' + name)
    with zipfile.ZipFile(archive) as package:
        package.extractall(TOOLS / directory)
    print(directory + ' ready', flush=True)

if __name__ == '__main__':
    with ThreadPoolExecutor(2) as pool:
        list(pool.map(download, JOBS))
