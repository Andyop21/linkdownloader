# Handoff — P07 Release 1.0.0

Date: 2026-11-16 · Prompt: [P07](../../../prompts/finished/P07-release.md)

## What changed

1. **Signing.** The release build reads `keystore.properties` (ignored by git) and stops at
   `checkReleaseSigning` with a clear message when the file, a key in it or the keystore is
   missing. The owner created the keystore outside the repository with the documented `keytool`
   command (`CN=linkdownloader`, no personal name). `a9c35f3`.
2. **Shrinking.** R8 and resource shrinking on, with keep rules for youtubedl-android, Jackson,
   Commons Compress and the app's serializers (`a9c35f3`), plus class names in `download` and line
   numbers kept so release logs stay readable (`b757173`).
3. **Version** `versionCode 100`, `versionName "1.0.0"`; scheme in `docs/development.md`
   § *Versions*. `a9c35f3`.
4. **Release test pass** on the owner's phone (below).
5. **Size and start-up** (below).
6. **Public-ready docs.** `LICENSE` (GPL-3.0-or-later, the owner's choice) and a first public
   README (`ceb3d5c`); then the docs checked against the code and 13 drifts fixed, the look's
   decision record (DEC-008) rewritten so it names no other project, and `CHANGELOG.md` moved
   under `[1.0.0] - 2026-11-16` (`f19e380`); the README rewritten in full, with light-mode
   screenshots taken on the phone and a light/dark comparison (`64d5a01`, `d11749e`).

Between the first test pass and this one, at the owner's request outside this prompt, the screen
was redesigned (DEC-008: `7c44707`, `73c1a1c`), the keyboard no longer covers the field
(`e39f16a`), and BUG-002 was fixed (`5c78ce3`). That is why the pass below was run again on the
final build.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease` passes (JDK 21).
- `apksigner verify --print-certs` on `app-release.apk`: verified, signer `CN=linkdownloader`.
- The signed release APK of `d11749e` was installed with `adb install -r` on the owner's phone
  (Samsung, Android 17 / API 37, over wireless debugging) and driven through the screen with
  `adb` (typed links and the keyboard's Go key; uiautomator for reading the screen).

### Release test table (final build)

| # | Check | Result |
|---|---|---|
| Sites | YouTube video | Saved, 1920×1080 MP4, 9.4 MB |
| | YouTube Short | Saved, 1080×1920 MP4, 16.3 MB, about 13 s |
| | Instagram Reel | Saved, 720×1280 MP4, 6.2 MB, about 9 s |
| | X post, one video | Saved, 1080×1920 MP4, 18.2 MB, about 9 s; a taken name got ` (1)` |
| | TikTok | Saved, 1080×1920 MP4, 0.7 MB, about 8 s |
| B3 | Text that is not a link, then Go | "That isn't a link" with its hint; nothing started |
| B4 | A 288 MB YouTube video | Title, "Downloading", the ring and percentage, **Cancel**; **Paste link** disabled |
| B5 | **Cancel** on the screen | Back to idle at once, field empty, no file or pending entry left in `Download/` |
| B6 | Finished download | Green orb, title, "Saved to Downloads", **Open**, field emptied |
| B7 | This app's network cut (`set-package-networking-enabled false`) | "You're offline" / "Connect to the internet and try again." with **Try again**; after restoring the network, **Try again** saved the video |
| B8 | Home pressed mid-download | Ongoing notification with the title, "Downloading" and progress; then "Saved to Downloads" with the file name |
| B9 | Activity finished with Back mid-download, then started again | The new screen showed the running download |
| B10 | Long-press on the title bar | App 1.0.0, yt-dlp 2026.10.26; **Update yt-dlp** answered "Up to date" |
| B11 | X post with two videos | Sheet with both thumbnails and lengths, all selected; clearing and selecting changed the button (2 videos, 1 video, none), **Select none** / **Select all** toggled; **Download 2 videos** saved both ("2 videos saved") |
| B12 | The same post's link again | "Already in your Downloads." with **Download again** and **Open**; nothing started |
| B13 | Clear button | Emptied the field and dismissed the message |
| Themes | Light and dark | Ready, reading, downloading, saved, already saved, failed and the About sheet checked in both while taking the README screenshots |
| Reboot | Phone restarted, then the app's first start and a fresh TikTok download | Cold start, saved normally |
| B5 | **Cancel** in the notification (by the owner, by hand) | The download stopped and no "Saved" notification followed |
| B6 | Haptic tick on a save with the app open (by the owner, by hand) | Felt |

From the first pass on the pre-redesign build (same pipeline and ViewModel rules), still standing:
B1 (share text around a link, then Paste: only the link used) and B2 (clipboard without a link:
"no link" status, bar unchanged), the notification permission asked once, the "Choose which
videos to save" notification opening the app on the sheet, and closing the sheet downloading
nothing. B1 and B2 were not repeated on the final build because `adb` cannot set the clipboard;
`MainViewModelTest` covers both.

Every test file was deleted from the phone's `Download/` afterwards (selected by the time the run
started); the owner's own files were not touched. The test links are not recorded here.

### Size and start-up

- `app-release.apk`: 22,643,329 bytes (22.6 MB), `arm64-v8a` only.
- Cold start, tap to first frame (`am start -W`, `TotalTime`): 137 ms on the first start after a
  reboot; 91–97 ms on later cold starts.
- First launch after install: the screen draws at once, and youtubedl-android unpacks Python in the
  background in about 0.3 s (measured in the first pass on a fresh install). A download started
  before that finishes shows "Getting ready…".

### Not checked

- TalkBack.

One UI does not expose the notification's action buttons to `uiautomator`, so Cancel from the
notification and the haptic tick were checked by the owner by hand (table above).

## Docs updated

`README.md`, `CHANGELOG.md`, `docs/product.md`, `docs/architecture.md`, `docs/development.md`,
`docs/index.md`, `docs/definition-of-done.md`, `docs/work/README.md`,
`docs/work/decisions/DEC-008-professional-look.md` (renamed), `prompts/finished/P07-release.md`
(deliverable 6 noted as done ahead), and this handoff.

## Bugs filed

- [BUG-002](../bugs/BUG-002-next-link-replaced-by-post-link.md), found by the owner on the
  release build and fixed in `5c78ce3`.

## Open questions

- A few KDoc comments in `ui/theme/Spacing.kt` describe spacing steps the screen no longer uses
  (`Space.xxl`, `Space.xxxl`). Harmless; worth tidying in a later session.
- An Instagram post that yt-dlp reports as "There is no video in this post" shows *Link not
  supported*. That is correct for a photo post; if a post with a video ever does this, it is
  Instagram hiding it from logged-out requests, which the app does not work around (no logins).

## Next

`worktree` is ready for 1.0.0. `bash scripts/promote.sh` fast-forwards `master` to it. After
that, `v1.0.0` can be tagged on `master` and the signed APK attached to a GitHub release, at
which point the README's install section already points to the Releases page.
