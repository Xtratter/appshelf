# Changelog

[Русский](CHANGELOG.ru.md) · **English**

## 1.8.3 — 2026-09-30

- "Liquid glass": the top bar is drawn from a copy of what is under it with the same lens as the cards — it no longer drifts or gets cut off
- Everything is glass: buttons, filter chips and buttons in windows; colored buttons become tinted glass with a highlight
- The ⋮ menu is a glass window instead of the solid system popup
- Windows no longer show a rectangular "frame" inside the rounded glass (the extra blur under the window is off; the screen behind is still blurred)
- The "liquid glass" background is plain and even (Material style) — no bright spots moving inside the cards while scrolling

## 1.8.2 — 2026-09-30

- "Liquid glass": the top of the bar is no longer cut off — the light frosting is now done by the glass shader itself instead of a separate blur, which shifted the lens

## 1.8.1 — 2026-09-30

- "Liquid glass": the top bar no longer drifts while scrolling — it is drawn as one layer and redrawn whole
- Closer to iOS 26: the middle of the glass stays clear, only a narrow band at the edge bends the content like a thick lens; a thin bright rim (strongest top-left, a reflection bottom-right) with a soft inner glow; a light blur; the floating bar casts a soft shadow; no more dark arcs along the edge

## 1.8 — 2026-09-30

- **"Liquid glass" theme (experimental)** in the style of iOS 26, Android 13+: the top bar is real glass — the list scrolls visibly beneath it and bends at the rounded edges like a lens, with a light blur, color fringes and bright rim highlights; cards refract the vivid background under them, and the refraction flows as you scroll. It is in the theme list and in the title-tap cycle; on older Android it looks like the regular glass

## 1.7 — 2026-09-30

- **Install an APK right from AppShelf**: a direct link to an .apk file is downloaded (with progress and Cancel), checked that it contains the same app, and handed to Android's installer. The first time AppShelf asks for the "Install unknown apps" permission and continues by itself when you come back
- **One entry point — "Save & restore"** on the main screen: saving (file, share, autosave, WebDAV, apps in the list, links for the catalog) and restoring (from a file or the server) in one window. The ⬇ button and the duplicate menu items are gone
- The link window has "Link" and "Label" captions; a link pasted into the label is caught with a hint

## 1.6 — 2026-09-30

- **Where to download** in the app card: your own links for any app — GitHub, GitLab, Codeberg, Telegram, 4PDA, a site or a direct .apk; the link from the clipboard is filled in by itself, long-press to change or delete. Repositories open on the latest release
- **Obtainium**: if it is installed, GitHub / GitLab / Codeberg links can be added to it — it installs the app and keeps it updated
- Your links travel everywhere: they are saved inside every list (JSON, CSV, Markdown with clickable links) and shared by all phones through "links.json" on the WebDAV server; opening a list adds its links to yours, the newer change wins
- **Link catalog** (menu): a shared sources.json from GitHub — [Xtratter/appshelf-sources](https://github.com/Xtratter/appshelf-sources) by default, any address can be set. Downloaded whole at most once a day; your own links always come first
- **Restore mode**: a missing app with a link shows "GitHub ›" in the list, and its card offers "Install: GitHub" before the store
- "APK without a link" filter — to fill in links for apps from APK files
- **Save & export → Links for the catalog** — your links as a ready sources.json for the catalog repository

## 1.5 — 2026-09-30

- **A folder for each phone on the WebDAV server**: lists go to "AppShelf/POCO F3/AppShelf_2026-09-30_135307.json"; the folder name is set in the WebDAV settings (the phone name by default) and is created automatically
- Opening from the server: first the phone ("POCO F3 · 10 versions · newest …"), then the version; with one phone — straight to its versions. Files from earlier versions of AppShelf in the main folder are under "Other files"
- **A missed scheduled send still happens**: it stays pending until a send succeeds — after a reboot, without network or VPN, when the server refused or the alarm did not fire. A background check every few hours (and every app launch) sends it as soon as possible; the summary shows "the scheduled send is pending"

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
