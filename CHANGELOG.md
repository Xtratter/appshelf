# Changelog

[Русский](CHANGELOG.ru.md) · **English**

## 1.4 — 2026-09-30

- **Save & export** — one window for every way to save the list: to a file (JSON / Markdown / CSV), share, autosave, WebDAV sync and which apps go into the list; each item shows its current state. It opens from the ⬇ button, the summary card and the menu
- **Versions on the server**: every send is a separate file with the date and time ("AppShelf-POCO F3_2026-09-30_135307.json"); choose how many to keep (10 by default, 1 — a single file as before), older ones are deleted
- **Leaving restore mode**: when the last missing app is installed, AppShelf offers to go back to your apps; with nothing missing the card shows one "Back to my apps" button; the Back gesture also leaves restore mode
- "Open a saved list" asks where from: a file or the WebDAV server

## 1.3.1 — 2026-09-30

- When Android, a VPN or a firewall blocks AppShelf from the network, WebDAV sync now says so and where to allow it, instead of "socket failed: ECONNREFUSED"
- Connecting tries every address of the server (IPv6 and IPv4), not only the first one
- The phone is named by its marketing name from the firmware ("POCO F3") when no device name is set in Android — in the WebDAV file name and in saved lists

## 1.3 — 2026-09-30

- **WebDAV sync** (menu → "WebDAV sync"): the list is sent to your own server — Nextcloud, ownCloud, Yandex Disk, a NAS
- **Schedule**: every day, on chosen days of the week, or every N days — at a time you pick; optionally only over Wi-Fi
- If the phone was off or offline at that time, the list is sent as soon as possible; the schedule survives reboots
- Each phone writes its own file ("AppShelf-POCO F3.json"), so a new phone doesn't overwrite the old list
- **Open a list from the server** — right into restore mode, the newest first
- "Test" and "Send now" buttons; the summary shows the next send time or the last error
- The password is encrypted with an Android Keystore key

## 1.2.1 — 2026-09-28

- The "Only missing" button in restore mode is no longer shifted down and cut off (the same fix applies to the Autosave button)
- When every app from an opened list is installed, the restore list says so ("All apps from the list are installed ✓") instead of "Nothing found"
- Saved lists name the phone by its device name when Android has one, e.g. "POCO F3 (M2012K11AG)" instead of "Xiaomi M2012K11AG"

## 1.2 — 2026-09-28

- **Choose which apps go into the list**: "Choose apps…" in the save dialog opens a list with checkboxes, search and "Select all / Select none" (it applies to the apps found by the search)
- The choice is remembered and applies to every save, "Share" and autosave; the summary shows how many apps are left out, and they look dimmed in the main list
- "Leave out of the list" / "Include in the list" in the app card

## 1.1 — 2026-09-28

- Tap the "AppShelf" title to switch to the next theme, long-press it to go back to the standard one; a hint shows which theme is on

## 1.0.1 — 2026-09-28

- The "Autosave" button in the summary no longer wraps and gets cut off: it shows ✓ when autosave is on, and the text shrinks to fit
- Source counts in the summary no longer break across lines (e.g. "F-Droid" / "14")

## 1.0 — 2026-09-28

First version:
- List of installed apps sorted by name, with letter headers: name, package, source and install date
- Source detection: Google Play, F-Droid and its clients, Aurora Store, Obtainium, RuStore, GetApps, Galaxy Store, AppGallery, Amazon, APK file ("via Telegram"), preinstalled
- Search, filter by source, optional system apps; app card with version, dates and actions
- Save the list as JSON, Markdown or CSV, share it as text, autosave to a chosen file on every start
- Restore mode: open a saved list, see and install the missing apps from their stores
- Material 3 "liquid glass" design with five themes
