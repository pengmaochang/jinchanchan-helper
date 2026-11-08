"""Build the phone-specific APK with Gradle so bundled OCR resources/providers merge correctly."""
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'

def main():
    candidates = sorted((TOOLS / 'jdk17').glob('*/bin/jlink.exe'))
    configured = os.environ.get('PROBE_JAVA_HOME')
    if configured:
        jdk = Path(configured)
    elif candidates:
        jdk = candidates[0].parents[1]
    else:
        raise RuntimeError('Set PROBE_JAVA_HOME to a full JDK 17, or run tools/prepare_build.py')
    if not (jdk / 'bin/jlink.exe').is_file():
        raise RuntimeError('A full JDK 17 with jlink is required; an IDE runtime alone is insufficient')
    sdk = Path(os.environ.get('ANDROID_HOME', str(TOOLS / 'sdk')))
    gradle = TOOLS / 'gradle-8.9/bin/gradle.bat'
    if not gradle.is_file():
        raise RuntimeError('Run python tools/prepare_build.py first')
    env = os.environ.copy()
    env.update(JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk), ANDROID_USER_HOME=str(TOOLS / 'android-user'))
    key = TOOLS / 'probe-debug.jks'
    if not key.exists():
        subprocess.run([str(jdk / 'bin/keytool.exe'), '-genkeypair', '-keystore', str(key),
            '-storepass', 'android', '-keypass', 'android', '-alias', 'probe', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Local Screenshot Probe', '-noprompt'], check=True, env=env)
    subprocess.run([str(gradle), '--no-daemon', '--console=plain', '--gradle-user-home',
        str(TOOLS / 'gradle-user-home'), ':app:assembleDebug'], cwd=ROOT, env=env, check=True)
    output = ROOT / 'dist/JccScreenshotProbe.apk'
    output.parent.mkdir(exist_ok=True)
    shutil.copyfile(ROOT / 'app/build/outputs/apk/debug/app-debug.apk', output)
    subprocess.run([str(jdk / 'bin/java.exe'), '-jar', str(sdk / 'build-tools/35.0.0/lib/apksigner.jar'),
        'verify', '--verbose', str(output)], check=True, env=env)
    print(output)

if __name__ == '__main__':
    main()
