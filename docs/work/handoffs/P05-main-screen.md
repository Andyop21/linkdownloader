# Handoff — P05 The main screen, wired end to end

Date: 2026-11-15 · Prompt: [P05](../../../prompts/finished/P05-main-screen.md)

## What changed

All in `31651c9`:

1. **`LinkParser`** (`link`, pure Kotlin): `firstLink(text)` finds the first `http`/`https` URL,
   stops at whitespace, quotes and angle brackets, drops trailing sentence punctuation and closing
   brackets that have no partner in the URL (so `Clip_(film)` keeps its bracket), requires a valid
   host (name, IPv4 or IPv6, optional port and user info) and lowers the scheme. `www.` without a
   scheme is not a link, as the prompt proposed. 21 tests with share-text shapes.
2. **`UiState` and `MainViewModel`** (`ui`): `UiState(bar, status, about)`, with `Status` one of
   `Idle`, `NoLink(fromClipboard)`, `Preparing`, `Choosing`, `Downloading`, `Saving`, `Done`,
   `Failed`. Two small departures from the prompt's shape: `Saving` also carries position, count
   and progress, so the bar does not jump back to indeterminate while saving; `Done` carries the
   job ID (for one haptic tick per job) and the saved count. Calls: `onPaste`, `onSubmit`,
   `onBarChanged`, `onCancel`, `onChoose`, `onDismissChoice`, plus `onOpenChoice` (the "Choose"
   notification), `onOpenAbout`, `onCloseAbout`, `onUpdateEngine`. The ViewModel talks to a small
   `Downloads` interface; `WorkDownloads` maps `WorkInfo` to `DownloadJob`. 36 tests against a fake,
   one or more per behaviour row B1–B11 that is not pure UI.
3. **`MainScreen`**: `HeaderWithMascot`, `LinkBar`, `PasteButton`, `StatusCard` take state and
   callbacks only; `MainContent` lays them out in a scrolling column at most 560 dp wide.
   `MainScreen` holds the platform glue: clipboard read only on Paste, **Open** with
   `ACTION_VIEW` and a snackbar when no app can play the video, a `Confirm` haptic once per
   finished job, focus cleared on Paste and Go. The mascot is a drawn placeholder face per mood.
4. **Strings**: every screen text in `res/values/strings.xml` (status card, choice sheet, About
   sheet, the no-app message); failures reuse P04's `FailureReason.messageRes`.
5. **Notification permission**: `AskForNotificationsOnce` asks on API 33+ the first time a
   download runs and records that it asked, so a refusal is never asked again.
6. **Choice sheet** (`ChoiceSheet`, `Thumbnails`): modal bottom sheet, post title, adaptive grid of
   square thumbnails with length badge and selected mark, all selected at first, **Select all**,
   **Download (n)** disabled at zero, dismiss calls `onDismissChoice`. Thumbnails: plain
   `HttpURLConnection` on `Dispatchers.IO`, `BitmapFactory` with a power-of-two sample size near
   360 px, 8 MB cap, kept in memory for the sheet's life, placeholder on failure (URLs are not
   logged). Each tile reads "Video 2, 0:08" with the state "selected" / "not selected".
7. **About sheet**: long-press on the header (and a TalkBack long-press action "Show app info");
   app version, yt-dlp version, **Update yt-dlp** → "Checking…", then "Up to date", "Updated to
   …" or "Couldn't check right now…". For that, `UpdateWorker` now returns an
   `EngineUpdateResult` in its output and `DownloadQueue` has `observeWork(id)`.
8. **Removed** `debug.DebugDownloadActivity` and `app/src/debug`.
9. Download side, for the screen: a `Choose` result now carries its link, so the chosen videos can
   be queued even when the app was opened from the notification after a process death.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, lint clean, 174 JVM tests
  pass (62 new: `LinkParserTest` 21, `MainViewModelTest` 36, `DurationTextTest` 3,
  `UpdateWorkerOutputTest` 2, one changed `DownloadDataTest` case).
- **On the owner's phone** (Samsung SM-S948B, Android 17 / API 37, wireless debugging), debug build,
  driven over `adb` (taps, `input text`, the keyboard's own Go key, `uiautomator dump`). Test text
  was put on the clipboard by copying it from the bar, so the owner's clipboard was never read.
  Public YouTube videos only ("Me at the zoo", 19 s; "Big Buck Bunny", the Blender film).

| # | Tried | Result |
|---|---|---|
| B1 | Clipboard "Watch this: https://www.youtube.com/watch?v=… so old!", tap Paste | The bar showed only the link, "Getting the video ready…" at once, Paste disabled, mascot "working" |
| B2 | Clipboard "no link here", bar holding "typed", tap Paste | Bar still "typed"; "No link on the clipboard / Copy a video link first, then tap Paste again."; nothing queued |
| B3 | `www.example.com` + Go; then a YouTube link + Go (adb Enter and the keyboard's Go key) | "That doesn't look like a link"; with a link the download started and the keyboard closed (real Go key) |
| B4 | Big Buck Bunny via Go | Title shown in two lines, "Downloading · 21 %", "· 24 %", determinate bar, Paste disabled, Cancel shown |
| B5 | Cancel on the card at ~25 % | Status back to "Ready when you are" at once; no new file in `Download/`, empty `cache/jobs`, no yt-dlp process |
| B6 | "Me at the zoo" finished | "Saved to Downloads / Me at the zoo.mp4", **Open**, mascot smiling; Open showed the system chooser and played in Samsung's Video Player |
| B7 | `https://example.com/`; then a YouTube link with this app's network cut | "Couldn't download / This link doesn't lead to a video I can download."; "…No internet right now. Try again when you're back online."; mascot worried |
| B8 | Download started, Home pressed | Ongoing notification (foreground service) with the title; it finished as "Saved to Downloads" with the file name |
| B9 | Mid-download, the activity relaunched with `NEW_TASK | CLEAR_TASK` (a fresh ViewModel) | Showed the running download ("Downloading · 35 %", Paste disabled), then its "Saved to Downloads" |
| B10 | Long-press on the header | Sheet: "App version 0.1.0", "yt-dlp version 2026.10.26", **Update yt-dlp** → "Up to date" |
| Permission | First download with `POST_NOTIFICATIONS` revoked and its flags cleared | The system dialog appeared once; "Don't allow" let the download finish; the next download did not ask again |
| Layout | Font scale 200 %, portrait and landscape | Everything readable; in landscape the column is centred and scrolls to the status card |
| Targets | `uiautomator` bounds | Paste 64 dp tall, bar 64 dp, Open and Cancel 48 dp, header 56 dp |

  Screens in words: dark theme, a round lavender face beside "Link Downloader", a pill-shaped
  outlined bar labelled "Video link", a wide lavender **Paste** pill, and a rounded grey card with
  a bold line ("Ready when you are"), a grey detail line, the progress bar and the button row. The
  About sheet slides up with the app name, two label/value rows and a full-width **Update yt-dlp**
  pill. No screenshot is committed.

  The four test videos were deleted from the phone afterwards (through MediaStore), the font scale
  and rotation were restored, and the per-app network block was turned off.
- **Not checked** (the owner chose to skip them this session): the must-work sites other than a
  YouTube video (a YouTube Short, Instagram, X, TikTok); B11 on a real post with several videos
  (choose one, some, all), so the choice sheet and its thumbnails were never seen on a device (the
  ViewModel side is unit-tested); TalkBack; the haptic tick (needs a hand on the phone); Cancel from
  the notification (two tries ended because the download finished first; P04 checked the action
  itself, and the screen's reaction to an outside cancel is unit-tested); opening the app from the
  "Choose" notification; the light theme; a phone with no video player.

## Docs updated

- `docs/architecture.md`: § *Code layout* (`ui` contents, no debug activity, what is JVM-tested),
  new § *The screen* (UiState, `Downloads`, which job is shown, choosing, platform glue,
  components, both sheets), § *Background work* (`Choose` carries the link, `observeWork`,
  `UpdateWorker` output), § *Tests*.
- `docs/product.md` B3: the bar then holds just the link; its own no-link message.
- `docs/development.md`: § *Background downloads by hand* replaced by § *Trying the screen by hand*.
- `CHANGELOG.md` *Unreleased*: the screen.

## Bugs filed

None.

## Open questions

- **Wording.** All screen texts are in `res/values/strings.xml` under "The screen", "Status card",
  "Choice sheet" and "About sheet"; the owner may want to read them once. In particular: the idle
  card ("Ready when you are / Copy a video link, then tap Paste."), the two no-link messages, and
  "Couldn't check right now. Try again when you're online."
- **Result notification while the app is open** (from P04): still posted, so a finished download
  shows both the card and a notification. Cancelling notification 2 when the screen shows the
  result would be a small change.
- **Old choices file** (from P04): still left in `cache/choices/` until the next post replaces it.
- **Update while downloading.** **Update yt-dlp** queues behind a running download (DEC-006), so
  the sheet says "Checking…" until the download ends. Fine for now; a hint could say so.
- **Hardware keyboard.** With a hardware Enter (or `adb` Enter) the bar keeps focus after Go and
  the soft keyboard switches to its read-only layout; the on-screen Go key behaves correctly.
- **Bar after a fresh start.** A download picked up after a fresh start shows its title, but the
  bar stays empty, since WorkManager's info does not carry the link.
- **P06**: the outlined bar's floating label sits close to the pill's curve at large font sizes;
  the "Video link" label may read better as a placeholder only.

## Next

P06 restyles the screen: `ui.theme` (palette, Nunito, shapes, motion), `Mascot` (replace the
drawn placeholder; moods come from `Status.mood`), and the components in `Components.kt`,
`StatusCard.kt`, `ChoiceSheet.kt` and `AboutSheet.kt`, whose structure should not need to change.
Before or during P06, run the checks skipped here on the phone: the four must-work sites, a post
with several videos (one, some, all, and the thumbnails), TalkBack, the haptic tick and the
"Choose" notification.
