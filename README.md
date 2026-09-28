# 📚 AppShelf

[Русский](README.ru.md) · **English**

A list of the apps installed on your phone — **name, package, where each one came from and when it was installed**,
sorted by name. Save it to a file, and after a factory reset or on a new phone open it again: AppShelf shows
which apps are missing and opens each one in the right store.

## Features

- **All installed apps**, sorted by name like a dictionary (upper/lower case and Ё/Е don't matter), with letter headers
- **Source** of every app: Google Play, F-Droid (and Droid-ify, Neo Store), Aurora Store, Obtainium, RuStore,
  GetApps, Galaxy Store, AppGallery, Amazon — or an **APK file**, including the app it was opened from ("APK · via Telegram")
- **Install date**, version and last update in the app card
- **Search** by name or package, **filter chips** by source, optional system apps
- **Save the list** as JSON (to restore later), Markdown (to read, e.g. in Obsidian) or CSV (for spreadsheets), or share it as text
- **Autosave**: pick a file once (a cloud drive, a memory card…) and the list is rewritten there every time you open the app
- **Restore mode**: open a saved list — see how many apps are missing, show only those, and tap to install each from its store
- **Material 3 "liquid glass" design**: translucent cards over a soft colored background, colors follow the wallpaper
  (Android 12+). Themes: standard, follow system, AMOLED black, light, graphite

## Download

Ready-made APKs are on the [Releases](https://github.com/Xtratter/appshelf/releases) page. Android 8.0 or newer is required.

## Permissions

Only `QUERY_ALL_PACKAGES` — without it Android 11+ shows an app just a few of the installed apps.
No internet permission: the list never leaves the phone unless you save or share it yourself.

## How it works

Everything comes from Android's `PackageManager`: `firstInstallTime` / `lastUpdateTime` for the dates and
`getInstallSourceInfo()` (Android 11+) for the installer and the app that started an APK installation.
Files are written through the system file picker (Storage Access Framework), so no storage permission is needed.

```
app/src/main/java/io/github/xtratter/appshelf/
├── Apps.kt           ← reading installed apps
├── Model.kt          ← AppInfo and Source (installer package → store)
├── ListFile.kt       ← sorting and JSON / CSV / Markdown files
├── Store.kt          ← opening an app in its store
├── MainActivity.kt   ← list, summary, filters, saving and restore mode
├── AppItemView.kt    ← a list row (drawn by hand for smooth scrolling)
├── DetailsDialog.kt  ← app card
└── Ui.kt, Theme.kt   ← glass design and themes
```

## Building

```sh
./gradlew assembleRelease      # → release build (R8), sign it with apksigner
./gradlew testDebugUnitTest    # source detection, sorting and file format tests
```

You need JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). No libraries.

## License

[GPL-3.0-or-later](LICENSE)
