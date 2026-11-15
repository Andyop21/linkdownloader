# P03 — The download pipeline

**Goal.** A link goes in and a playable video in `Downloads/` comes out, with progress, cancel and
a clear failure reason, all as plain, testable Kotlin in the `download` package. No UI and no
WorkManager yet: P04 wraps this in a background worker and P05 puts a screen on it.

**Entry condition.** P02 finished; DEC-003 exists and has picker rules.

**Read first.** `docs/architecture.md` (§ *Download pipeline*, *Failure handling*, *Code layout*,
*Tests*), `docs/work/decisions/DEC-002-no-ffmpeg-merge-with-mediamuxer.md`,
`docs/work/decisions/DEC-003-engine-probe-results.md`, `docs/product.md` (§ *What gets downloaded*,
*Failure reasons*), the `EngineProbeTest` from P02.

## Scope

The whole pipeline in `download`: extract → pick → fetch → assemble → save, plus cancel, cleanup,
progress and failure mapping.

## Out of scope

WorkManager, notifications, the screen, yt-dlp updates. Do not add settings or options the product
does not ask for.

## Deliverables

1. **Video model.** A small `@Serializable` model of the yt-dlp JSON with only the fields the
   pipeline uses (id, title, extractor, formats with format_id, ext, protocol, vcodec, acodec,
   height, tbr, abr, filesize, filesize_approx), parsed with kotlinx.serialization and
   `ignoreUnknownKeys`. Add the serialization plugin and library to the catalog.
2. **`FormatPicker`** (pure Kotlin): `pick(video): Plan` where `Plan` is `Single(format)` or
   `Pair(video, audio)`, or a *not supported* failure when nothing usable exists. It follows
   `docs/architecture.md` and DEC-003's rules, caps at 1080p, and only returns streams MediaMuxer
   can write. Unit tests run against trimmed sample JSON for each site in
   `app/src/test/resources/formats/` (format lists only: no URLs, no titles from real people;
   replace titles with neutral text).
3. **`FailureClassifier`** (pure Kotlin): yt-dlp error text and exceptions → `FailureReason`
   (`NoInternet`, `NotSupported`, `Unavailable`, `NeedsLogin`, `Unknown`). Unit tests cover the real
   messages seen in P02 and yt-dlp's usual ones ("Unsupported URL", "Private video", "Video
   unavailable", "Sign in to confirm", "login required", name-resolution and connection errors).
4. **`FileNamer`** (pure Kotlin): title → a safe display name: illegal characters removed, emoji
   kept where the file system allows, whitespace collapsed, at most about 100 characters, never
   empty (falls back to the site and video id), with the right extension. Unit tests.
5. **`ProgressTracker`** (pure Kotlin): per-file percentages from the youtubedl-android callback →
   one overall fraction that never decreases. Fetch maps to 0–0.95, weighted by file size when
   known and evenly when not; assemble and save take the rest. Unit tests.
6. **`YtDlp`** wrapper around youtubedl-android: `extract(link, jobDir): VideoJson` (`-J
   --no-playlist`, JSON saved to `jobDir`) and `fetch(jsonFile, formatIds, jobDir, processId,
   onLine)` (`--load-info-json`, `-f`, `-o "<jobDir>/%(format_id)s.%(ext)s"`, `--no-mtime`, plus
   anything DEC-003 found necessary). It runs off the main thread (`Dispatchers.IO`) and maps
   exceptions through `FailureClassifier`.
7. **`MediaAssembler`**: joins a video file and an audio file into one MP4 with `MediaExtractor` +
   `MediaMuxer`, keeping the video's rotation, and remuxes a single MPEG-TS file into MP4. Prefer
   writing straight into the pending MediaStore entry's file descriptor so the file is not copied
   twice. Test it by hand on files the probe fetched; add an instrumented test if a small, freely
   licensed fixture can be generated on the device without committing media files.
8. **`DownloadsSaver`**: creates a MediaStore `Downloads` entry with `IS_PENDING = 1`, the
   display name from `FileNamer`, MIME type `video/mp4` (or `video/webm`), and
   `RELATIVE_PATH = Download/`; writes; clears `IS_PENDING`; deletes the entry on failure or cancel.
   Returns the content `Uri`.
9. **`VideoDownloader`**: the one entry point P04 calls:
   `suspend fun download(link, jobId, onEvent): DownloadOutcome`, which emits `Preparing`,
   `TitleKnown(title)`, `Progress(fraction)` and `Saving`, and returns `Saved(uri, fileName, title)`
   or `Failed(reason)`. It runs the steps in a per-job cache folder that is deleted in a `finally`,
   checks connectivity first (`NoInternet`), and supports cancellation: coroutine cancellation
   destroys the yt-dlp process (`destroyProcessById(jobId)`) and removes partial files and the
   pending MediaStore entry.
10. **Probe check.** Extend `EngineProbeTest` with an option to run the full `VideoDownloader` on the
    passed links, and run it for all must-work sites. Each resulting video must play in the phone's
    gallery or Files app with sound and the correct orientation. Delete them afterwards.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes; deliverables 2–5 have unit
      tests that cover their edge cases.
- [ ] The full pipeline saved a playable video for every must-work site on a real phone, and
      cancelling mid-download left no files behind (checked in the Files app).
- [ ] `docs/architecture.md` matches the code (class names, pipeline steps, progress split).
- [ ] No real titles, links or media files committed.

## Handoff

Write `docs/work/handoffs/P03-download-pipeline.md` with a per-site table (picked plan, file type,
plays OK, time taken). Then run `bash scripts/finish-prompt.sh P03`.
