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

## Code layout

One Gradle module, `app`. Packages under `io.github.andyop21.linkdownloader`:

| Package | Holds |
|---|---|
| (root) | `LinkDownloaderApp` (initialises youtubedl-android once, schedules the yt-dlp update) and `MainActivity` |
| `ui` | `MainScreen`, `MainViewModel`, `UiState`, the mascot and the About sheet |
| `ui.theme` | Colours, type, shapes and motion |
| `link` | `LinkParser`: finds the first `http`/`https` link in a piece of text. Pure Kotlin. |
| `download` | The download pipeline (below), `DownloadWorker`, `UpdateWorker` and the notifications |

Dependencies point inward: `ui` uses `link` and `download`; `link` uses nothing. Pure-Kotlin parts
(`LinkParser`, `VideoJson`, `FormatPicker`, `FailureClassifier`, `FileNamer`, `ProgressTracker`,
`Container`) take no Android types, so they are tested on the JVM.

## Download pipeline

`VideoDownloader.download(link, jobId, onEvent)` is the one entry point; `DownloadWorker` calls it.
It reports `Preparing`, `TitleKnown(title)`, `Progress(fraction)` and `Saving`, and returns
`Saved(uri, fileName, title)` or `Failed(reason)`. Every step runs off the main thread, in a
per-job folder, `cache/jobs/<jobId>`, that is deleted when the job ends, whatever the outcome.

```text
link
 → Check       no network                                  → Failed(NoInternet), nothing started
 → Extract     YtDlp: yt-dlp -J --no-playlist <link>       → info.json in the job folder → VideoJson
 → Pick        FormatPicker.pick(video)                    → Plan: Single(format) | Pair(video, audio)
 → Fetch       YtDlp: yt-dlp --load-info-json info.json -f <ids> --fixup never --no-mtime
                      -o <job>/%(format_id)s.%(ext)s       → one or two files in the job folder
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

`MainViewModel` exposes one `UiState`: `Idle`, `NoLink`, `Preparing`, `Downloading(title, progress)`,
`Saving(title)`, `Done(title, fileName, uri)`, `Failed(reason)`. It enqueues work and maps
WorkManager's `WorkInfo` (state, progress data and output data) to `UiState`. The screen only
renders `UiState`. A finished job's result is shown only if this ViewModel saw that job run, so a
cold start is always `Idle` (product B9).

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

- **Unique chain.** Downloads and yt-dlp updates are enqueued on one unique work name, `engine`,
  with `APPEND_OR_REPLACE`, so an update never replaces the yt-dlp file under a running download.
  Downloads carry the tag `download`; the ViewModel observes only those.
- **DownloadWorker** calls `setForeground` at once, with a `dataSync` foreground service type and a
  low-importance progress notification that has a Cancel action. The finish notification opens the
  video.
- **UpdateWorker** calls `updateYoutubeDL(STABLE)`. It is enqueued at app start at most once a day,
  needs a network connection, and fails silently. The About sheet can enqueue it on demand.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and
`POST_NOTIFICATIONS` (asked once, the first time a download starts, on API 33+; a refusal does not
block downloading). No storage permission. WorkManager's `SystemForegroundService` is declared with
`foregroundServiceType="dataSync"`.

## Look and feel

Implemented in `ui.theme`; tokens, not inline values.

| Token | Light | Dark |
|---|---|---|
| background | `#FFF8F3` cream | `#1B1825` |
| surface | `#FFFFFF` | `#252134` |
| primary (lavender) | `#7C6CF2` | `#B9AEFF` |
| primaryContainer | `#E9E5FF` | `#3D3370` |
| secondary (peach) | `#FF9E8A` | `#FFB8A8` |
| tertiary (mint, success) | `#3FAE93` | `#8EE3CB` |
| error (soft red) | `#D9534A` | `#FF8A80` |
| onBackground | `#2E2A3B` | `#EDE9F7` |

These are starting values; any colour may move to pass WCAG AA contrast, and the final values are
recorded here. Dynamic colour is off: the app keeps its own palette.

- **Type:** Nunito (SIL Open Font License), bundled in `res/font`, weights 400, 600 and 800.
- **Shapes:** the bar and the Paste button are pills; the status card has 24 dp corners.
- **Motion:** short and soft (spring or 200–300 ms ease). All motion is skipped when the system
  animator duration scale is 0.
- **Mascot:** a round blob with a little download-arrow, drawn as a Compose vector; moods idle,
  working, happy and worried.
- **Icon:** adaptive launcher icon from the mascot, with a monochrome layer for themed icons.

## Dependencies

Keep them few: AndroidX core, activity-compose, lifecycle, Compose BOM (Material 3),
WorkManager, kotlinx.serialization (with its Kotlin compiler plugin), kotlinx.coroutines,
youtubedl-android `library`, and test libraries. Every one is
pinned in `gradle/libs.versions.toml`. Adding a library or bumping a major version is recorded as a
decision in `work/decisions/`.

## Tests

- **JVM unit tests:** `LinkParser`, `VideoJson`, `FormatPicker`, `FailureClassifier`, `FileNamer`,
  `ProgressTracker`, `Container` and `MainViewModel`'s state mapping. No network. `FormatPicker`
  runs against trimmed format lists per site in `app/src/test/resources/formats/` (format fields
  only: neutral IDs and titles, no URLs).
- **Engine probe:** an instrumented test, `EngineProbeTest`, that runs real extractions and
  downloads on a device for links passed as instrumentation arguments. It is skipped when no links
  are passed, so it never runs by accident. Use it when a site seems broken. With `pipeline=true`
  it runs `VideoDownloader` itself on the links, checks the saved files, and can check that a
  cancel leaves nothing behind. Commands in
  [development.md](development.md).
- **By hand:** the screen, the notification and the five must-work sites, on a device; each
  session's handoff says what was tried.
