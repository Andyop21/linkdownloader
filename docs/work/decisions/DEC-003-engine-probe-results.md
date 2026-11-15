---
id: DEC-003
title: Engine probe results and the picker rules for P03
date: 2026-11-15
status: accepted
---

# DEC-003 — Engine probe results and the picker rules for P03

## Context

DEC-001 chose youtubedl-android and DEC-002 chose to ship no ffmpeg and to join streams with
`MediaMuxer`. Before the pipeline is built (P03), P02 ran `EngineProbeTest` on the owner's phone to
see what each must-work site actually serves, what arrives on disk, how long it takes, and what
breaks. This record holds what was seen and the rules P03 builds on.

### Setup

| Item | Value |
|---|---|
| Phone | Samsung SM-S948B, Android 17 (API 37), `arm64-v8a`, over wireless debugging |
| Page size | `adb shell getconf PAGE_SIZE` → **4096** (the probe's `Os.sysconf` agrees) |
| Library | `io.github.junkfood02.youtubedl-android:library` **0.18.1**, still the latest on Maven Central on 2026-11-15, so there was no newer release to weigh |
| yt-dlp | bundled **2025.11.12**; after `updateYoutubeDL(STABLE)` **2026.10.26** |
| Init | `YoutubeDL.init` 629–664 ms on the phone on a fresh install (1.36 s on the x86_64 emulator) |
| Debug APK | 66.1 MB with both ABIs; the `arm64-v8a` part alone is about **51.5 MB** (66.1 MB minus 14.6 MB of compressed `x86_64` libraries). Unsigned release, `arm64-v8a` only, not minified: 44.0 MB |
| Native libraries | `libpython.zip.so` 14.0 MB (a zip of Python, unpacked at init), `libqjs.so` 0.9 MB, `libpython.so`; no `libffmpeg.so` |
| 16 KB pages | Not testable on this phone (4 KB). Every ELF in the APK's `arm64-v8a` libraries and all 186 ELF files inside the bundled Python have 16 KB `LOAD` alignment |

Links: one public link each for YouTube, a YouTube Short, an Instagram Reel, X and TikTok. **Reddit
was not probed:** the owner chose to skip it. The links are not recorded here.

Every run is a fresh install (Gradle's connected test installs and uninstalls the app) with a pass
on the bundled yt-dlp, the update, and a pass on the updated yt-dlp.

| Run | Links | What changed |
|---|---|---|
| 1 | all five | first format choice: cap on `height`, highest bitrate, no extra yt-dlp options |
| 2 | all five | cap on the short side, HTTPS before HLS, non-DRC audio |
| 3 | all five | the fetch adds `--fixup never`; every candidate fetched (`allCandidates=true`) |
| 4 | Short only, bundled only | audio language fields logged |
| 5 | all five | the committed probe with default arguments |
| 6 | the two YouTube links | re-extract once when every candidate gets HTTP 403 |

## Results per site

Times are wall-clock on the phone over home Wi-Fi. "Extract" is `yt-dlp -J --no-playlist`; "fetch"
is the download of the chosen formats from the saved JSON.

### YouTube (regular video, 16:9, 1080p available)

| | Bundled 2025.11.12 | Updated 2026.10.26 |
|---|---|---|
| Formats | 30: DASH HTTPS only (yt-dlp warns that YouTube forces SABR on some web-client formats and skips them) | 49: the same DASH HTTPS formats plus HLS (`m3u8_native`) video-only formats |
| Extract | 3.7–4.0 s | 10.6–11.3 s |
| Should pick | pair `299` (H.264 1920×1080, HTTPS) + `140` (AAC, HTTPS) | the same |
| Fetch | 5.9–7.1 s for 9.4 MB | 1.4 s (HTTPS pair); 2.3–4.3 s (HLS pair `312` + `140`) |
| Arrived | `299.mp4` MP4 (`video/avc`), `140.m4a` MP4 (`audio/mp4a-latm`) | the same; HLS video arrives as `312.mp4` that is **MPEG-TS inside** |
| Failures | before `--fixup never`: crash after download (below) | HTTP 403 in 2 of 5 extractions (below) |
| Single fallback | `18` (H.264 + AAC 640×360, MP4) | the same |

### YouTube Short (vertical 1080×1920)

| | Bundled | Updated |
|---|---|---|
| Formats | 75 | 123 (adds HLS) |
| Extract | 7.9–8.2 s | 14.3–15.3 s |
| Should pick | pair `137` (H.264 1080×1920, HTTPS) + `140-11` (AAC, the original-language track) | the same |
| Fetch | 1.9–3.0 s for 16.3 MB | 1.9–2.8 s (HTTPS pair); 2.8–2.9 s (HLS pairs `231`/`270` + audio) |
| Arrived | `137.mp4` MP4 (`video/avc` 1080×1920), `140-11.m4a` MP4 | the same; HLS video is MPEG-TS |
| Audio | 12 AAC variants of itag 140 (`140-0` … `140-11`) plus `140-drc`: YouTube's auto-dubbed tracks (German, Spanish, French, Hindi, …). Only `140-11` and `140-drc` are the original language, with `language_preference` 10; the dubs have −1. Their bitrates differ by 0.001 kbps, so a bitrate rule picks a language by accident. | the same |

### Instagram Reel

| | Bundled | Updated |
|---|---|---|
| Result | **Fails every run**: "Main webpage is locked behind the login page", then "Requested content is not available, rate-limit reached or login required" | Worked in runs 1–3. Run 5: "Instagram sent an empty media response", most likely rate limiting after eight extractions in eleven minutes |
| Formats | — | 12: three progressive MP4s `1`, `2`, `3` whose codecs and sizes the JSON leaves `null`; eight DASH video-only VP9 formats (1280–2560 tall); one DASH AAC audio |
| Extract | — | 2.7–3.9 s |
| Should pick | — | single `3`: the last progressive format, which is also yt-dlp's own default |
| Fetch | — | 1.4 s for 6.2 MB |
| Arrived | — | `3.mp4` MP4: `video/avc` 720×1280 + `audio/mp4a-latm` |

The only H.264 here is in the progressive formats, and their resolution is unknown until
downloaded. The VP9 DASH video cannot go into an MP4 with `MediaMuxer`, so it is never picked.

### X

| | Bundled | Updated |
|---|---|---|
| Formats | 13: five progressive `http-*` MP4s (codecs `null` in the JSON, sizes approximate), five HLS video-only H.264 `hls-*`, three HLS audio `hls-audio-*` (codec `null`) | the same |
| Extract | 2.1 s | 1.7–1.9 s |
| Should pick | single `http-10368` (1080×1920) | the same |
| Fetch | 1.5–1.7 s for 18.2 MB | 1.4–1.5 s |
| Arrived | `http-10368.mp4` MP4: `video/avc` 1080×1920 + `audio/mp4a-latm` | the same |

This video also had `http-25128` (2160×3840, 59 MB), which run 1 fetched before the size cap
moved to the short side. No H.264 + AAC *pair* is possible: the HLS audio's codec is not stated,
so the single progressive format wins.

### TikTok

| | Bundled | Updated |
|---|---|---|
| Formats | 9–10, all progressive MP4 over HTTPS with both streams: H.264 + AAC at 576×1024 (`h264_540p_*`), HEVC (`bytevc1_*`, vcodec `h265`) + AAC at 540p, 720p and 1080p, each twice (`-0`, `-1`, different CDNs); a `download` format (H.264 + AAC, no size); an `audio` MP3 | the same |
| Extract | 2.0 s | 1.9–2.0 s |
| Should pick | single `bytevc1_1080p_*-1` (HEVC 1080×1920 + AAC) | the same |
| Fetch | 1.2–2.1 s for 0.7 MB | 1.2–1.4 s |
| Arrived | MP4: `video/hevc` 1080×1920 + `audio/mp4a-latm` | the same |

yt-dlp warns that TikTok "is attempting impersonation, but no impersonate target is available";
extraction worked anyway.

### What arrived, all sites

Every file, from every site and version, opened in `MediaExtractor`, and `MediaMuxer` (MPEG-4
output) accepted every track: `video/avc`, `video/hevc` and `audio/mp4a-latm`, from MP4, DASH m4a
and MPEG-TS sources.

## Failures and their causes

1. **Bundled yt-dlp crashes on every YouTube pair fetch** (runs 1–2). After both files download,
   yt-dlp runs an ffmpeg *fixup* post-processor (`postprocessor/ffmpeg.py` `_fixup` →
   `_configuration_args`) and dies with `AttributeError: 'NoneType' object has no attribute
   'lower'`. The library always adds `--ffmpeg-location` pointing at `libffmpeg.so`, which the app
   does not ship. **`--fixup never` fixes it**: with it, the bundled yt-dlp fetched both YouTube
   pairs in runs 3, 4, 5 and 6. The updated yt-dlp only warns ("writing DASH m4a", "Possible
   MPEG-TS in MP4 container") instead of crashing.
2. **The forced `--ffmpeg-location` on its own** only prints `WARNING: ffmpeg-location … does not
   exist! Continuing without ffmpeg` on every call, plus, on the updated yt-dlp, "ffmpeg not found.
   The downloaded format may not be the best available" during extraction. Neither changes the
   result once `--fixup never` is set.
3. **YouTube answers HTTP 403 to every URL of some extractions** (updated yt-dlp, the 16:9 video,
   runs 2 and 5; the other three extractions of it, and all of the Short's, were fine). In run 5 the
   HTTPS pair, the HLS pair and the progressive `18` from the same JSON were all refused, so trying
   another format from the same extraction does not help. Extracting again is the likely remedy;
   the probe does that since run 6, but no 403 happened in run 6, so it is **not yet seen working**.
4. **Bundled yt-dlp cannot read Instagram** (login wall, every run). The updated yt-dlp can, until
   Instagram rate-limits (run 5).
5. **Bundled yt-dlp warns it is "older than 90 days"** on every call. Harmless; gone after the update.

Nothing fundamental broke: Python starts, QuickJS solves YouTube's challenges, the 4 KB phone loads
everything, and YouTube works on both versions. The Chaquopy fallback in DEC-001 is not needed.

## The update on the phone

`updateYoutubeDL(context, UpdateChannel.STABLE)` returned `DONE` in 0.86–0.92 s and moved yt-dlp
from 2025.11.12 to 2026.10.26; the bundled Python 3.11 ran the new yt-dlp without trouble. What
changed: Instagram started working, YouTube gained HLS formats and lost the fixup crash, YouTube
extraction got slower (about 3.8 → 10.8 s for a video, 8.0 → 14.7 s for a Short), and the
occasional YouTube 403 appeared. X and TikTok did not change. Before the first update the
library's `version(context)` and `versionName(context)` return `null`; `yt-dlp --version` gives the
real version.

## Decision

### yt-dlp options that worked

```text
Extract:  -J --no-playlist <link>
Fetch:    --load-info-json <job>/info.json
          -f <id> | -f <video-id>,<audio-id>
          --fixup never
          -o <job>/%(format_id)s.%(ext)s
```

The library adds `--no-cache-dir`, `--js-runtimes quickjs:<nativeLibraryDir>/libqjs.so` and
`--ffmpeg-location <nativeLibraryDir>/libffmpeg.so` to every call by itself. `--fixup never` is
required on every fetch. With `-f a,b` yt-dlp downloads the formats in the order given, each with
its own `[download] Destination:` line and its own 0–100 % progress.

**HTTP 403 on fetch** (yt-dlp's error ends in `HTTP Error 403: Forbidden`): delete the job's files,
extract once more, pick again from the new JSON, fetch again. A second 403 ends the job as
*unknown* ("try again in a moment"). This is the rule least likely to be wrong given run 5, but it
has not been seen to succeed yet; P03 confirms it with the probe when a 403 turns up.

### Picker rules for P03

Definitions:

- **Short side** = `min(width, height)` when both are known, else `height`. The product's "up to
  1080p" cap is `short side ≤ 1080`; `height` alone is the long side of vertical videos (Shorts,
  TikTok, Reels, X), where it reached 1920–3840.
- **Video-only**: `vcodec` is set and not `"none"`, `acodec == "none"`. **Audio-only**: the
  reverse. **Has both**: neither `vcodec` nor `acodec` is `"none"`; `null` (unknown) counts as
  present, because Instagram and X leave the codecs of their progressive MP4s `null`.
- **Muxable video codec**: `vcodec` starts with `avc1`, `avc3`, `h264`, `hvc1`, `hev1`, `h265` or
  `hevc` (TikTok writes `h265`). **AAC**: `acodec` starts with `mp4a`, or is `aac` (TikTok).
- **HLS**: `protocol` starts with `m3u8`. **HTTPS**: `protocol == "https"`.
- **yt-dlp order**: yt-dlp sorts `formats` worst to best by its own preferences. When the rules
  below tie, the later entry wins.

`FormatPicker` returns the first of these that exists:

1. **Pair.** Video: video-only, muxable codec, short side ≤ 1080; ranked by HTTPS over HLS, then
   short side, then `tbr`, then yt-dlp order. (HTTPS first, because HLS always needs a remux and was
   never the only working choice.) Audio: audio-only AAC; ranked by `language_preference` (the
   original language is 10, dubs −1), then non-DRC (format ID without `drc`) over DRC, then HTTPS
   over HLS, then `tbr`, then yt-dlp order.
2. **Single.** Has both, short side ≤ 1080 or unknown, video codec muxable or unknown, `ext` `mp4`
   or `webm`; ranked by short side (unknown counts as 0), then `ext == "mp4"`, then HTTPS over HLS,
   then `tbr`, then yt-dlp order. This takes Instagram's last progressive MP4, X's best `http-*`
   within the cap, and TikTok's HEVC 1080p.
3. **Nothing** → the job fails as *not supported*. The picker never returns yt-dlp's `best`, since
   that may need ffmpeg.

A format whose short side is above 1080 is never picked, even when nothing smaller exists in that
category; the next rule is tried instead. All five probed sites offered something within the cap.

Note the pair's video rank puts HTTPS *before* resolution: a 720p HTTPS video beats a 1080p HLS one.
On YouTube both protocols offered the same resolutions, so this did not cost anything in the probe.

**Only HLS.** An HLS video-only format is fine in a pair, and an HLS single with both streams is fine
as a single. Either way the file that arrives is MPEG-TS whatever its extension (`.mp4`), so P03
decides by the file's first bytes (`0x47` sync byte at offsets 0 and 188), not by `ext`, and always
remuxes it with `MediaExtractor` + `MediaMuxer`. Seen working for YouTube HLS video.

**Only webm.** VP9, AV1 and Opus cannot go into an MP4 with `MediaMuxer`, so they never form a
pair. A single `webm` with both streams is kept as is and saved as `.webm` (the product allows that
when nothing else is offered). No probed site needed it.

### Assembly and progress, as seen

- **Pair** → always joined with `MediaMuxer` into a new MP4, so YouTube's DASH m4a ("only some
  players support this container") never reaches the user. The video file may be MP4 or MPEG-TS.
- **Single MP4** → used as is (X, TikTok, Instagram and YouTube `18` were all plain MP4 with
  `ftyp`). **Single MPEG-TS** → remuxed. **Single webm** → as is.
- **Progress**: the callback's percentage is −1 until the first `[download] … ETA mm:ss` line, then
  0–100 per file, and starts again at 0 for the second file of a pair. HLS also prints a
  `100.0% of ~1.00KiB … ETA Unknown (frag 0/N)` line first, which the library's regex ignores.
  `ProgressTracker` should count files by the `[download] Destination:` lines and weight them by
  `filesize`, else `filesize_approx`, else equally (HLS formats have neither).

### Updating yt-dlp

The bundled yt-dlp is not good enough on its own (no Instagram). The update should run before the
first download where possible: P04's `UpdateWorker`, enqueued at app start on the same unique
`engine` chain, already runs ahead of a download enqueued after it. If the update fails, the bundled
yt-dlp still handles YouTube (with `--fixup never`), X and TikTok.

## Consequences

- P03 implements the rules above and unit-tests `FormatPicker` against the shapes seen here
  (YouTube with and without HLS, dubbed audio, Instagram's `null` codecs, X's `null`-codec
  progressive formats, TikTok's HEVC).
- The pipeline needs a one-time re-extract on HTTP 403 and container sniffing before assembly.
- `EngineProbeTest` stays as the tool for "this site seems broken". Its candidate list (HTTPS pair,
  HLS pair, single) and `allCandidates=true` are for diagnosis; production takes the first.
- Repeated probing gets rate-limited (Instagram after eight extractions in eleven minutes): probe
  sparingly.
- Reddit is a must-work site in `docs/product.md` but has not been probed.
- YouTube extraction with the current yt-dlp takes 10–15 s, which the UI's *preparing* state has to
  carry.
- TikTok's best format is HEVC. Phones play it, but a file shared to an old PC player may not; an
  H.264 preference would cost TikTok its 1080p (its H.264 tops out at 540p).

## Alternatives considered

- **Height cap instead of short-side cap**: picks 480p–720p for vertical videos (seen in run 1).
- **HLS first on YouTube**: always needs a TS remux and is slower; HTTPS worked whenever the
  extraction was not refused as a whole.
- **Falling back to other formats of the same extraction on 403**: run 5 showed every format of a
  refused extraction fails alike.
- **Bitrate-first audio ranking**: picks a dubbed track's twin by 0.001 kbps; language first is what
  yt-dlp itself does.
- **Letting yt-dlp's fixups run**: crash on the bundled yt-dlp, warnings only on the updated one,
  and nothing they would do is needed once `MediaMuxer` assembles the file.

## Note from P03

Rule 2 asks a single format for a video codec that is "muxable or unknown", while *Only webm*
keeps a single WebM as is. P03 reads the codec rule as applying to MP4 singles only, since only
they may need a remux (an HLS single is MPEG-TS): an MP4 single needs H.264/HEVC video **and** AAC
audio, or codecs left unstated; a WebM single is kept whatever its codecs. P03 also considers only
formats yt-dlp downloads without ffmpeg (`https`, `http`, `m3u8_native`, `m3u8`,
`http_dash_segments`). On the phone, every plan P03 picked matched the *Should pick* rows above.

After P03 the owner dropped Reddit from the must-work sites ([DEC-005](DEC-005-posts-with-several-videos.md)),
so its absence from these results no longer matters.
