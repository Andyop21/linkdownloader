# Handoff — P03 The download pipeline

Date: 2026-11-15 · Prompt: [P03](../../../prompts/finished/P03-download-pipeline.md)

## What changed

1. **Build** (`44f9262`). kotlinx-serialization-json 1.13.0 and the serialization compiler plugin,
   kotlinx-coroutines-android (catalog version 1.11.0) as a direct dependency, JUnit for JVM tests.
   Recorded in [DEC-004](../decisions/DEC-004-serialization-and-coroutines.md).
2. **`VideoJson`** (`44f9262`): the fields the pipeline reads, plus `width` and
   `language_preference`, which the DEC-003 rules need. Numbers are `Double?`; unknown keys ignored.
3. **`FormatPicker`** (`44f9262`): DEC-003's rules. `pick` returns `Plan.Single` or `Plan.Pair` and
   throws `DownloadException(NotSupported)` when nothing is usable. 27 tests, against trimmed
   per-site samples in `app/src/test/resources/formats/` and small inline cases.
4. **`FailureClassifier`** (`44f9262`): reads only `ERROR:` lines when there are any; strong
   "unavailable" signs before login, so "Private video. Sign in…" and "Sign in to confirm your age"
   are *unavailable* while "Sign in to confirm you're not a bot" is *needs login*. 15 tests with the
   P02 messages and yt-dlp's usual ones.
5. **`FileNamer`** (`44f9262`): 100 code points and 200 UTF-8 bytes at most, emoji kept, no
   dangling zero-width joiner, `<site>-<id>` then `video` as fallbacks. 11 tests.
6. **`ProgressTracker`** (`44f9262`): fetch 0–95 % weighted by size or evenly, assemble 95–99 %,
   published 100 %. It reads percentages from the lines, because the library's callback repeats the
   last percentage on lines without one. 13 tests.
7. **`Container`** (`44f9262`, not in the prompt's list): container sniffing as a pure class, so the
   "decide by first bytes" rule is unit-tested. 7 tests.
8. **`YtDlp`** (`bd80bab`): `extract` and `fetch` with `--fixup never`, `--no-mtime` and the `-o`
   template, on `Dispatchers.IO`; coroutine cancellation calls `destroyProcessById`.
9. **`MediaAssembler`** (`bd80bab`): join and remux with `MediaExtractor` + `MediaMuxer`, written
   straight into the pending MediaStore entry's file descriptor, interleaved by time, rotation kept.
10. **`DownloadsSaver`** (`bd80bab`): pending entry in `Download/`, publish, delete on any failure
    or cancel; returns the name MediaStore actually gave.
11. **`VideoDownloader`** (`bd80bab`): the entry point P04 calls, with the connectivity check, one
    re-extract on HTTP 403, progress that never goes backwards across that retry, and an *unknown*
    failure turned into *no internet* when the phone is offline.
12. **`EngineProbeTest`** (`bd80bab`): `pipeline=true` runs `VideoDownloader` on the links;
    `keep=true` leaves the videos for a manual check; `cancel=true` cancels the first link during
    the fetch and while saving, and checks for leftovers.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug` (plus `assembleDebugAndroidTest`): BUILD
  SUCCESSFUL, 78 JVM tests pass, lint clean.
- **On the owner's phone** (Samsung SM-S948B, Android 17 / API 37, wireless debugging), one run of
  `EngineProbeTest` with `pipeline=true keep=true cancel=true`, after `updateYoutubeDL(STABLE)` to
  yt-dlp 2026.10.26, one public link per site:

| Site | Picked plan | Saved file | Plays OK | Time (extract / fetch / save / total) |
|---|---|---|---|---|
| YouTube video | Pair `299` + `140` | MP4, joined: H.264 1920×1080 + AAC, 9.4 MB | Yes | 11.1 s / 1.9 s / 0.4 s / 13.4 s |
| YouTube Short | Pair `137` + `140-11` (original-language audio) | MP4, joined: H.264 1080×1920 + AAC, 16.3 MB | Yes, vertical | 15.1 s / 3.4 s / 0.3 s / 18.8 s |
| Instagram Reel | Single `3` | MP4, copied: H.264 720×1280 + AAC, 6.2 MB | Yes, vertical | 4.0 s / 1.5 s / 0.03 s / 5.5 s |
| X | Single `http-10368` | MP4, copied: H.264 1080×1920 + AAC, 18.2 MB | Yes, vertical | 2.0 s / 1.5 s / 0.03 s / 3.6 s |
| TikTok | Single `bytevc1_1080p_…-1` | MP4, copied: HEVC 1080×1920 + AAC, 0.7 MB | Yes, vertical | 1.9 s / 1.2 s / 0.03 s / 3.1 s |
| Reddit | not run (the owner does not need it) | — | — | — |

  Every plan matched DEC-003's *Should pick* rows. Progress went 0 → 1 without going backwards
  (14–131 events per link), and no job folder was left. The owner played all five in the phone's
  gallery/Files app: sound and orientation correct. They were then deleted from `Download/`.
- **Cancel**, on the YouTube link: once at 21 % (fetching) and once at 95 % (writing into the
  pending entry). Both ended in a `CancellationException`, with this app's `Downloads/` entries
  (pending included) unchanged, no job folder and no registered yt-dlp process. The owner confirmed
  in the Files app that only the five saved videos were new.
- **Not checked:** the MPEG-TS remux path (no site needed an HLS format this time); a WebM single;
  a source with a rotation tag; the HTTP 403 re-extract (no 403 happened); losing the network
  mid-download; Android versions below 17. No instrumented test was added for `MediaAssembler`:
  making a media fixture on the device would need an encoder pipeline larger than the assembler
  itself, and the probe exercises the join on real files.

## Docs updated

- `docs/architecture.md`: § *Code layout* (pure parts), § *Download pipeline* (entry point, steps,
  class table, single-format codec rule, save, file name, progress split, cancel), § *Failure
  handling* (order of checks, offline fallback, 403), § *Dependencies*, § *Tests*.
- `docs/development.md` § *Engine probe*: the `pipeline`, `keep` and `cancel` arguments, the log
  tags, and passing links without tracking parameters.
- `docs/work/decisions/DEC-003`: a note on how P03 read the single-format codec rule.
- `docs/work/decisions/DEC-004` and its row in `docs/work/README.md`.
- `CHANGELOG.md`: no entry; nothing user-visible yet (the screen is still the P01 placeholder).

## Bugs filed

None.

## Open questions

- **Reddit.** The owner said they do not need it. `docs/product.md` still lists it as a must-work
  site; should it move to "whatever yt-dlp supports"?
- **Posts with several videos** (an X post with two clips, an Instagram carousel): yt-dlp returns a
  playlist JSON without `formats`, which the pipeline reports as *not supported*. Taking the first
  entry would be a small change if the owner wants it.
- **TikTok titles** arrive cut at 72 characters by yt-dlp itself, so their file names end
  mid-word. Harmless; noted in case it looks like a bug.

## Next

P04 wraps `VideoDownloader.download(link, jobId, onEvent)` in `DownloadWorker`:

- Use the WorkManager work ID as `jobId`; stopping the work cancels the coroutine, which already
  cleans up.
- `onEvent` is called from yt-dlp's reader thread and from IO threads. Progress can arrive more
  than a hundred times per download, so throttle it as the prompt says.
- A cancelled download ends in `CancellationException`, not in an outcome.
- The first YouTube extraction after the update takes 11–15 s inside `Preparing`.
