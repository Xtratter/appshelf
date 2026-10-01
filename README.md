# 📚 AppShelf

[![Build](https://github.com/Xtratter/appshelf/actions/workflows/build.yml/badge.svg)](https://github.com/Xtratter/appshelf/actions/workflows/build.yml)

[Русский](README.ru.md) · **English**

A list of the apps installed on your phone — **name, package, where each one came from and when it was installed**,
sorted by name. Save it to a file, and after a factory reset or on a new phone open it again: AppShelf shows
which apps are missing and opens each one in the right store.

## Features

- **All installed apps**, sorted by name like a dictionary (upper/lower case and Ё/Е don't matter), with letter headers
- **Source** of every app: Google Play, F-Droid (and Droid-ify, Neo Store), Aurora Store, Obtainium, RuStore,
  GetApps, Galaxy Store, AppGallery, Amazon — or an **APK file**, including the app it was opened from ("APK · via Telegram")
- **Install date**, version and last update in the app card
- **Search** by name or package (tap anywhere on the top bar), **filter chips** by source, optional system apps
- **Save the list** as JSON (to restore later), Markdown (to read, e.g. in Obsidian) or CSV (for spreadsheets), or share it as text
- **Choose what goes into the list**: "Choose apps…" in the save dialog — checkboxes, search and select all / none;
  apps you leave out are not saved, shared or autosaved (you can also do it from an app's card)
- **WebDAV sync on a schedule**: Nextcloud, ownCloud, Yandex Disk, a NAS — every day, on chosen days of the week
  or every N days at a set time, optionally only over Wi-Fi; a folder per phone, keeps the last versions (10 by default),
  a missed send is made up as soon as possible;
  on a new phone open the list straight from the server
- **What changed** (menu ⋮): a timeline of installs and uninstalls for a week, a month, 3 months or all time (older uninstalls can be added from list versions on the server), and a comparison with another phone from the server
- **Updates from GitHub**: for apps with a GitHub link AppShelf checks the latest release (every 6 hours) — "↑ 1.11" in the list, an "Updates" chip and "Update to …" in the app card, which downloads the APK for this phone's processor and installs it
- **APK backups**: copies of apps you cannot get anywhere else (installed from APK files, or all apps) on the WebDAV server or in a folder; split apps are packed into one .apks; only changed versions are uploaded, optionally with every scheduled send; on a new phone "Install from the backup" (also in "Install all one by one")
- **Select several apps** (long-press a row): uninstall them one after another, leave out of / put back into the list, share
- **App settings backup**: save all AppShelf settings to a file or to this phone's folder on the WebDAV server (with or without the WebDAV password) and restore them — on this phone or another
- **Notes** in the app card — why you need the app, which account, what to set up; they travel with the list and via WebDAV to a new phone, show in the list and are searchable
- **Where to download**: your own links for each app (GitHub, Telegram, a site…) in the app card, saved with the list
  and shared by all phones via WebDAV; plus a shared [link catalog](https://github.com/Xtratter/appshelf-sources) on GitHub;
  a GitHub link installs the APK from the latest release right in AppShelf, as does a direct .apk link; GitHub / GitLab / Codeberg links can also go to Obtainium. In restore mode an APK backup comes first, then the link, then the store
- **Long-press help**: hold a finger on a button or item to see what it does
- **Save & restore** in one window: what is protected and when, two big buttons Save and Restore, plus collapsible Automation (WebDAV, autosave, APK backups) and What to save
- **Autosave**: pick a file once (a cloud drive, a memory card…) and the list is rewritten there every time you open the app
- **Restore mode**: open a saved list — see how many apps are missing, show only those, and tap to install each from its store — or **install all one by one**: AppShelf opens each missing app in turn and moves on as soon as it is installed
- **Haptic feedback** like a Taptic Engine: crisp clicks on buttons, a double click on success, a thud on errors; strength or off in menu ⋮ → Vibration
- **Material 3 Expressive design**: tonal surfaces, a hero summary card with a big count, list rows grouped by letter, large buttons that change shape when pressed, tonal filter chips and a floating "Save" button for the main action; the surfaces are slightly translucent (Theme → Transparency switches it off)
- **Soft edges**: the list fades into a progressive blur under the top bar and at the bottom of the screen, and so do scrolling windows (Android 12+; older versions fade without blur)
- **Themes**: follow system (default), light, dark, graphite, AMOLED black; colors follow the wallpaper (Android 12+); menu ⋮ → Theme

## Download

Ready-made APKs are on the [Releases](https://github.com/Xtratter/appshelf/releases) page. Android 8.0 or newer is required.

## Permissions

- `QUERY_ALL_PACKAGES` — without it Android 11+ shows an app just a few of the installed apps.
- `INTERNET`, `ACCESS_NETWORK_STATE` — only for WebDAV sync, and only to the server you enter; nothing else goes online.
- `REQUEST_INSTALL_PACKAGES` — to install an APK from a direct link (Android still asks you to confirm each install).
- `REQUEST_DELETE_PACKAGES` — to uninstall an app from its card (Android asks you to confirm).
- `VIBRATE` — for haptic feedback (can be turned off).
- `RECEIVE_BOOT_COMPLETED` — to restore the sync schedule after a reboot.

## How it works

Everything comes from Android's `PackageManager`: `firstInstallTime` / `lastUpdateTime` for the dates and
`getInstallSourceInfo()` (Android 11+) for the installer and the app that started an APK installation.
Files are written through the system file picker (Storage Access Framework), so no storage permission is needed.

```
app/src/main/java/io/github/xtratter/appshelf/
├── Apps.kt           ← reading installed apps
├── Model.kt          ← AppInfo and Source (installer package → store)
├── ListFile.kt       ← sorting and JSON / CSV / Markdown files
├── Links.kt, LinkStore.kt ← links to sources: your own, WebDAV links.json, the catalog
├── WebDav.kt         ← small WebDAV client (PUT, GET, PROPFIND, MKCOL)
├── Schedule.kt       ← when the next send is due
├── Sync.kt           ← alarm → send; waits for network if needed; SyncDialog.kt — the settings
├── Store.kt          ← opening an app in its store
├── MainActivity.kt   ← list, summary, filters, saving and restore mode
├── AppItemView.kt    ← a list row (drawn by hand for smooth scrolling)
├── DetailsDialog.kt  ← app card
└── Ui.kt, Theme.kt   ← glass design and themes
```

## Building

```sh
./gradlew assembleRelease      # → release build (R8), sign it with apksigner
./gradlew testDebugUnitTest    # tests: sources, sorting, file formats, schedule
```

You need JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). No libraries.

## License

[GPL-3.0-or-later](LICENSE).
