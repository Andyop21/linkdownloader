# Changelog

User-visible changes to the app. Workflow and documentation changes are not listed here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- The screen: copy a video link, tap **Paste** (or type it and press Go), and the video is saved to
  `Downloads/`, with progress, **Cancel**, **Open** and a friendly reason when it fails. A post
  with several videos offers a choice of which to save. A long-press on the header shows the app
  and yt-dlp versions and can update yt-dlp.
- Downloads keep going when the app is left, with a notification that shows the progress and can
  cancel the download, then says when the video is saved (tap to play it) or why it failed.
- yt-dlp updates itself in the background, at most once a day.
