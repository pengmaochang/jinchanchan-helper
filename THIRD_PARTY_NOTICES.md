# OCR and build dependencies

## Game icon

`app/src/main/res/drawable-nodpi/jcc_icon.png` is the 金铲铲之战 icon retrieved at the user's request from its Xiaomi App Store listing: https://app.mi.com/details?id=com.tencent.jkchess

Asset URL: https://file.market.xiaomi.com/thumbnail/PNG/l512/AppStore/0a974be48eccc4b2abdf27b59b7bffa1e931600c0

The game artwork belongs to Tencent and the respective rights holders. The app keeps its own helper name; the requested icon is used for the local launcher and translucent overlay.

## OCR

The APK uses Google's bundled Chinese Text Recognition model, `com.google.mlkit:text-recognition-chinese:16.0.1`, resolved from Google's Maven repository by Gradle. The model is packaged into the app; image processing stays on the device. The app removes the INTERNET permission from the merged manifest.

- ML Kit documentation: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ML Kit terms: https://developers.google.com/ml-kit/terms
- Google APIs terms: https://developers.google.com/terms
- AndroidX source and licenses: https://android.googlesource.com/platform/frameworks/support/

ML Kit's published Maven POMs identify their applicable terms and transitive dependencies. Packaged META-INF notices from dependencies are handled by the Android Gradle plugin. No Tesseract library or language model is shipped in v0.4.

Portable development tools are kept outside Git under `.tools/`: Gradle 8.9 (Apache 2.0), Eclipse Temurin JDK 17 (GPLv2 with the Classpath Exception), and Android SDK tools (Android SDK license). Their archives include the upstream license notices. Preparation scripts verify the pinned archive checksums.
