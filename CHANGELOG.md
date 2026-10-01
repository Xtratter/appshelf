# Changelog

[Русский](CHANGELOG.ru.md) · **English**

## 1.33.3 — 2026-10-01

- Material 3 Expressive itself (colour scheme, themes, surfaces, windows, button squash) now comes from the shared [android-ui-kit](https://github.com/Xtratter/android-ui-kit) 1.1, the same as in DroidTop; nothing changes in how it looks

## 1.33.2 — 2026-10-01

- Soft edges, vibration and long-press help now come from the shared [android-ui-kit](https://github.com/Xtratter/android-ui-kit) (also used by DroidTop); nothing changes in how they work

## 1.33.1 — 2026-10-01

- F-Droid metadata (fastlane): up-to-date description of all features in English and Russian

## 1.33 — 2026-10-01

- **Faster start**: the list from the previous launch appears at once, and the fresh one is gathered in the background (asking the system about several apps in parallel) and quietly replaces it
- **Smoother scrolling**: the soft blurred edges use 2 blur steps instead of 4 — half the work in every frame
- **Smaller APK**: Kotlin reflection metadata is no longer packed in (212 → 206 KB)

## 1.32 — 2026-10-01

- **Long-press help**: hold a finger on a button or item and a bubble shows its name and what it does — the search bar, the ⋮ menu and its items, the Save button, filter chips, the summary, the app card buttons and the Save & restore window. It hides on a tap elsewhere or after 5 seconds (list rows keep long-press for selecting several apps)

## 1.31 — 2026-10-01

- **Save & restore reorganized**: at the top a "what is protected" card — when and where the app list was last saved, how many APK backups there are and when, whether the app settings were saved; then two big buttons **Save** (list to a file, share, send to the server now, APK backups now, app settings, links for the catalog) and **Restore** (list from a file or the server, app settings); below, collapsible **Automation** (WebDAV, autosave, APK backups) and **What to save** (apps in the list)

## 1.30 — 2026-10-01

- **Save and restore the app's own settings** (Save & restore → App settings): WebDAV server and schedule, APK backups, apps left out of the list, catalog, vibration and theme — to a file on the phone or to this phone's folder on the WebDAV server (settings.json); the WebDAV password is included only if you tick it (it is then stored as plain text). Restore from a file or from the server, picking the phone; everything is applied at once and the WebDAV schedule is set up again

## 1.29.2 — 2026-10-01

- The Transparency, Blur and Grain sliders are removed — the Theme window has the simple "Transparency" switch again, as in 1.28.5

## 1.29.1 — 2026-10-01

- Fixed a crash when letting go of the Blur, Transparency or Grain slider in the Theme window

## 1.29 — 2026-10-01

- Theme window: Material 3 Expressive sliders instead of the transparency switch — **Transparency** (0–60 %, 0 turns it off), **Blur** (how blurred the background behind windows and the soft list edges are) and **Grain** (test: a fine frosted-glass grain on the background and surfaces). A slider has a thick track, a slim vertical handle with a gap around it and an end dot; it ticks every 5 %, and the change is applied in place when you let go
- Theme switching checked on the recording — no flashes any more

## 1.28.5 — 2026-10-01

- Theme and transparency now switch truly in place: the screen and the open Theme window are recoloured on the spot instead of the screen being recreated — no more one-frame flash with a sharp background on each switch

## 1.28.4 — 2026-10-01

- Smoother theme switching (checked frame by frame): the background no longer flashes sharp for a moment and the Theme window text no longer doubles — the snapshot under the new screen is blurred and dimmed like the real background behind a window, and the old window picture disappears in the same frame as the new window appears

## 1.28.3 — 2026-10-01

- A fresh install now really starts with "Follow system": the theme and the transparency are no longer part of Android's backup, which used to bring the old theme back after reinstalling (all other settings are still backed up). After this update the theme is "Follow system" once more
- Switching the theme or the transparency no longer flashes: the old screen gently dissolves into the new one under the Theme window

## 1.28.2 — 2026-10-01

- The Theme window stays open when you switch the theme or the transparency: the screen is rebuilt in the new colours right under it, so you can try themes one after another

## 1.28.1 — 2026-10-01

- Theme list reordered: Follow system (default) first, then Light, Dark, Graphite and AMOLED; the former "Standard" theme is now called "Dark"

## 1.28 — 2026-10-01

- **Light transparency**: cards, the summary, chips, the top bar and windows are slightly translucent, so the coloured background shows through. It is on by default; the Theme window has a "Transparency" switch to turn it off for the whole interface
- The default theme for new installs is "Follow system" — dark or light as set in Android

## 1.27 — 2026-10-01

- **Material 3 Expressive design** (after Google's research on expressive design): tonal surfaces instead of glass; the summary is a hero card in the accent's tonal colour with a big heavy count; list rows of one letter form a group with large outer and small inner corners, and the letters are bigger; buttons are larger (52 dp) and squash from a pill into a rounded rectangle when pressed, springing back on release; filter chips are larger, the selected one is a tonal pill with a check; the main action "Save & restore" moved to a floating button at the bottom right, under the thumb (hidden while restoring, selecting, searching or installing one by one)

## 1.26.2 — 2026-10-01

- Menu ⋮: the "System apps" checkbox is now a real checkbox in the theme's colours instead of an old system picture that looked foreign in every theme; tapping it toggles at once and the menu stays open. Theme and Vibration moved up, System apps is second to last

## 1.26.1 — 2026-10-01

- Windows: no more hard line where the scrolling content ends (e.g. under "Leave out of the list" above "Close") — at the edges the content now blurs and dissolves into the window's glass over a longer, lower transition; the buttons stay in place

## 1.26 — 2026-10-01

- The top bar now looks like what it is — a search field: the magnifier on the left, "Search apps" in grey, the ⋮ menu on the right. Tapping anywhere on it opens the search, as before

## 1.25.4 — 2026-10-01

- Main list: the blur under the top bar and at the bottom edge is always there, also before you start scrolling
- Windows: the buttons ("Close" and others) are back in their place — the content no longer runs under them (in some windows the buttons ended up over the last items with an empty strip below); the content blurs softly above them when there is more to scroll

## 1.25.3 — 2026-10-01

- Soft edges follow scrolling right away (from the recording with voice comments): the top blur appears as soon as the list moves away from its start and the bottom one stays until its end — before, they got stuck in their old state until a hard fling
- Windows: at the top only a light blur remains, without the darkening into which titles like "2GIS" disappeared; the content scrolls under the window's buttons ("Close" and others) like messages under the input field in Telegram, with a constant blur beneath them — the last item still scrolls up to the buttons

## 1.25.2 — 2026-10-01

- Soft edges redone in the Telegram manner (from the recording with voice comments): the blur is now drawn by the list's own container in the same frame as the list, so nothing lags or slides even on a hard fling, and no dark patches appear at the top; four overlapping blur steps instead of three distinct ones — no visible line in the transition; a lighter fade at the top. The same in windows (What changed, the app card, Save & restore…)

## 1.25.1 — 2026-10-01

- Soft edges fixed (from the screen recording with voice comments): the bottom of the list no longer smears icons into vertical streaks that changed while scrolling; the blur at the top is now visible — the gradual part sits right under the top bar instead of hiding behind it, and the blur is stronger; scrolling windows (the app card, Save & restore…) now really get the blurred edges — before, AppShelf picked the dialog's hidden message area instead of its list

## 1.25 — 2026-10-01

- **Soft edges**: under the top bar and at the bottom of the screen the list no longer ends with a hard cut — it fades into a progressive blur, getting more blurred towards the edge (three steps) and dissolving into the background. The same at the top and bottom of scrolling windows (the app card, Save & restore, WebDAV, choosing apps…), only on the side where there is more to scroll. Android 12+; older versions get the fade without blur

## 1.24 — 2026-10-01

- **"Liquid glass" removed**: the refracting glass, buttons flowing into windows, the tilt highlight, the elastic press and the glass transparency setting are gone, together with the "Liquid glass" checkbox in Theme. What stays is the themes (standard, follow system, AMOLED, light, graphite) with the calm translucent glass of cards and windows. The app is smaller (about 190 KB) and simpler
- Vibration stays as it was

## 1.23.2 — 2026-10-01

- Window to window without a flash (checked frame by frame on the second recording): a window opened from another one (menu → Theme, Save & restore → WebDAV or Save to a file, the app card → note or link) flows out of the previous window, and the previous one closes only once the new one is already on screen — the blurred background no longer flashes sharp for 150–200 ms in between
- A row or button no longer stays blank for a moment when a window flows back into it: it reappears as soon as the drop reaches it, and the drop melts over it

## 1.23.1 — 2026-10-01

- Smoother flowing glass (checked frame by frame on a screen recording): a window that flows back into its button no longer shows twice — the system fade of the window is off, the glass drop takes its place at once; at the end of closing the button reappears earlier and the drop melts over it instead of covering it with a blurred patch; a window opened straight from another one (menu → Theme) now flows out of the previous window, without the background flashing sharp in between

## 1.23 — 2026-09-30

- **What changed** (menu ⋮): a timeline of installed and uninstalled apps by day — for a week, a month, 3 months or all time. Installs come from Android's install dates; uninstalls are noticed by AppShelf itself every time it opens, and older ones can be added from the versions of this phone's list on the WebDAV server
- **Compare with a phone**: pick another phone on the server and see what is only on this phone and only on that one; "Open its list to restore" switches straight to restore mode

## 1.22 — 2026-09-30

- **Updates from GitHub**: for installed apps with a GitHub link (yours or from the catalog) AppShelf checks the latest release, at most every 6 hours per repository (unchanged answers do not count against GitHub's limit). If it is newer than the installed version, the row shows "↑ 1.11" instead of the date, an "Updates · N" chip appears, and the app card offers "Update to 1.11 (GitHub)" — it downloads the APK for this phone's processor (or a universal one) from the release and installs it; if the release has no suitable APK, its page opens

## 1.21 — 2026-09-30

- **APK backups** (Save & restore → APK backups): copies of the apps themselves — for apps you cannot get anywhere else. Where: the WebDAV server (folder apk/) or a folder on the phone. What: apps from APK files or all apps except system ones. One file per app with the latest version ("package__versionCode.apk"); split apps are packed into one .apks; the same version is not uploaded twice and older ones are deleted. "Back up now" shows progress and can be stopped; "Update backups every time the list is sent to WebDAV" keeps them fresh in the background
- **Restoring from a backup**: when a list is opened, AppShelf checks which missing apps have backups — the list shows "Backup ›", the app card offers "Install from the backup" (split apps are installed in one session), and "Install all one by one" uses the backup after your links and before the store
- The WebDAV client streams large files instead of holding them in memory

## 1.20 — 2026-09-30

- **Install all missing apps one by one**: in restore mode, "Install all one by one (N)" opens the first missing app where it comes from (your link or the catalog's, otherwise its store); a glass bar at the bottom shows "Installing 3 of 31 — Telegram". Come back to AppShelf once it is installed and the next one opens by itself; if it is not installed, choose Try again, Skip or Stop. At the end: "Done: N of M installed"

## 1.19 — 2026-09-30

- **Select several apps**: long-press a row to start selecting, then tap rows to mark them (a highlight and a check on the icon). A glass bar at the bottom shows how many are selected and offers: All (everything shown, with filters and search), Leave out / Put back into the saved list, Share (as text, with links and notes) and Uninstall — AppShelf asks once, then Android confirms each app in turn; cancelling one stops the rest, system apps are skipped. ✕ or Back leaves the selection

## 1.18 — 2026-09-30

- **Notes for apps**: a "Note" block in the app card — why you need the app, which account, what to set up after installing. Notes are saved inside every list (JSON, CSV, Markdown) and shared through links.json on the WebDAV server together with your links, so on a new phone they are right there. The start of the note shows in the list row, and search finds apps by their notes too

## 1.17.3 — 2026-09-30

- Vibration on phones that only have the manufacturer's click effects (their strength is fixed by the phone): the level now changes what vibrates — Light: windows, success and errors; Medium: also buttons and list rows; Strong: also chips, checkboxes and the slider, and a window opens with a double click. The Vibration window explains how the level works on this phone

## 1.17.2 — 2026-09-30

- The drop now closes into exactly the visible glass it came from: for a list row it is the card itself (without the row's transparent margins) with the card's corner radius — before it flowed into the whole row and looked bigger
- Vibration window: "How to vibrate" — Auto, the manufacturer's click effects (a crisp tap, strength set by the phone), vibration primitives (adjustable strength) or a simple pulse; unsupported ways are marked, and choosing one plays a sample. Auto uses primitives when the phone has them (so the strength setting works), otherwise the manufacturer's effects

## 1.17.1 — 2026-09-30

- The glass drop now flows back exactly into the size of the button when a window closes (and starts exactly from it when it opens): the mercury bridge only appears mid-way and is gone at both ends, so it no longer inflates the shape
- Vibration uses the manufacturer's ready-made click effects first — they are tuned for the phone's motor, so a linear motor gives a crisp tap instead of a buzz (strength: Light → tick, Medium → click, Strong → heavy click); then vibration primitives, then a short pulse. The Vibration window shows which way this phone uses
- Glass buttons in windows (the app card and others) no longer stretch after the finger; the light press remains

## 1.17 — 2026-09-30

- **The highlight follows the tilt of the phone**: the light "hangs" in the room — tilt the phone and the bright rim on every piece of glass slides round accordingly (gravity sensor, only while AppShelf is on screen with "Liquid glass" on)
- **A mercury bridge**: when a button flows into its window, the drop does not detach at once — a glass bridge stretches between them, thins and snaps, and what is left of the button melts away; when the window closes, the bridge grows back and merges into the button
- **Elastic press**: glass buttons, chips, list rows and window items give under the finger — they sink in slightly, the lens squeezes the background harder and the rim brightens; moving the finger drags the glass elastically after it, and on release it springs back

## 1.16 — 2026-09-30

- **Haptic feedback** like a Taptic Engine — short, crisp clicks of different character: a click on buttons, window items, list rows and the top bar; a light tick on filter chips, checkboxes and the transparency slider (every 5%); a rising pulse when a button flows into its window and a falling one when it flows back; a double click on success (sent to the server, saved, installed, uninstalled) and a thud on errors. It fires when the finger lifts, not when you start scrolling
- **Menu ⋮ → Vibration**: Off, Light, Medium (default) or Strong; choosing a level plays a sample. Uses crisp vibration primitives with adjustable strength on Android 11+ where the phone supports them, otherwise a short pulse of that strength

## 1.15.1 — 2026-09-30

- The whole top bar except the ⋮ button opens the search — the title, the empty space and the magnifier; a tap anywhere on it shows a ripple

## 1.15 — 2026-09-30

- Tapping the "AppShelf" title opens the app search (like the magnifier), no longer switches the theme; the theme is chosen in menu ⋮ → Theme
- The font choice is gone together with the bundled Cascadia Mono — the system font is used everywhere, and the app is almost three times smaller (about 160 KB)

## 1.14.2 — 2026-09-30

- "Liquid glass": when scrolling inside a window (for example "Save & restore"), the background seen through the glass buttons no longer slides along with them — the glass is redrawn while scrolling, so the background stays put like behind real glass

## 1.14.1 — 2026-09-30

- The flowing glass is no longer empty: the window's content (text, buttons, cards) travels inside the drop, stretched to its shape — it fades in while the drop grows and fades out while it flows back into the button; the same for the search field

## 1.14 — 2026-09-30

- **The glass really flows now**: the tapped button (the ⋮ menu, "Save & restore", an app in the list) turns into its window — a glass drop flows out of it and stretches into the window, the leading edge first and the trailing one catching up; when the window closes, it flows back into the button. The search field flows out of the magnifier and back. The background behind a window is dimmed and blurred only once the window is in place
- The swelling of buttons under the finger and the pop-in of windows from 1.13 are gone

## 1.13 — 2026-09-30

- **"Liquid glass" flows like a drop**: windows (the ⋮ menu, app cards, Theme…) grow out of the spot you tapped with a springy settle; the search field flows out of the magnifier button and back into it; glass buttons, chips, list rows and window items swell slightly around the finger and spring back when released. Only while "Liquid glass" is on
- The Theme window no longer mentions iOS

## 1.12.3 — 2026-09-30

- Glass transparency changes live while you move the slider: the glass fill follows at once, and the blur of what is behind the window catches up in the background (the screen is captured once when the window opens, not on every change)

## 1.12.2 — 2026-09-30

- Search: while the search field is open, the summary card and filter chips are hidden, so the apps found appear right under the search field; the search covers all apps regardless of the chosen filter, and everything comes back when the search is closed

## 1.12.1 — 2026-09-30

- "Liquid glass": the dark patches beside the ends of the top bar in light themes are gone — the bar's shadow was cut off by its container into a rectangle; now it falls softly around the whole bar

## 1.12 — 2026-09-30

- **Uninstall apps** from the app card ("Uninstall", in red): Android asks to confirm, then the list updates. Not shown for system apps and for AppShelf itself
- AMOLED theme: filled buttons and selected chips are black with a thin light outline and light text instead of the accent color
- "Liquid glass": no more dark corners beside the ends of the top bar in light themes — the shadow now follows the fully rounded shape of the glass

## 1.11 — 2026-09-30

- **"Liquid glass" is now a checkbox in Theme** instead of a separate theme — it works with every theme (standard, follow system, AMOLED, light, graphite; in the light theme the glass is light). On by default; on Android older than 13 the checkbox is unavailable and the regular glass is used. The "Liquid glass" theme chosen before becomes standard + checkbox, so nothing changes on screen

## 1.10 — 2026-09-30

- **"Liquid glass" is the default theme** and no longer experimental; it comes first in the theme list, and long-pressing the title returns to it (on Android older than 13, where its shaders are unavailable, the default stays standard)
- **Font choice** (menu → Font): Cascadia Mono — a monospaced font by Microsoft in the spirit of Consolas, with full Cyrillic — by default, the system font or system monospace. Consolas itself is a paid font and cannot be bundled with a free app

## 1.9.1 — 2026-09-30

- "Liquid glass": the top bar no longer flashes black while scrolling — what is under it is drawn into one of three pictures in turn, and only a finished one goes on screen (before, the screen sometimes caught the single picture halfway through being redrawn)

## 1.9 — 2026-09-30

- **Glass transparency** for the "Liquid glass" theme: Theme → "Transparency…" — a slider from frosted to clear. It sets how blurred the list is under the top bar and behind windows and how dense the glass fill is; the window itself changes as you move the slider, the whole screen when you close it. "Default" returns the middle

## 1.8.9 — 2026-09-30

- "Liquid glass": cleaner, grain-free blur under windows and the top bar — the snapshot behind windows is twice as sharp, sampled smoothly, and blurred with a proper smooth (near-Gaussian) blur instead of scattered samples that caused grain and ripples

## 1.8.8 — 2026-09-30

- "Liquid glass": the rim is back to the 1.8.5 look — no rainbow outline and no heavy shading; windows keep refracting the screen behind them (1.8.7)

## 1.8.7 — 2026-09-30

- "Liquid glass" windows refract what is behind them: when a window opens, AppShelf takes a snapshot of the main screen, and the window with every card and button in it shows it blurred and bent at its edges with color fringes — like the top bar over the list
- The bright rim of every glass element splits light like a prism — warm on the outside, cool on the inside — so the fringe is visible even over a plain background; stronger color separation in the refraction
- The shading added in 1.8.6 is much softer

## 1.8.6 — 2026-09-30

- "Liquid glass": the edge of every glass element now has visible thickness even over a plain background — a brighter band on the side facing the light, a shaded band on the far side and a thin dark line just inside the bright rim; over content (the top bar above the list) the edge still bends what is under it

## 1.8.5 — 2026-09-30

- "Liquid glass": the same edge as on the top bar everywhere — windows, cards and buttons inside them get the thin bright rim with the highlight top-left, the reflection bottom-right and the soft inner glow (the fill stays translucent)

## 1.8.4 — 2026-09-30

- No faint light "frame" inside windows anymore: glass windows cast no system shadow (it showed through the translucent glass near the edges) and the dialog's inner panels have no backing of their own

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
