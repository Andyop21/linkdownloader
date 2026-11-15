# Architecture

The plan the code follows. Until a part is built, this page describes its intended shape; once it
exists, the code and this page must agree.

## Stack

| Part | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Extraction and download | [youtubedl-android](https://github.com/yausername/youtubedl-android) `library` module only: yt-dlp with bundled Python and QuickJS. **Not** its `ffmpeg` or `aria2c` modules. |
| Joining video and audio | Android's own `MediaExtractor` + `MediaMuxer` |
| Background work | WorkManager, as a foreground worker with a notification |
| Saving files | MediaStore `Downloads` collection |
| JSON | kotlinx.serialization |
| Build | Gradle (Kotlin DSL) with a version catalog at `gradle/libs.versions.toml`; Android Gradle Plugin 9 with its built-in Kotlin (no `kotlin-android` plugin) and the Kotlin Compose compiler plugin, whose version sets the Kotlin version; Java and Kotlin toolchain 17 |

Why: [DEC-001](work/decisions/DEC-001-kotlin-compose-ytdlp.md) (stack) and
[DEC-002](work/decisions/DEC-002-no-ffmpeg-merge-with-mediamuxer.md) (no ffmpeg).

## Android targets

- Application ID and namespace `io.github.andyop21.linkdownloader`; app label "Link Downloader".
- `minSdk 29` (Android 10): from 29 on, saving to `Downloads/` through MediaStore needs no storage
  permission.
- `targetSdk` and `compileSdk`: the latest stable API level the Android Gradle Plugin supports
  (37), pinned in the version catalog together with `minSdk`.
- ABIs: release builds `arm64-v8a` only; debug builds `arm64-v8a` and `x86_64` so the app also runs
  on a PC emulator. Lint's `ChromeOsAbiSupport` check is suppressed on that one release line, since
  ChromeOS is not a target.
- youtubedl-android runs Python and QuickJS as executables from the native library directory, so
  native libraries must be extracted at install time: `packaging.jniLibs.useLegacyPackaging = true`
  in the app module. AGP writes `android:extractNativeLibs="true"` into the packaged manifest from
  that setting; the source manifest does not carry the attribute (AGP warns when it does).
- Release build: signed with the key named in `keystore.properties` (outside git; without it the
  build stops at `checkReleaseSigning`), shrunk with R8 and resource shrinking. AndroidX,
  WorkManager, Compose and kotlinx.serialization bring their own R8 rules; `app/proguard-rules.pro`
  adds youtubedl-android's (the library ships none): the library kept whole, Jackson's annotations
  and optional handlers, Commons Compress's ZIP extra fields, and the app's serializers. Versions:
  `versionCode = major*10000 + minor*100 + patch` ([development.md](development.md) § *Versions*).

## Code layout

One Gradle module, `app`. Packages under `io.github.andyop21.linkdownloader`:

| Package | Holds |
|---|---|
| (root) | `LinkDownloaderApp` (initialises youtubedl-android once, creates the notification channel, schedules the yt-dlp update) and `MainActivity` |
| `ui` | `MainScreen` and its components (`HeaderWithMascot`, `LinkBar`, `PasteButton`, `StatusCard`), `MainViewModel`, `UiState`, `Downloads` (the ViewModel's view of the download side), the `Mascot`, the line icons, the choice sheet with its thumbnail loader, the About sheet and the one-time notification permission request |
| `ui.theme` | Colours (both schemes, the status card tones, the mascot's colours), Nunito type, shapes, motion and reduced motion, `LinkDownloaderTheme` |
| `link` | `LinkParser`: finds the first `http`/`https` link in a piece of text. Pure Kotlin. |
| `download` | The download pipeline (below), `DownloadWorker`, `UpdateWorker`, the notifications and `DownloadQueue` (§ *Background work*) |

Dependencies point inward: `ui` uses `link` and `download`; `link` uses nothing. Pure-Kotlin parts
(`LinkParser`, `VideoJson`, `FormatPicker`, `FailureClassifier`, `FileNamer`, `ProgressTracker`,
`Container`, `DownloadProgress`, `throttleLatest`, `EngineUpdateRule`, `ChoicesFile`) take no
Android types, so they are tested on the JVM; so are `DownloadData` and `UpdateWorker`'s output,
which only use WorkManager's `Data`, and `MainViewModel`, which reaches the download side only
through the `Downloads` interface.

## Download pipeline

`VideoDownloader.download(link, jobId, selection, onEvent)` is the one entry point;
`DownloadWorker` calls it. It reports `Preparing`, `TitleKnown(title, position, count)`,
`Progress(fraction)` and `Saving`, and returns one of:

- `Saved(videos, failed)`: each saved video's uri, file name and title, and how many chosen videos
  could not be saved;
- `ChooseVideos(title, choices)`: the link is a post with several videos and no `selection` was
  passed; nothing was downloaded (below);
- `Failed(reason)`.

Every step runs off the main thread, in a per-job folder, `cache/jobs/<jobId>`, that is deleted
when the job ends, whatever the outcome.

```text
link
 → Check       no network                                  → Failed(NoInternet), nothing started
 → Extract     YtDlp: yt-dlp -J --no-playlist --flat-playlist <link>
                                                           → info.json in the job folder → VideoJson
 → (Several)   a post with several videos: ChooseVideos, or the chosen entries one by one
 → Pick        FormatPicker.pick(video)                    → Plan: Single(format) | Pair(video, audio)
 → Fetch       YtDlp: yt-dlp --load-info-json info.json -f <ids> --fixup never --no-mtime
                      -o <job>/media/%(format_id)s.%(ext)s → one or two files in the job folder
 → Assemble    Pair             → MediaAssembler.join: video + audio into one MP4
   and save    Single MPEG-TS   → MediaAssembler.remux into MP4
               Single MP4/WebM  → copied as is
               DownloadsSaver: a pending MediaStore Downloads entry; the MP4 is written straight
               into its file descriptor, then IS_PENDING is cleared → content Uri
```

| Class | Does |
|---|---|
| `VideoDownloader` | Runs the steps, the 403 retry, cleanup and cancellation; maps every error to a `FailureReason` |
| `YtDlp` | Calls youtubedl-android on `Dispatchers.IO`; turns its errors into `DownloadException`s and destroys the process on cancel |
| `VideoJson` | The few fields of yt-dlp's JSON the pipeline reads (kotlinx.serialization, unknown keys ignored) |
| `FormatPicker` | Chooses the plan (DEC-003) |
| `ProgressTracker` | Turns yt-dlp's per-file lines into one overall fraction |
| `Container` | Names a file's real container from its first bytes |
| `MediaAssembler` | Joins or remuxes with `MediaExtractor` + `MediaMuxer`, keeping the video's rotation |
| `DownloadsSaver` | Creates, writes, publishes or deletes the `Downloads/` entry |
| `FileNamer` | Turns the title into the file name |
| `FailureClassifier` | Maps yt-dlp's error text and exceptions to a `FailureReason` |

- **Extract once.** The JSON from the first call is written to the job folder and handed back to
  yt-dlp with `--load-info-json`, so the site is only queried once. The one exception: when the
  fetch fails with HTTP 403 (YouTube sometimes refuses every URL of one extraction), the job
  extracts once more, picks again and fetches again; a second 403 fails the job.
- **Posts with several videos** (product B11). With `--flat-playlist`, a post with several videos
  (an X post, an Instagram carousel) comes back as a playlist whose entries are full videos with
  their formats, while a real playlist or profile only lists links, without formats, and fails as
  *not supported*. A post with one video entry is downloaded at once. With more, and no
  `selection`, `download` returns `ChooseVideos`: each video's position, title, thumbnail URL and
  length. The screen offers them (B11) and enqueues a second download with the chosen positions.
  That second job extracts the post again (the first job's folder is gone, and the step is quick
  for these sites), writes each chosen entry to its own `entry-<n>.json` for `--load-info-json`,
  and downloads them one after another through the same pick, fetch, assemble and save, each into
  a fresh `media/` folder. One video's failure does not stop the others; `Saved.failed` counts it,
  and only when every chosen video fails is the job `Failed`. Progress splits evenly between the
  chosen videos.
- **`--fixup never`** on every fetch. The library always passes `--ffmpeg-location` to a binary the
  app does not ship, and the bundled yt-dlp crashes in its ffmpeg fixups without this option.
- **FormatPicker** (pure, unit-tested) returns the first of: the best *pair* of a video-only H.264
  or HEVC stream (HTTPS before HLS) and an AAC audio-only stream (original language first, DRC
  last); the best *single* format with both video and audio (MP4 before webm). The 1080p cap
  applies to the frame's short side, so vertical videos keep their 1080×1920. Ties go to the later
  entry in yt-dlp's `formats` list. It never selects a format MediaMuxer cannot write; with nothing
  left the job fails as *not supported*. A single MP4 needs H.264/HEVC video and AAC audio, or codecs
  the site left unstated; a single WebM is kept as is, whatever its codecs. Only formats yt-dlp
  downloads without ffmpeg are considered (`https`, `http`, `m3u8_native`, `m3u8`,
  `http_dash_segments`). The full rules, and the probe results they come from, are in
  [DEC-003](work/decisions/DEC-003-engine-probe-results.md).
- **Assemble** decides by the file's first bytes (`Container`), not its extension: HLS formats
  arrive as MPEG-TS in a file named `.mp4`. A pair is always joined, which also turns YouTube's DASH
  m4a audio into a normal MP4. Samples are copied, never re-encoded, interleaved by time.
- **Save.** The entry is created in `Download/` with `IS_PENDING = 1`, the name from `FileNamer`
  and MIME type `video/mp4` (or `video/webm`), so no other app sees a half-written file. MediaStore
  may add ` (1)` to a name that is taken; the name it chose is the one returned.
- **File name.** The title with `/ \ : * ? " < > |` and control characters removed, whitespace
  collapsed, emoji kept, cut to 100 code points and 200 UTF-8 bytes; `<site>-<video id>` when the
  title leaves nothing, and `video` when there is not even that.
- **Progress.** youtubedl-android's callback restarts at 0 % for each file of a pair, and each file
  starts with a `[download] Destination:` line. The callback also repeats its last percentage on
  lines that carry none, so `ProgressTracker` reads the percentage from the line itself and ignores
  HLS's opening `100.0% … (frag 0/N)` line. It turns the per-file percentages into one overall
  fraction that never goes backwards: fetch takes 0–95 %, weighted by `filesize`, else
  `filesize_approx`, else equally (HLS formats state neither); assembling and writing take 95–99 %;
  the published entry is 100 %. A fetch after a fresh extraction starts again from zero, so
  `VideoDownloader` only passes on progress that is ahead of the last value sent.
- **Cancel.** Cancelling the coroutine that runs `download` destroys the yt-dlp process
  (`destroyProcessById(jobId)`), stops the muxer or the copy between samples, deletes the pending
  MediaStore entry and the job folder, and ends in the `CancellationException`. If the process is
  killed outright, MediaStore removes the stale pending entry itself after about a week.

## The screen

`MainViewModel` exposes one `UiState`: the bar's text, a `Status` and the About sheet's state.
`Status` is one of `Idle`, `NoLink(fromClipboard)`, `Preparing`, `Choosing(title, choices)`,
`Downloading(title, position, count, progress)`, `Saving(title, position, count, progress)`,
`Done(jobId, videos, saved, failed)` and `Failed(reason)`; `Preparing`, `Downloading` and `Saving`
count as running. The screen only renders `UiState` and passes taps back as calls (`onPaste`,
`onSubmit`, `onBarChanged`, `onCancel`, `onChoose`, `onDismissChoice`, `onOpenChoice`,
`onOpenAbout`, `onCloseAbout`, `onUpdateEngine`).

- **`Downloads`** is the small interface the ViewModel uses instead of `DownloadQueue`, so its unit
  tests use a fake: `start`, `cancel`, `jobs()` (each download's state, progress and result, mapped
  from `WorkInfo`), `readChoices`, `updateEngine` and `engineVersion`. `WorkDownloads` is the real
  one.
- **Which job is shown.** The ViewModel follows one job at a time: the one it enqueued, else a job
  still queued or running when it first sees the list (B9). A finished job is shown only when it is
  that job, or when the app was opened from the "Choose which videos" notification
  (`onOpenChoice`, from `MainActivity`), so a cold start is always `Idle`. A job whose end was
  shown, or that was cancelled or dismissed here, is never shown again. Progress shown for a job
  never goes backwards; a post's later video starting over at *preparing* keeps showing the
  download.
- **Choosing.** A `Choose` result carries the link, so the chosen positions are queued as a second
  download of the same link, even after a process death. The choices are read from the file
  `ChoicesFile` wrote; if it is gone, the status is `Failed(Unknown)`.
- **Platform glue** stays in `MainScreen`: the clipboard is read only when Paste is tapped
  (`ClipboardManager`, first item, `coerceToText`); **Open** sends `ACTION_VIEW` with a read grant
  and shows a snackbar when no app can play the video; a `Confirm` haptic plays once per finished
  job (also across rotations); `POST_NOTIFICATIONS` is asked once (§ *Permissions*).
- **Components** (`HeaderWithMascot`, `LinkBar`, `PasteButton`, `StatusCard`, `ChoiceSheet`,
  `AboutSheet`) take state and callbacks and hold no logic. Each sheet's content
  (`ChoiceSheetContent`, `AboutSheetContent`) is separate from its `ModalBottomSheet`, so it can be
  previewed. The column is at most 560 dp wide and scrolls, so small phones, landscape, the
  largest font and the open keyboard all fit; it pads for the system bars and the keyboard
  (`safeDrawing` insets, `imePadding`, `adjustResize`). `MainScreen.kt` has `@Preview`s of every
  `UiState` and both sheets, in light and dark (motion off, so they are still).
- **Choice sheet** (B11, DEC-005): a modal bottom sheet with an adaptive grid of thumbnails, all
  selected at first. Thumbnails are fetched with `HttpURLConnection` on `Dispatchers.IO`, decoded
  with `BitmapFactory` at a power-of-two reduction close to 360 px, and kept in memory for the
  sheet's life; a failure leaves a neutral placeholder. Each tile reads "Video 2, 0:08, selected".
- **About sheet** (B10): opened by a long-press on the header (TalkBack: the "Show app info"
  long-press action); shows the app version, the yt-dlp version and **Update yt-dlp**, which queues
  `updateEngineNow()` and shows "Checking…", then "Up to date", "Updated to …", or that it could not
  check.

## Failure handling

`FailureClassifier` (pure, unit-tested) maps yt-dlp's error text to the product's reasons: no
internet, not supported, unavailable, needs login, unknown. When the text holds `ERROR:` lines it
reads only those, so warnings never decide. It checks, in order: network errors (*no internet*),
the strong signs of a private, removed, age- or region-locked video (*unavailable*, even though
those messages also mention signing in), login and bot checks (*needs login*), unsupported links
(*not supported*), and other "not available" wordings (*unavailable*); anything else is *unknown*.
A network exception anywhere in an exception's cause chain is *no internet*.

A network check before the job starts gives *no internet* immediately, and a job that fails as
*unknown* while the phone is offline is reported as *no internet*. A second HTTP 403 after the
fresh extraction is *unknown*. Raw error text goes to Logcat only (tags `YtDlp` and
`VideoDownloader`), never to the screen.

## Background work

Why: [DEC-006](work/decisions/DEC-006-workmanager-background-downloads.md).

- **Unique chain.** Downloads and yt-dlp updates are enqueued on one unique work name, `engine`,
  with `APPEND_OR_REPLACE`, so an update never replaces the yt-dlp file under a running download,
  and downloads run one at a time. Downloads carry the tag `download`; the ViewModel observes only
  those. A failed or cancelled job ends its chain: the next job starts a new one and WorkManager
  deletes the old chain's finished work. Cancelling a download also cancels an update queued after
  it.
- **`DownloadQueue`** is the API the screen uses: `startDownload(link, selection)` (returns the
  work ID), `cancel(id)`, `observeDownloads()` (a `Flow` of the `download` work), `updateEngineIfDue()`
  (called from `LinkDownloaderApp.onCreate`), `updateEngineNow()` and `engineVersion()` (the stored
  version after an update, else `yt-dlp --version`), plus `observeWork(id)` for one piece of work
  (the About sheet's update).
- **`DownloadData`** holds the keys and turns WorkManager's `Data` into types: the input (link and
  chosen positions), the progress (`DownloadProgress`: stage *preparing*, *downloading* or
  *saving*, title, position and count, overall fraction once known) and the result
  (`DownloadResult`: `Saved` with each video's uri, file name and title and the `failed` count,
  `Choose` with the link, the post's title and the choices file, or `Failed` with the
  `FailureReason`).
  Titles are cut to 200 characters. `Data` is capped at 10 KB: should a post's saved list not fit,
  its last videos are left out of the list but still counted.
- **DownloadWorker** (`CoroutineWorker`) calls `setForeground` at once, with a `dataSync`
  foreground service type and the ongoing notification; if Android refuses (the work started while
  the app was in the background), it goes on as ordinary work. It folds `VideoDownloader`'s events
  into a `DownloadProgress` and publishes it to `setProgress` and the notification at most every
  250 ms (`throttleLatest`), always ending on the latest state. Stopping the work cancels the
  coroutine, which `VideoDownloader` turns into a clean cancel. A failure ends in
  `Result.failure`, never a retry. A `ChooseVideos` outcome ends successfully with the choices
  written to `cache/choices/<work id>.json` (`ChoicesFile`, which keeps only the newest list) and
  only that path in the output, because thumbnail URLs are long.
- **Notifications** (`Notifications`), one low-importance channel, "Downloads", created at app
  start. IDs are fixed, as one download runs at a time:

  | ID | Notification | Tap |
  |---|---|---|
  | 1 | Ongoing: the title (or "Getting ready…"), the stage, "2 of 3" for a post, the progress (indeterminate until known) and **Cancel** (`createCancelPendingIntent`) | Opens the app |
  | 2 | Result: "Saved to Downloads" with the file name, or "3 videos saved" (and how many could not be); or "Couldn't download" with the friendly reason | Plays the (first) video (`ACTION_VIEW`, read permission granted); a failure opens the app |
  | 3 | "Choose which videos to save", only when no activity of the app is on screen; removed when the next download starts | Opens the app with the extra `EXTRA_CHOOSE_WORK_ID` (the work ID) |

  Results auto-cancel on tap. Notifications are posted only when `POST_NOTIFICATIONS` is granted;
  the foreground service runs either way.
- **UpdateWorker** calls `updateYoutubeDL(STABLE)` and records the time of the last successful
  check. `updateEngineIfDue()` enqueues it when that is a day old or more (`EngineUpdateRule`), and
  the worker checks the rule again when it runs, unless forced by `updateEngineNow()`. It has no
  network constraint: offline it skips and records nothing, so the next start tries again. It always
  ends in success and only logs, so it never holds back or fails a download queued after it. Its
  output says how the check went (`EngineUpdateResult`: updated with the version, up to date, or not
  checked), for the About sheet.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and
`POST_NOTIFICATIONS` (asked once by the screen, the first time a download starts, on API 33+; a
refusal does not block downloading, it only hides the notifications). No storage permission.
WorkManager's `SystemForegroundService` is merged into the manifest with
`foregroundServiceType="dataSync"` (`tools:node="merge"`).

## Look and feel

Implemented in `ui.theme`; tokens, not inline values. Why some values are as they are:
[DEC-007](work/decisions/DEC-007-look-and-feel-tokens.md).

| Token | Light | Dark |
|---|---|---|
| background | `#FFF8F3` cream | `#1B1825` |
| onBackground | `#2E2A3B` | `#EDE9F7` |
| surface | `#FFFFFF` | `#252134` |
| onSurface | `#2E2A3B` | `#EDE9F7` |
| onSurfaceVariant | `#5E586B` | `#C9C2D8` |
| surfaceContainerLow (sheets) | `#FFFBF8` | `#211D2E` |
| surfaceContainerHighest (the bar) | `#F1E7E1` | `#3A344C` |
| outlineVariant (idle card edge) | `#E3DCE6` | `#48425A` |
| primary (lavender) | `#6352D9` | `#B9AEFF` |
| onPrimary | `#FFFFFF` | `#2A1F6B` |
| primaryContainer | `#E9E5FF` | `#3D3370` |
| onPrimaryContainer | `#2A1F6B` | `#E6E0FF` |
| secondary (peach) | `#FF9E8A` | `#FFB8A8` |
| tertiary (mint, success) | `#3FAE93` | `#8EE3CB` |
| onTertiary | `#00382C` | `#00382C` |
| tertiaryContainer | `#D5F3EA` | `#1E4A3F` |
| onTertiaryContainer | `#0F4A3C` | `#BFF5E4` |
| error (soft red) | `#C4433B` | `#FF8A80` |
| errorContainer | `#FFE1DE` | `#5A2425` |
| onErrorContainer | `#6B1712` | `#FFDAD6` |
| inverseSurface / inverseOnSurface (snackbar) | `#322E3E` / `#F6F1FA` | `#EDE9F7` / `#322E3E` |

The rest of the Material 3 slots are filled in `ui/theme/Color.kt`. Every text/background pair the
screen draws passes WCAG AA (4.5:1 for text, 3:1 for icons, the progress bar and the mascot's face)
in both themes; `ThemeContrastTest` checks the list. Disabled controls are exempt, as in WCAG.
Dynamic colour is off: the app keeps its own palette.

- **Status card tones** (`cardColors`): neutral (surface with an outlineVariant edge) while idle,
  with no link or while choosing; lavender (primaryContainer) while preparing, downloading and
  saving; mint (tertiaryContainer) when done; soft red (errorContainer) on failure. The tint and
  the text colours cross-fade, and the card's height changes with the spring.
- **Type:** Nunito (SIL Open Font License), the variable font from Google Fonts bundled as
  `res/font/nunito.ttf` and used at weights 400, 600 and 800 (`FontVariation.weight`); licence in
  `assets/licenses/OFL-Nunito.txt`. Headings and titles 800, labels 600, body 400.
- **Shapes** (`ui/theme/Shape.kt`): `PillShape` for the bar and every button, `CardShape` 24 dp for
  the status card, `SheetShape` 32 dp top corners for both sheets, `TileShape` 16 dp for thumbnails.
- **Motion** (`ui/theme/Motion.kt`): one spring (`Motion.spring`, damping 0.6, medium-low
  stiffness) and one 250 ms ease (`Motion.tween`), used everywhere through `springSpec()` and
  `tweenSpec()`. `rememberReducedMotion()` reads the system animator duration scale and follows
  changes; the theme provides it as `LocalReducedMotion`, and when it is 0 every transition snaps,
  the mascot stands still and the indeterminate progress bar is a still track.
- **Screen:** the content sits a little above the middle (free space split 2:3), with the mascot
  (104 dp) and the app name in Nunito ExtraBold centred above a filled pill bar (link icon, "Video
  link" label, lavender ring when focused), a 76 dp lavender **Paste** pill with a clipboard icon
  that shrinks to 95 % when pressed, and the status card. The progress bar is 10 dp, rounded,
  on a pill track, and glides between updates with the 250 ms ease.
- **Mascot** (`ui/Mascot.kt`): a lavender mochi-shaped blob with a download-arrow on its head,
  blush and a face, drawn with `Canvas` in a 100-unit space. Idle: breathes (2.4 s). Working: a
  "o" mouth, bobs (0.45 s) with the arrow pushing down, and fills from the bottom with the
  download's progress. Happy: closed "^ ^" eyes and an open smile, one hop and three sparkles
  that stay. Worried: raised brows, a small frown, a sweat drop and one wobble. Each mood has a
  content description ("Mascot, cheering"). Its colours are `AppTheme.mascot`.
- **Icon:** adaptive launcher icon with the idle mascot on lavender `#7C6CF2`
  (`drawable/ic_launcher_foreground.xml`), a monochrome layer for themed icons
  (`ic_launcher_monochrome.xml`: the silhouette with eyes and smile cut out) and the same
  silhouette as the white notification icon (`ic_stat_download.xml`).
- **Line icons** (`ui/Icons.kt`): the clipboard and link icons are drawn as stroked paths; no icon
  library.

## Dependencies

Keep them few: AndroidX core, activity-compose, lifecycle, Compose BOM (Material 3),
WorkManager (`work-runtime`), kotlinx.serialization (with its Kotlin compiler plugin),
kotlinx.coroutines, youtubedl-android `library`, and test libraries (JUnit, kotlinx-coroutines-test). Every one is
pinned in `gradle/libs.versions.toml`. Adding a library or bumping a major version is recorded as a
decision in `work/decisions/`.

## Tests

- **JVM unit tests:** `LinkParser`, `VideoJson`, `FormatPicker`, `FailureClassifier`, `FileNamer`,
  `ProgressTracker`, `Container`, `DownloadProgress`, `throttleLatest` (in virtual time),
  `EngineUpdateRule`, `DownloadData`, `UpdateWorker`'s output, `ChoicesFile`, the choice sheet's
  `durationText`, `MainViewModel` (every behaviour row that is not pure UI, against a fake
  `Downloads`) and the theme's contrast (`ThemeContrastTest`). No network. `FormatPicker`
  runs against trimmed format lists per site in `app/src/test/resources/formats/` (format fields
  only: neutral IDs and titles, no URLs).
- **Engine probe:** an instrumented test, `EngineProbeTest`, that runs real extractions and
  downloads on a device for links passed as instrumentation arguments. It is skipped when no links
  are passed, so it never runs by accident. Use it when a site seems broken. With `pipeline=true`
  it runs `VideoDownloader` itself on the links, checks the saved files, and can check that a
  cancel leaves nothing behind. Commands in
  [development.md](development.md).
- **By hand:** the screen, the notification, the must-work sites and a post with several videos,
  on a device; each session's handoff says what was tried.
