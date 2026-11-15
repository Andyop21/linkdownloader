# Product

## What it is

A tiny Android app for one person's phone. Copy a video link anywhere, open the app, tap
**Paste**, and the video is saved to `Downloads/`. Everything happens on the phone: no server, no
account, no ads, no pop-ups, nothing running when no download is.

It is installed by sideloading the APK. It is not published on Google Play.

## The screen

One screen, centred, top to bottom:

1. **A title bar**: the app name, small and centred.
2. **The orb**, in the middle of the screen: a disc in the status's colour that shows the state at
   a glance, with the progress as a ring around it and the percentage inside while downloading.
3. **The status** under the orb: one plain line about what is happening, a detail line, and the
   action that fits (Cancel, Open, Try again, Download again).
4. **The bar** near the bottom: a single-line text field that holds the link, with a clear button
   while it holds text.
5. **The Paste link button** under the bar, at the bottom of the screen where the thumb rests: full
   width and the obvious thing to tap.

A tap on empty space puts the keyboard away. While the keyboard is open, the orb shrinks and the
detail line folds away, so the bar and **Paste link** stay whole above the keyboard.

No menus, no settings screen, no history list, no second screen.

## Behaviour

| # | When | Then |
|---|---|---|
| B1 | **Paste** is tapped and the clipboard holds an `http`/`https` link | The link fills the bar and the download starts at once. If the clipboard text holds other words around the link (share text), the first link is used. The link saved last is the exception (B12). |
| B2 | **Paste** is tapped and the clipboard holds no link, or is empty | The bar is left unchanged and the status says that there is no link on the clipboard. Nothing starts. |
| B3 | A link is typed or pasted into the bar with the keyboard, and the keyboard's action key is pressed | Same as B1, using the bar's text (the bar then holds just the link); same as B2 if it holds no link, with a message that the bar holds no link. |
| B4 | A download is running | The status shows the video's title once known, then the progress, which never goes backwards. When several videos of one post download, it also says which one of how many ("2 of 3"), and the progress covers them all. **Paste** and the keyboard action are disabled. One download at a time. |
| B5 | **Cancel** is tapped on the screen or in the notification | The download stops, its partial files are deleted, the bar is emptied and the status returns to idle. Videos of the same post that were already saved stay saved. |
| B6 | The download finishes | The video is in `Downloads/`, named after its title. The bar empties itself, ready for the next link. The status says it was saved, with the video's title (or its file name when it has none) and an **Open** action that plays it. For several videos it says how many were saved (and how many could not be, if any), and **Open** plays the first. The phone gives a light haptic tick. |
| B7 | The download fails | The status gives a short title and a plain reason (table below). No technical error text, ever. The link stays in the bar. When the phone was offline or the cause is unknown, **Try again** starts the same link again. |
| B8 | The app is left while a download runs | The download continues, with a notification showing progress and a Cancel action. When it ends, the notification says saved (tap to open) or failed. When the link turns out to be a post with several videos while the app is not on screen, a notification asks to choose which videos to save and opens the app on the choice. |
| B9 | The app is opened again later | It starts idle. A download still running is shown as running. |
| B10 | The header is long-pressed | A small sheet shows the app version and the yt-dlp version, with an **Update yt-dlp** button. This is the only "settings" surface and it stays hidden. |
| B11 | The link leads to a post with several videos (an X post with several clips, an Instagram carousel) | After *preparing*, a sheet shows a thumbnail of each video, with its length, all of them selected. Tapping a thumbnail selects or clears it, so one, some or all can be chosen; **Select all** / **Select none** selects or clears every one. **Download** (with the count; "Select a video" and disabled when none is selected) saves the chosen videos one after another, each as its own file. Closing the sheet downloads nothing and returns to idle. A post with one video skips the sheet. Pictures in a post are not offered. |
| B12 | **Paste** or the keyboard action finds the link this screen saved last | Nothing starts, so no duplicate is made. The link fills the bar and the status says it was already saved, with **Open** and **Download again** (which saves a second copy on purpose). Only the last saved link counts, and only until the app is closed. |
| B13 | The bar holds text and no download or choice is in progress | A clear button sits at the bar's end: it empties the bar and dismisses the last message. Editing the bar also dismisses a message about an earlier link. |

### Failure reasons

| Reason | Shown when | Title and message | Try again |
|---|---|---|---|
| No internet | No network before or during the download | "You're offline" / "Connect to the internet and try again." | Yes |
| Not supported | yt-dlp does not know the site or the link is not a video | "Link not supported" / "This link doesn't lead to a video that can be saved." | No |
| Unavailable | The video is private, removed, age- or region-locked | "Video unavailable" / "This video is private, removed or restricted." | No |
| Needs login | The site asks for a login or a bot check | "Sign-in required" / "This site only shows the video to signed-in users." | No |
| Unknown | Anything else | "Download failed" / "Something went wrong. Try again in a moment." | Yes |

The notification shows the message alone, under "Couldn't download".

## What gets downloaded

- The video the link points to; for a post with several videos, the ones chosen in B11. Never a
  whole playlist or profile.
- Best quality up to 1080p, video with audio, saved as `.mp4` (`.webm` only when a site offers
  nothing else).
- Sites: whatever yt-dlp supports. The ones that must work: YouTube (including Shorts), TikTok,
  Instagram (Reels and posts), X.

## Look and feel

Calm, clean and precise, like a well-made tool, with one living element at its centre:

- A cool-gray neutral palette with one blue for actions. The orb takes the status's colour: blue
  while ready or working, green when saved, orange when there is nothing to download, red on
  failure. A colour never stands alone; the words under it always say the same.
- Lato, a tight type scale and plain, short wording. No mascot, no exclamation marks.
- A centred composition on an even 4 dp spacing scale; one corner scale and capsule buttons.
- Purposeful motion: the screen's blocks rise in on launch; ready, a halo breathes out from the
  orb; while the link is read an arc turns and the arrow drops into the tray; while downloading
  the ring fills and the percentage counts up; when saved the orb turns green, the check pops in
  and one ripple spreads; on a failure or when there is nothing to download the orb shakes once. A
  new status fades in where it stands, and buttons dip when pressed. With the system's animations
  turned off, everything is still.
- Light and dark themes, both equally finished. Text contrast meets WCAG AA.
- Material 3 components with the app's own colours and shapes, not the stock look.

The exact palette, type, shapes and motion are set in `ui/theme` and described in
[architecture.md](architecture.md) § *Look and feel*; why they were chosen is in
[DEC-008](work/decisions/DEC-008-professional-look.md).

## Not in scope

Accounts, logins or cookies, playlists and profiles (a post with several videos is not a playlist:
see B11), audio-only downloads, quality pickers, a download history,
a settings screen, a share-sheet target, Google Play, iOS, ads and analytics. Any of these is
proposed to the owner before it is built.
