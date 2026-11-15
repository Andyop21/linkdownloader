# Architecture

The plan the code follows. Until the app is scaffolded, this page describes the intended shape;
once code exists, the code and this page must agree.

## Stack

| Part | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Downloader | [youtubedl-android](https://github.com/yausername/youtubedl-android): yt-dlp with bundled Python and ffmpeg |
| Background work | WorkManager, as a foreground worker with a notification |
| Saving files | MediaStore `Downloads` collection |
| Build | Gradle (Kotlin DSL) with a version catalog at `gradle/libs.versions.toml` |

Why: [DEC-001](work/decisions/DEC-001-kotlin-compose-ytdlp.md).

## Android targets

- `minSdk 29` (Android 10). From 29 on, saving to `Downloads/` through MediaStore needs no storage
  permission.
- `targetSdk` and `compileSdk`: the latest stable API level when the app is scaffolded.
- ABI: `arm64-v8a` only. youtubedl-android ships native code per ABI; one ABI keeps the APK size down.

## Code layout

One Gradle module, `app`. Packages under the application ID:

| Package | Holds |
|---|---|
| (root) | `LinkDownloaderApp` (initialises yt-dlp and ffmpeg once) and `MainActivity` |
| `ui` | The one screen, its `MainViewModel`, and the theme (colours, shapes, type) |
| `link` | `LinkParser`: finds the first `http`/`https` link in a piece of text. Pure Kotlin, no Android types. |
| `download` | `DownloadWorker`, the yt-dlp wrapper, and `DownloadsSaver` (copies a file into MediaStore Downloads) |

Dependencies point inward: `ui` uses `link` and `download`; `link` uses nothing.

## Download flow

```text
Paste tapped
  → ClipboardManager text
  → LinkParser.firstLink(text) ── none ──► UiState.NoLink
  → enqueue unique work "download" (DownloadWorker, link as input)
  → DownloadWorker (foreground, progress notification)
      → yt-dlp: single video, best video+audio merged to mp4, into the app's cache dir
      → DownloadsSaver: copy into MediaStore Downloads, delete the cache file
  → WorkInfo → MainViewModel → UiState.Done(fileName) / UiState.Failed(reason)
```

`MainViewModel` exposes one `UiState`: `Idle`, `NoLink`, `Downloading(title, progress)`,
`Done(fileName)`, `Failed(reason)`. It maps the worker's state to it; the screen only renders it.

Failure reasons are a small enum (no network, unsupported link, unavailable video, unknown), mapped
from yt-dlp's errors in the `download` package. The screen never shows raw error text.

## Keeping yt-dlp current

Sites change and break extractors. On launch, at most once a day, the app asks youtubedl-android to
update yt-dlp in the background. A failed download is not retried automatically.

## Permissions

`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` (API 34+), and `POST_NOTIFICATIONS`
(asked at runtime on API 33+, the first time a download starts). No storage permission.

## Dependencies

Keep them few. Every library is pinned in `gradle/libs.versions.toml`. Adding a library or bumping a
major version is recorded as a decision in `work/decisions/`.

## Tests

- Unit tests (JVM) for `LinkParser` and `MainViewModel`'s state mapping. No network in tests.
- The download path is checked by hand on a device or emulator; the handoff says which links were
  tried.
