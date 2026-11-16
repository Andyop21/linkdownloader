# Handoff — P02 Prove the engine on a real phone

Date: 2026-11-15 · Prompt: [P02](../../../prompts/finished/P02-engine-probe.md)

## What changed

1. **Dependency and packaging** (`7f97029`). `io.github.junkfood02.youtubedl-android:library`
   0.18.1 in the version catalog; 0.18.1 is still the newest on Maven Central, so there was no
   newer release to weigh. `packaging.jniLibs.useLegacyPackaging = true`, `INTERNET` and
   `ACCESS_NETWORK_STATE`. **Deviation:** `android:extractNativeLibs="true"` is not in the source
   manifest. AGP 9.5 warns when it is there ("should not be specified in this source
   AndroidManifest.xml") and writes `extractNativeLibs=true` into the packaged manifest from
   `useLegacyPackaging` anyway; the debug APK's manifest was checked with `aapt2 dump xmltree`.
2. **Initialisation** (`7f97029`). `LinkDownloaderApp.onCreate` starts a thread that calls
   `YoutubeDL.getInstance().init(this)` and logs the time or the failure under `LinkDownloaderApp`;
   it never crashes the app.
3. **`EngineProbeTest`** (`7f97029`, refined in `870b55f`) in `app/src/androidTest/kotlin/…`, with
   `androidx.test` runner 1.7.0 and ext-junit 1.3.0. Does everything the prompt lists. Additions the
   runs called for:
   - fetches with `--fixup never`;
   - picks by the DEC-003 rules and tries the candidates (HTTPS pair, HLS pair, single) in order;
     `allCandidates=true` fetches all of them;
   - when every candidate gets HTTP 403, it extracts once more and tries again;
   - sniffs each file's real container from its first bytes;
   - opens each file with `MediaExtractor` and offers each track to `MediaMuxer`;
   - logs width, `format_note` and language fields per format;
   - `update=false` skips the update and the second pass.
4. **DEC-003** (`870b55f`). Results per site, the yt-dlp options that worked, the failures and their
   causes, page size, APK size, and the picker rules for P03. Its row is in `docs/work/README.md`.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug` (plus `assembleDebugAndroidTest`): BUILD
  SUCCESSFUL, lint clean, no Gradle deprecation warnings. `testDebugUnitTest` still has no tests.
- `EngineProbeTest` without `links` on the S25_Ultra emulator: reported **skipped** (assumption).
- `installDebug` on the emulator after `pm clear`: app opens, `youtubedl-android ready in 1360 ms`.
- **On the owner's phone** (Samsung SM-S948B, Android 17 / API 37, over wireless debugging), six
  probe runs with one public link each for YouTube, a YouTube Short, an Instagram Reel, X and
  TikTok. Each run covered the bundled yt-dlp 2025.11.12, `updateYoutubeDL(STABLE)` to 2026.10.26,
  and the updated yt-dlp. DEC-003 has the run table. The last full run (run 5) **failed as a test**
  on three items, all recorded in DEC-003:
  - bundled Instagram: login wall, every run;
  - updated Instagram: "empty media response", most likely rate limiting after repeated probing;
  - updated YouTube: HTTP 403 on every format of one extraction.
  Run 6 (the two YouTube links only) passed.
- `adb shell getconf PAGE_SIZE` → 4096. ELF `LOAD` alignment of every native library in the APK and
  in the bundled Python is 16 KB (checked on the host).
- APK sizes from the built files: debug 66.1 MB (both ABIs; about 51.5 MB for `arm64-v8a`), release
  unsigned 44.0 MB.
- **Not checked:** Reddit (the owner chose to skip it); a 16 KB-page device; the re-extract on 403
  actually recovering (no 403 occurred in run 6).

## Docs updated

- `docs/architecture.md`:
  - § *Android targets*: extraction comes from `useLegacyPackaging`, and the manifest attribute is
    not set in the source manifest.
  - § *Download pipeline*:
    - `--fixup never` on every fetch;
    - one re-extract on HTTP 403;
    - FormatPicker summary: short-side cap, HTTPS before HLS, original audio language, ties to
      yt-dlp's order;
    - container sniffing before assembly;
    - progress weighting when sizes are unknown.
- `docs/development.md` § *Engine probe*:
  - reading the log, `ANDROID_SERIAL`;
  - the `update` and `allCandidates` arguments;
  - the connected test uninstalls the app after every run;
  - what the log can reveal;
  - rate limiting.
- `docs/work/README.md`: DEC-003 row.
- `CHANGELOG.md`: no entry. Nothing user-visible changed; the screen is still the P01 placeholder.

## Bugs filed

None. BUG-001 still stands; this handoff links to `prompts/finished/` from the start so the
untracked-handoff case does not apply.

## Open questions

- **Reddit** is a must-work site in `docs/product.md` but was not probed at the owner's request.
  Should it stay on the must-work list, or move to "whatever yt-dlp supports"?
- **TikTok in HEVC.** The best TikTok format within 1080p is HEVC; its H.264 stops at 540p. Phones
  play HEVC, older PC players may not. DEC-003 keeps resolution first; the owner may prefer H.264.
- **YouTube 403.** It hit 2 of 5 extractions of the 16:9 video with the updated yt-dlp; in run 5
  it refused every format of that extraction. Re-extracting is the proposed remedy, not yet seen working. If it does not help,
  yt-dlp's YouTube client selection (`--extractor-args youtube:player_client=…`) is the next thing
  to look at.
- **APK size.** The debug `classes.dex` files are large partly because the library brings
  jackson-databind 2.11.1, commons-io 2.5 and appcompat 1.4.2 with it. Minifying the release (P07)
  should shrink them. jackson-databind 2.11.1 is old and may be flagged by dependency scanners once
  the repository is public; it only parses yt-dlp's and GitHub's JSON.
- **Command-line JDK.** `docs/development.md` suggests Android Studio's `jbr`, but on this machine
  Android Studio is not at `C:\Program Files\Android\Android Studio`; builds used
  `C:\Program Files\Android\openjdk\jdk-21.0.8`. The doc's example path may need a note.

## Next

P03 builds the pipeline on DEC-003. Specifically:

- **Fetch:** pass `--fixup never` on every fetch.
- **On HTTP 403:** extract again once.
- **Picker:** implement the rules from DEC-003, with unit tests built on the shapes it describes.
- **Assembly:** check each file's first bytes before choosing how to assemble it.
- **Progress:** handle the per-file 0–100 % restart.
- **Probe:** use `EngineProbeTest` with `allCandidates=true` to recheck a site, and keep requests
  few, because the sites rate-limit.

`kotlinx-coroutines-test` and JUnit are still not wired into `app` as `testImplementation`; add
them with the first JVM tests.
