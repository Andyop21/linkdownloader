# linkdownloader

A tiny Android app that does one thing: copy a video link, tap **Paste**, and the video
lands in your phone's Downloads folder.

It works with the sites [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports (YouTube, TikTok,
Instagram, X, Reddit and many more), through
[youtubedl-android](https://github.com/yausername/youtubedl-android).

> Status: in development. Built for personal use and installed by sideloading the APK.

## How it works

1. Copy a link in any app.
2. Open linkdownloader and tap **Paste**.
3. If the clipboard holds a valid link, the download starts; the finished video is saved to
   `Downloads/`.

See [docs/product.md](docs/product.md) for the full behaviour.

## Building

Requires Android Studio (JDK 17) and the Android SDK.

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install it on a connected device
```

More commands and setup notes: [docs/development.md](docs/development.md).

## Working in this repository

Read [AGENTS.md](AGENTS.md) (identical to [CLAUDE.md](CLAUDE.md)) first. It holds the rules for
humans and coding agents alike. Documentation starts at [docs/index.md](docs/index.md), and
session prompts live in [prompts/](prompts/README.md).

## Responsible use

Download only content you have the right to save, and respect each platform's terms of service.
