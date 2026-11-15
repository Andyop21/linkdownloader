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
- The look: cool grays with one blue, the Lato font, white cards with a hairline edge and a small
  tinted icon for each state, in light and dark. **Paste link** sits at the bottom of the screen,
  within reach of the thumb. Quiet motion: the screen rises in on launch, a new status fades in
  where it stands, progress glides, a live dot breathes while a download runs and buttons dip when
  pressed; all of it keeps still when the phone's animations are off. An app icon (with a
  themed-icon layer) and notification icon showing an arrow into a tray.
- The link field has a clear button, and empties itself when a video is saved or a download is
  cancelled, ready for the next link.
- **Try again** after a failure that may pass (offline, or an unknown error).
- Pasting the link that was just saved does not download it twice: the card says it is already
  saved, with **Open** and **Download again**.
