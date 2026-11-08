# OCR and build dependencies

## Golden spatula icon

`app/src/main/res/drawable-nodpi/jcc_icon.png` is an ImageGen-cleaned version of the golden spatula equipment artwork supplied by the user. It replaces the previously downloaded game launcher icon, following the user's clarification. The edit removes the screenshot border and website overlay, keeps the diagonal golden spatula and dark gold background, and adds no text or characters.

The underlying game artwork belongs to Tencent and the respective rights holders. This asset is used for the local helper launcher, main screen and translucent overlay.

## OCR

The APK uses Google's bundled Chinese Text Recognition model, `com.google.mlkit:text-recognition-chinese:16.0.1`, resolved from Google's Maven repository by Gradle. The model is packaged into the app; image processing stays on the device. The app removes the INTERNET permission from the merged manifest.

- ML Kit documentation: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ML Kit terms: https://developers.google.com/ml-kit/terms
- Google APIs terms: https://developers.google.com/terms
- AndroidX source and licenses: https://android.googlesource.com/platform/frameworks/support/

ML Kit's published Maven POMs identify their applicable terms and transitive dependencies. Packaged META-INF notices from dependencies are handled by the Android Gradle plugin. No Tesseract library or language model is shipped in v0.4.

Portable development tools are kept outside Git under `.tools/`: Gradle 8.9 (Apache 2.0), Eclipse Temurin JDK 17 (GPLv2 with the Classpath Exception), and Android SDK tools (Android SDK license). Their archives include the upstream license notices. Preparation scripts verify the pinned archive checksums.

## S18 catalog and portraits

`app/src/main/assets/champions_s18.json` was retrieved on 2026-11-09 from the public endpoint https://www.dataj.cc/api/web/gamedata?setId=18 used by https://www.dataj.cc/explorer. It keeps unique visible, non-special (`heroType=0`) entries with prices 1–5, including shop-related creature variants exposed by the source; duplicate names are collapsed. The upstream `season` text is inconsistent with `setId`; the request parameter and page selector identify this as set 18. No claim of official completeness is made.

Portraits in `assets/portraits/` come from each record's `picture` URL on `img.dataj.cc` and are bundled for offline search. Underlying game art remains owned by its respective rights holders.
