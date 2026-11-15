---
id: DEC-001
title: Kotlin, Jetpack Compose and youtubedl-android
date: 2026-11-15
status: accepted
---

# DEC-001 — Kotlin, Jetpack Compose and youtubedl-android

## Context

The app is one Android screen that turns a link from the clipboard into a saved video, for many
sites, entirely on the phone: no server and nothing always on. The hard part is not the UI but
resolving and downloading media from sites that change often. Since yt-dlp 2025.11, full YouTube
support also needs a JavaScript runtime to solve the player's challenges.

## Decision

- Native Android in **Kotlin** with **Jetpack Compose** and Material 3.
- **yt-dlp on the phone, through youtubedl-android's `library` module** (Maven Central
  `io.github.junkfood02.youtubedl-android:library`, 0.18.1). It bundles Python 3.11, yt-dlp and
  QuickJS, so every site yt-dlp supports works, including YouTube's JS challenges.
- yt-dlp itself is **updated on the phone** (`updateYoutubeDL`), so site fixes arrive without a new
  library release.
- All yt-dlp calls sit behind the app's own pipeline in the `download` package, so the engine can
  be swapped without touching the UI.
- **Sideloaded APK**, `minSdk 29`, release builds `arm64-v8a` only.

## Consequences

- One small native codebase, no server, no cross-platform bridge.
- The library has had no release since November 2025. The parts it freezes are Python 3.11 and
  its QuickJS build; yt-dlp is not frozen. yt-dlp will drop Python 3.11 at some point after its
  end of life in October 2027. Then, or if QuickJS falls behind, the engine moves to Chaquopy
  (Python 3.10–3.15, actively released) with yt-dlp from pip and a QuickJS-NG executable, behind
  the same pipeline.
- Its QuickJS adds a few seconds to each YouTube extraction (upstream issue #371). Acceptable for
  one video at a time.
- youtubedl-android is GPL-3.0. An APK that bundles it and is given to others must be distributed
  under GPL-3.0-compatible terms; the repository's license should be chosen with that in mind.
- Google Play is not an option for a downloader of this kind; sideloading is the distribution.

## Alternatives considered

- **A server that downloads for the phone**: needs hosting and something always on; ruled out.
- **Chaquopy now**: maintained and modern, but more to assemble (QuickJS build, runtime yt-dlp
  updates); kept as the fallback.
- **NewPipe Extractor**: pure Java, but YouTube and a few others only; no TikTok, Instagram or X.
- **Flutter or React Native**: still need a native module for yt-dlp; more moving parts for one screen.
- **Own extractors per site**: break whenever a site changes; yt-dlp is maintained for exactly that.
