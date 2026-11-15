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
| Build | Gradle (Kotlin DSL) with a version catalog at `gradle/libs.versions.toml` |

Why: [DEC-001](work/decisions/DEC-001-kotlin-compose-ytdlp.md) (stack) and
[DEC-002](work/decisions/DEC-002-no-ffmpeg-merge-with-mediamuxer.md) (no ffmpeg).

## Android targets

- Application ID and namespace `io.github.andyop21.linkdownloader`; app label "Link Downloader".
- `minSdk 29` (Android 10): from 29 on, saving to `Downloads/` through MediaStore needs no storage
  permission.
- `targetSdk` and `compileSdk`: the latest stable API level, pinned in the version catalog.
- ABIs: release builds `arm64-v8a` only; debug builds `arm64-v8a` and `x86_64` so the app also runs
  on a PC emulator.
- youtubedl-android runs Python and QuickJS as executables from the native library directory, so
  the app sets `android:extractNativeLibs="true"` and `packaging.jniLibs.useLegacyPackaging = true`.

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
(`LinkParser`, `FormatPicker`, `FailureClassifier`, `FileNamer`, `ProgressTracker`) take no Android
types, so they are tested on the JVM.

## Download pipeline

All steps run inside `DownloadWorker`, in a per-job folder under the app's cache directory that is
deleted when the job ends, whatever the outcome.

```text
link
 → Extract     yt-dlp -J --no-playlist <link>              → video JSON (title, formats, sizes)
 → Pick        FormatPicker(json)                          → Plan: Single(format) | Pair(video, audio)
 → Fetch       yt-dlp --load-info-json <json> -f <ids>     → one or two files in the job folder
 → Assemble    Pair      → MediaMuxer joins video + audio into one .mp4
               Single ts → MediaMuxer remuxes into .mp4
               Single mp4/webm → used as is
 → Save        DownloadsSaver: MediaStore Downloads entry (IS_PENDING while written) → content Uri
```

- **Extract once.** The JSON from the first call is written to the job folder and handed back to
  yt-dlp with `--load-info-json`, so the site is only queried once.
- **FormatPicker** (pure, unit-tested) prefers, in order: the best *pair* of a video-only H.264 or
  HEVC MP4 stream up to 1080p and an AAC (`mp4a`) audio stream; the best *single* MP4 with both
  video and audio; any single format with both video and audio. It uses height, then bitrate, to
  rank, and filesize (or filesize_approx) to weight progress. It never selects a format MediaMuxer
  cannot write. The rules are refined from the probe results in DEC-003.
- **Progress** comes from youtubedl-android's callback per file. `ProgressTracker` turns the per-file
  percentages into one overall fraction that never goes backwards: fetch takes 0–95 %, weighted by
  file size; assemble and save take the rest.
- **Cancel** destroys the yt-dlp process by its process ID, then deletes the job folder and any
  pending MediaStore entry.

`MainViewModel` exposes one `UiState`: `Idle`, `NoLink`, `Preparing`, `Downloading(title, progress)`,
`Saving(title)`, `Done(title, fileName, uri)`, `Failed(reason)`. It enqueues work and maps
WorkManager's `WorkInfo` (state, progress data and output data) to `UiState`. The screen only
renders `UiState`. A finished job's result is shown only if this ViewModel saw that job run, so a
cold start is always `Idle` (product B9).

## Failure handling

`FailureClassifier` (pure, unit-tested) maps yt-dlp's error text to the product's reasons: no
internet, not supported, unavailable, needs login, unknown. A network check before the job starts
gives *no internet* immediately. Raw error text goes to Logcat only, never to the screen.

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
WorkManager, kotlinx.serialization, youtubedl-android `library`, and test libraries. Every one is
pinned in `gradle/libs.versions.toml`. Adding a library or bumping a major version is recorded as a
decision in `work/decisions/`.

## Tests

- **JVM unit tests:** `LinkParser`, `FormatPicker`, `FailureClassifier`, `FileNamer`,
  `ProgressTracker` and `MainViewModel`'s state mapping. No network.
- **Engine probe:** an instrumented test, `EngineProbeTest`, that runs real extractions and
  downloads on a device for links passed as instrumentation arguments. It is skipped when no links
  are passed, so it never runs by accident. Use it when a site seems broken. Commands in
  [development.md](development.md).
- **By hand:** the screen, the notification and the five must-work sites, on a device; each
  session's handoff says what was tried.
