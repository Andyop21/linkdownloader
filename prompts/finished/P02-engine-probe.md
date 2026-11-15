# P02 — Prove the engine on a real phone

**Goal.** Before the pipeline is built, find out what youtubedl-android 0.18.1 *actually* does on
the owner's phone without ffmpeg: what each must-work site serves, which formats the picker should
choose, how long extraction takes, and whether anything crashes. The answers become decision
record DEC-003, which P03 builds on. This session also leaves behind the reusable
`EngineProbeTest`.

**Entry condition.** P01 finished. A device or emulator is connected; prefer the owner's phone.

**Read first.** `docs/work/decisions/DEC-001-kotlin-compose-ytdlp.md`,
`docs/work/decisions/DEC-002-no-ffmpeg-merge-with-mediamuxer.md`, `docs/architecture.md`
(§ *Android targets*, *Download pipeline*, *Tests*), `docs/development.md` § *Engine probe*.

## Scope

- Add youtubedl-android's `library` module (not `ffmpeg`, not `aria2c`) and the native packaging
  settings it needs.
- Initialise it in `LinkDownloaderApp`.
- Write `EngineProbeTest`, run it against one public link per must-work site, and record what it
  finds.

## Out of scope

The production pipeline (`FormatPicker`, `MediaMuxer` joining, saving to Downloads), WorkManager
and the UI. The probe may write into the app's cache only; it deletes what it downloads.

## Deliverables

1. **Dependency and packaging.**
   - Add `io.github.junkfood02.youtubedl-android:library:0.18.1` to the version catalog. If Maven
     Central has a newer version by then, read its release notes and decide; record the choice.
   - Set `android:extractNativeLibs="true"` in the manifest and
     `packaging { jniLibs { useLegacyPackaging = true } }` in the app module. Add `INTERNET` and
     `ACCESS_NETWORK_STATE` permissions.
   - Initialise with `YoutubeDL.getInstance().init(context)` from `LinkDownloaderApp.onCreate`, off
     the main thread if it takes long, with failures logged rather than crashing the app.
2. **`EngineProbeTest`** in `app/src/androidTest/.../EngineProbeTest.kt`:
   - Reads the instrumentation argument `links` (space-separated). When it is absent, the test is
     skipped with an assumption, not failed.
   - For each link: runs `yt-dlp -J --no-playlist` and logs, under the Logcat tag `EngineProbe`, the
     extractor name, title, duration and every format's id, ext, protocol, vcodec, acodec, height,
     tbr and filesize/filesize_approx; times the extraction.
   - Saves the JSON to the cache and runs a real fetch with `--load-info-json` and an explicit
     `-f` (a video-only H.264/HEVC + AAC audio pair where one exists, otherwise the best single
     format with both video and audio) into a per-link cache folder, with
     `-o "<folder>/%(format_id)s.%(ext)s"`. It logs the files produced, their sizes, the time
     taken, and the progress callback lines seen.
   - Logs yt-dlp's version, and whether the forced `--ffmpeg-location` (which the library always
     adds, pointing to a binary we do not ship) prints only a warning or breaks anything.
   - Deletes its downloads at the end.
3. **Run it.** Ask the owner for one public link per site (YouTube, a YouTube Short, TikTok, an
   Instagram Reel, X, Reddit) and run the probe on the phone. Also run
   `adb shell getconf PAGE_SIZE` and note the result. **Never commit the links**; in the records,
   name the site only.
4. **Update the bundled yt-dlp once.** Call `updateYoutubeDL(context, UpdateChannel.STABLE)` from the
   probe before a second pass, and note whether results changed. This shows the on-phone update
   works with the bundled Python.
5. **DEC-003** at `docs/work/decisions/DEC-003-engine-probe-results.md` from the template, with:
   a table per site (formats offered, the pair or single that should be picked, protocols, file
   types that arrived, extraction and fetch times); the exact yt-dlp options that worked; anything
   that failed and why; the page size; the APK size of `assembleDebug` for `arm64-v8a`. End with
   **picker rules for P03**: concrete selection rules derived from what was seen, including what
   to do when a site serves only HLS or only webm.
   Add DEC-003's row to `docs/work/README.md`.
6. **If something is fundamentally broken** (Python does not start, QuickJS crashes, the 16 KB page
   size breaks loading, YouTube fails every time), stop after recording it in DEC-003 and ask the
   owner. The fallback is the Chaquopy route in DEC-001; do not start it unasked.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- [ ] `EngineProbeTest` is skipped without the `links` argument and ran on a device with it.
- [ ] DEC-003 exists with results for all six links and the picker rules for P03.
- [ ] `docs/architecture.md` § *Download pipeline* agrees with what was learned; any change is made
      there in the same commit.
- [ ] No links, cookies or personal data committed.

## Handoff

Write `docs/work/handoffs/P02-engine-probe.md`, then run `bash scripts/finish-prompt.sh P02`.
