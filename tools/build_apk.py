"""Build the dependency-free probe with official Android SDK tools, without Gradle."""
from pathlib import Path
import os
import subprocess
import zipfile
import shutil

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
    env = os.environ.copy()
    env['JAVA_HOME'] = str(JDK)
    os.environ['JAVA_HOME'] = str(JDK)
    os.environ['PATH'] = str(JDK / 'bin') + os.pathsep + os.environ['PATH']
    resources = BUILD / 'resources.zip'
    run(bt / 'aapt2.exe', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', resources)
    raw = BUILD / 'raw.apk'
    run(bt / 'aapt2.exe', 'link', '-o', raw, '-I', sdk, '--manifest',
        ROOT / 'app/src/main/AndroidManifest.xml', '--min-sdk-version', '30',
        '--target-sdk-version', '35', '--version-code', '1', '--version-name', '0.1',
        '--rename-manifest-package', 'local.jcc.probe', resources)
    sources = list((ROOT / 'app/src/main/java').rglob('*.java'))
    run(JDK / 'bin/javac.exe', '-encoding', 'UTF-8', '--release', '17', '-classpath', sdk,
        '-d', classes, *sources)
    dex = BUILD / 'dex'
    dex.mkdir(exist_ok=True)
    run(JDK / 'bin/java.exe', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--lib', sdk, '--min-api', '30', '--output', dex, *classes.rglob('*.class'))
    unsigned = BUILD / 'unsigned.apk'
    shutil.copyfile(raw, unsigned)
    with zipfile.ZipFile(unsigned, 'a') as archive:
        for file in dex.glob('*.dex'):
            archive.write(file, file.name)
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
