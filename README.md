# FieldTag

**FieldTag** is an Android app for industrial field teams: import P&ID PDFs, extract instrument tags, and document equipment with photos and video—**offline-first**, with no cloud required for core workflows.

This repository contains the **FieldTag** Android project under [`fieldtag/`](fieldtag/), plus product notes and sample assets at the repo root.

## Repository layout

| Path | Purpose |
|------|--------|
| [`fieldtag/`](fieldtag/) | Android application (Gradle module `:app`) |
| [`fieldTag.md`](fieldTag.md) | MVP architecture and product specification |
| [`FieldTag_MVP_Architecture_v3.docx`](FieldTag_MVP_Architecture_v3.docx) | Architecture document (v3) |
| [`Example_PID_pdfs/`](Example_PID_pdfs/) | Example P&ID PDFs for testing |

## Features (high level)

- **Projects** — Organize work by job or site.
- **P&ID import** — Load PDF drawings; parse text to discover **instruments** (ISA-style tags).
- **Diagram viewer** — View sheets and tag positions; calibration flow for aligning overlays.
- **Field capture** — **CameraX** for photo/video; attach media to instruments with roles (overview, detail, nameplate, etc.).
- **OCR / ML Kit** — Text recognition and barcode scanning to support tagging workflows.
- **Local persistence** — **Room** database; export-oriented design per product spec.

## Tech stack

- **Language:** Kotlin  
- **UI:** Jetpack Compose, Material 3  
- **DI:** Hilt  
- **Database:** Room (KSP)  
- **PDF:** PdfBox Android  
- **Camera:** CameraX  
- **ML:** ML Kit (text recognition, barcode)  
- **Async:** Kotlin coroutines, WorkManager  
- **Tests:** JUnit, Robolectric, Turbine, MockK, Compose UI tests (see `app/src/test` and `app/src/androidTest`)

## Requirements

- **JDK 17**  
- **Android SDK** with **compileSdk 34** (Android Studio recommended)  
- Device or emulator running **API 26+**

## Build and run

From the `fieldtag` directory:

```bash
cd fieldtag
./gradlew assembleDebug
```

On Windows:

```powershell
cd fieldtag
.\gradlew.bat assembleDebug
```

Install the debug build on a connected device:

```bash
./gradlew installDebug
```

### Where the APK is

After a successful build:

- **Debug:** `fieldtag/app/build/outputs/apk/debug/app-debug.apk`
- **Release (unsigned by default):** `fieldtag/app/build/outputs/apk/release/app-release-unsigned.apk`

Release builds that ship to users need a **signing config** (keystore + `keystore.properties`—never commit secrets; see `.gitignore`).

## Run tests

```bash
cd fieldtag
./gradlew test
./gradlew connectedAndroidTest   # requires device/emulator
```

## Links

- **Remote:** [github.com/Lucas-Song-Dev/fieldtag](https://github.com/Lucas-Song-Dev/fieldtag)

## License

No license file is included yet. Add one (for example MIT or Apache-2.0) if you intend to open-source the project for others to reuse.
