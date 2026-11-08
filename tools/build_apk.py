"""Build with official SDK tools and the pinned OCR AAR, without Gradle."""
from pathlib import Path
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
BUILD = ROOT / 'app' / 'build' / 'standalone'
JDK = Path(os.environ.get('PROBE_JAVA_HOME', r'C:\Program Files\JetBrains\IntelliJ IDEA 2023.1\jbr'))

def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)

def main():
    sdk = next((TOOLS / 'platform').rglob('android.jar'))
    bt = next((TOOLS / 'buildtools').rglob('aapt2.exe')).parent
    BUILD.mkdir(parents=True, exist_ok=True)
    classes = BUILD / 'classes'
    classes.mkdir(exist_ok=True)
    ocr = TOOLS / 'ocr'
    ocr_jar = ocr / 'aar/classes.jar'
    if not ocr_jar.is_file() or not (ocr / 'assets/tessdata/chi_sim.traineddata').is_file():
        raise RuntimeError('Run python tools/prepare_ocr.py first')
    os.environ['JAVA_HOME'] = str(JDK)
    os.environ['PATH'] = str(JDK / 'bin') + os.pathsep + os.environ['PATH']
    resources = BUILD / 'resources.zip'
    run(bt / 'aapt2.exe', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', resources)
    raw = BUILD / 'raw.apk'
    run(bt / 'aapt2.exe', 'link', '-o', raw, '-I', sdk, '--manifest',
        ROOT / 'app/src/main/AndroidManifest.xml', '--min-sdk-version', '30',
        '--target-sdk-version', '35', '--version-code', '3', '--version-name', '0.3',
        '--rename-manifest-package', 'local.jcc.probe', '-A', ROOT / 'app/src/main/assets',
        '-A', ocr / 'assets', resources)
    sources = list((ROOT / 'app/src/main/java').rglob('*.java'))
    run(JDK / 'bin/javac.exe', '-encoding', 'UTF-8', '--release', '17', '-classpath', str(sdk) + os.pathsep + str(ocr_jar),
        '-d', classes, *sources)
    dex = BUILD / 'dex'
    dex.mkdir(exist_ok=True)
    run(JDK / 'bin/java.exe', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--lib', sdk, '--min-api', '30', '--output', dex, ocr_jar, *classes.rglob('*.class'))
    unsigned = BUILD / 'unsigned.apk'
    # Repack instead of appending to aapt2's ZIP: keep local/central headers consistent.
    with zipfile.ZipFile(raw) as source, zipfile.ZipFile(unsigned, 'w') as archive:
        for entry in source.infolist():
            archive.writestr(entry.filename, source.read(entry), compress_type=
                zipfile.ZIP_STORED if entry.filename == 'resources.arsc' else zipfile.ZIP_DEFLATED)
        for file in dex.glob('*.dex'):
            archive.write(file, file.name)
        for file in (ocr / 'aar/jni/arm64-v8a').glob('*.so'):
            archive.write(file, 'lib/arm64-v8a/' + file.name, compress_type=zipfile.ZIP_DEFLATED)
    aligned = BUILD / 'aligned.apk'
    run(bt / 'zipalign.exe', '-f', '4', unsigned, aligned)
    key = TOOLS / 'probe-debug.jks'
    if not key.exists():
        run(JDK / 'bin/keytool.exe', '-genkeypair', '-keystore', key, '-storepass', 'android',
            '-keypass', 'android', '-alias', 'probe', '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '3650', '-dname', 'CN=Local Screenshot Probe', '-noprompt')
    output = ROOT / 'dist/JccScreenshotProbe.apk'
    output.parent.mkdir(exist_ok=True)
    run(JDK / 'bin/java.exe', '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-key-alias', 'probe', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', output, aligned)
    run(JDK / 'bin/java.exe', '-jar', bt / 'lib/apksigner.jar', 'verify', '--verbose', output)
    print(output)

if __name__ == '__main__':
    main()
