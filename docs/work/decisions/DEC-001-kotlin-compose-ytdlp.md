---
id: DEC-001
title: Kotlin, Jetpack Compose and youtubedl-android
date: 2026-11-15
status: accepted
---

# DEC-001 — Kotlin, Jetpack Compose and youtubedl-android

## Context

The app is one Android screen that turns a link from the clipboard into a saved video, for many
sites. The hard part is not the UI but resolving and downloading media from sites that change
often.

## Decision

- Native Android in **Kotlin** with **Jetpack Compose** and Material 3.
- **youtubedl-android** for the download: it embeds yt-dlp (with Python) and ffmpeg, so every site
  yt-dlp supports works, and video and audio streams can be merged on the phone.
- **Sideloaded APK**, `minSdk 29`, `arm64-v8a` only.

## Consequences

- One small native codebase and no cross-platform bridge.
- The APK is large (tens of MB) because of the embedded Python and ffmpeg; one ABI keeps it in check.
- yt-dlp must be kept current, so the app updates it in the background (see `architecture.md`).
- Google Play is not an option for a downloader of this kind; sideloading is the distribution.

## Alternatives considered

- **Flutter or React Native**: still need a native module for yt-dlp; more moving parts for one screen.
- **Own extractors per site**: break whenever a site changes; yt-dlp is maintained for exactly that.
- **A server that downloads for the phone**: needs hosting and moves the files twice.
