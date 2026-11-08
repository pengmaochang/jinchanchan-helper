# OCR dependencies

The APK bundles Tesseract4Android 4.9.0 and official Tesseract fast Chinese/English models. Binary caches are excluded from Git; `tools/prepare_ocr.py` verifies pinned SHA-256 checksums.

- Tesseract4Android: https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0 — Apache License 2.0. Copyright 2019 Adaptech s.r.o., Robert Pösel.
- Tesseract OCR: https://github.com/tesseract-ocr/tesseract — Apache License 2.0.
- Fast models: https://github.com/tesseract-ocr/tessdata_fast — Apache License 2.0.
- Leptonica: https://github.com/DanBloomberg/leptonica — BSD 2-Clause license.
- libjpeg: https://www.ijg.org — Independent JPEG Group license.
- libpng: https://libpng.org/pub/png/libpng.html — PNG Reference Library License.

The relevant bundled-library license texts are supplied in `app/src/main/assets/licenses/` and distributed with the APK. See the upstream projects for source code and copyright details.
