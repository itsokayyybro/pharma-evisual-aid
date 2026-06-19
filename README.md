# E-Visual Aid

A premium, **100% offline** Android tablet app for pharmaceutical field/MR teams. The MR
manages PDF product catalogs on the Home screen, then hands the tablet to a doctor who
browses them in an immersive, zero-training full-screen viewer with a smart side-index panel.

> Catalog viewer + file manager hybrid. No login, no cloud, no network calls, no ads,
> no analytics. Everything lives in app-private storage.

---

## Features

- **Home / File Manager** — 2-column responsive catalog grid with first-page thumbnails,
  page-count badges, upload dates and tag chips. Inline rename, delete (confirmation
  bottom sheet), tagging, multi-select bulk delete, fuzzy search, filter chips and sort.
- **Presentation / Viewer** — landscape-locked, edge-to-edge immersive PDF viewer with
  swipe navigation, pinch-to-zoom (up to 3×), double-tap reset, auto-hiding chrome, a
  page scrubber with live preview, and a **right-side index drawer** of page thumbnails
  with the active page highlighted.
- **Resume** — reopening a catalog offers "Resume from page N?".
- **Settings** — company name & logo, 6 accent-color presets, default sort, auto-hide
  delay, export all catalogs (zip → share sheet), and a double-confirmed "Clear All Data".

## Tech stack

| Area | Choice |
|------|--------|
| Language / UI | Kotlin · Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository, `StateFlow` |
| PDF rendering | Native `android.graphics.pdf.PdfRenderer` (chosen over PdfBox for performance) |
| Database | Room |
| Settings | DataStore (Preferences) |
| Background work | WorkManager (thumbnail generation) |
| Navigation | Navigation Compose |
| Images | Coil |
| Build | Gradle Kotlin DSL · AGP 8.5.2 · Kotlin 1.9.24 |
| Min / Target SDK | 26 / 34 |

## Project structure

```
app/src/main/java/com/pharma/evisualaid/
├── EVisualAidApp.kt            # Application + manual-DI container + WorkManager config
├── MainActivity.kt             # Single activity; hosts Compose nav, applies accent theme
├── data/
│   ├── db/                     # Room: CatalogEntity, CatalogDao, AppDatabase
│   ├── model/                  # Catalog domain model, SortOrder
│   ├── prefs/                  # SettingsRepository (DataStore)
│   └── repository/             # CatalogRepository (upload pipeline, CRUD), TagCodec
├── ui/
│   ├── home/                   # HomeScreen, HomeViewModel, CatalogCard, FilterBar
│   ├── presentation/           # PresentationScreen/VM, PageViewer, SideIndexPanel, BottomNavBar
│   ├── settings/               # SettingsScreen, SettingsViewModel
│   ├── components/             # Shared dialogs / bottom sheets
│   ├── navigation/             # AppNavigation (routes)
│   ├── theme/                  # Color, Type, Theme
│   └── util/                   # Activity lookup + orientation lock
├── util/                       # PdfUtils, FileUtils, BitmapCache
└── work/                       # ThumbnailWorker
```

## Setup

1. **Android Studio** Koala (2024.1) or newer, with JDK 17 and the Android SDK (API 34).
2. Open the project root in Android Studio. On first sync it will download the configured
   Gradle distribution and dependencies.
3. Run on a tablet emulator or device (Android 8.0 / API 26+).

> **Gradle wrapper jar:** this repo intentionally omits the binary `gradle-wrapper.jar`.
> Android Studio regenerates it automatically on first sync. To use the CLI without Android
> Studio, run once from the project root (with a system Gradle ≥ 8.7 installed):
>
> ```bash
> gradle wrapper --gradle-version 8.7
> ```
>
> after which `./gradlew` works normally.

## Build the APK

```bash
# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned; configure signing in app/build.gradle.kts for production)
./gradlew assembleRelease
```

## Sideload onto a tablet

```bash
# With USB debugging enabled on the tablet:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the `.apk` to the tablet and open it with a file manager (enable
"Install unknown apps" for that file manager).

## Usage

1. On Home, tap **Upload Catalog** and pick a `.pdf`. The file is copied into app-private
   storage; its thumbnail renders in the background (WorkManager).
2. Tap a catalog to present it. Swipe between pages, pinch to zoom, double-tap to reset.
3. Swipe in from the right edge — or tap the grid icon — to open the page index.
4. Drag the scrubber to scan pages with a live preview.
5. Tap **Back** to return to the library; reopen later to resume.

## CDR files

CorelDRAW `.cdr` files are **not** parsed. Export them to PDF from CorelDRAW first, then
upload the PDF.

## Brand fonts (optional)

The app ships with system fonts (sans-serif + monospace) so it builds and runs fully
offline out of the box. To match the exact brand spec (Nunito Sans / Inter / Roboto Mono):

1. Drop the `.ttf` files into `app/src/main/res/font/`.
2. Update the three `FontFamily` definitions in
   [Type.kt](app/src/main/java/com/pharma/evisualaid/ui/theme/Type.kt) — there is a worked
   example in the file's header comment.

## Permissions

- `READ_EXTERNAL_STORAGE` (maxSdkVersion 32 only) — legacy safety net. Modern devices use
  the Storage Access Framework, which grants per-file access with **no** runtime permission.
- No camera, microphone, location, or network permissions are requested.

## Architectural notes

- **Single-activity** design. The Presentation screen locks itself to `sensorLandscape` and
  enters immersive mode at runtime; Home/Settings rotate freely. (Decision made over a
  separate `PresentationActivity` to keep Navigation Compose simple.)
- **Pre-render window:** the viewer keeps current page ± 2 in an `LruCache<Int, Bitmap>`
  sized to ~¼ of heap, trimming pages outside the window to bound memory on large PDFs.
- **Room migrations:** v1 uses `fallbackToDestructiveMigration()`. Add real `Migration`
  objects in `AppDatabase` for future schema versions.
```
