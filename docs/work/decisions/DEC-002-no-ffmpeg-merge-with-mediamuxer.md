---
id: DEC-002
title: No ffmpeg; pick formats in Kotlin and join them with MediaMuxer
date: 2026-11-15
status: accepted
---

# DEC-002 — No ffmpeg; pick formats in Kotlin and join them with MediaMuxer

## Context

YouTube and Reddit serve the video and the audio as separate streams above low resolutions, so
something has to join them. yt-dlp normally uses ffmpeg for that. youtubedl-android's `ffmpeg`
module 0.18.1 has open defects: its payload lacks `libc++_shared.so`, which `librubberband.so`
needs (upstream #369), and five of its bundled libraries are not 16 KB page aligned (upstream
#365). It also adds tens of megabytes per ABI.

yt-dlp without ffmpeg refuses a `video+audio` format request ("You have requested merging of
multiple formats but ffmpeg is not installed") unless errors are ignored.

## Decision

- Do not ship ffmpeg.
- Ask yt-dlp for the video's JSON once (`-J`), choose the formats in Kotlin (`FormatPicker`), then
  download exactly those formats by ID from the saved JSON (`--load-info-json`, `-f <id>` or
  `-f <video-id>,<audio-id>`).
- Choose only streams Android's `MediaMuxer` can write to MP4: H.264 or HEVC video, AAC audio.
- Join a pair, or remux an MPEG-TS single file, with `MediaExtractor` + `MediaMuxer`.

## Consequences

- A smaller APK and no dependency on a broken module.
- YouTube tops out at 1080p in H.264 (higher resolutions are VP9 or AV1 only). The product caps at
  1080p anyway.
- Format choice becomes plain Kotlin, unit-tested against sample JSON instead of a format string.
- Anything that needs re-encoding is out of reach. The picker never selects such formats; if a
  site offers nothing usable, the download fails as *not supported*.

## Alternatives considered

- **youtubedl-android's ffmpeg module**: open crash and alignment defects, large.
- **Another ffmpeg build** (ffmpeg-kit forks): ffmpeg-kit was retired in 2025; forks are
  unofficial and still large.
- **Progressive formats only**: no merge needed, but YouTube's progressive format is 360p.
