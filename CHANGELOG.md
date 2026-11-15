# Changelog

User-visible changes to the app. Workflow and documentation changes are not listed here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- The screen: copy a video link, tap **Paste** (or type it and press Go), and the video is saved to
  `Downloads/`, with progress, **Cancel**, **Open** and a plain reason when it fails. A post
  with several videos offers a choice of which to save. A long-press on the header shows the app
  and yt-dlp versions and can update yt-dlp.
- Downloads keep going when the app is left, with a notification that shows the progress and can
  cancel the download, then says when the video is saved (tap to play it) or why it failed.
- yt-dlp updates itself in the background, at most once a day.
- The look: a centred screen around an orb that shows what is happening, in cool grays with one
  blue and the Lato font, in light and dark. The orb breathes while ready, drops its arrow into the
  tray while the link is read, fills a ring with the percentage inside while downloading, turns
  green with a check and a ripple when saved, and shakes once on a failure; all of it keeps still
  when the phone's animations are off. The link field and **Paste link** sit together at the
  bottom, within reach of the thumb, and a tap on empty space puts the keyboard away. An app icon (with a
  themed-icon layer) and notification icon showing an arrow into a tray.
- The link field has a clear button, and empties itself when a video is saved or a download is
  cancelled, ready for the next link.
- **Try again** after a failure that may pass (offline, or an unknown error).
- Pasting the link that was just saved does not download it twice: the card says it is already
  saved, with **Open** and **Download again**.
