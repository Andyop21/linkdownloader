# Product

## What it is

A tiny Android app for one person's phone. Copy a video link anywhere, open the app, tap
**Paste**, and the video is saved to `Downloads/`. Everything happens on the phone: no server, no
account, no ads, no pop-ups, nothing running when no download is.

It is installed by sideloading the APK. It is not published on Google Play.

## The screen

One screen, top to bottom:

1. **A small header**: the app name and a little mascot that reacts to what is happening.
2. **The bar**: a rounded, single-line text field that holds the link.
3. **The Paste button**: large, rounded and the obvious thing to tap.
4. **A status card** under the button: one short, friendly line about what is happening, with
   progress while downloading and an action when there is one (Cancel, Open).

No menus, no settings screen, no history list, no second screen.

## Behaviour

| # | When | Then |
|---|---|---|
| B1 | **Paste** is tapped and the clipboard holds an `http`/`https` link | The link fills the bar and the download starts at once. If the clipboard text holds other words around the link (share text), the first link is used. |
| B2 | **Paste** is tapped and the clipboard holds no link, or is empty | The bar is left unchanged and the status says, kindly, that there is no link on the clipboard. Nothing starts. |
| B3 | A link is typed or pasted into the bar with the keyboard, and the keyboard's action key is pressed | Same as B1, using the bar's text; same as B2 if it holds no link. |
| B4 | A download is running | The status shows the video's title once known, then the progress, which never goes backwards. When several videos of one post download, it also says which one of how many ("2 of 3"), and the progress covers them all. **Paste** and the keyboard action are disabled. One download at a time. |
| B5 | **Cancel** is tapped on the status card or in the notification | The download stops, its partial files are deleted, and the status returns to idle. Videos of the same post that were already saved stay saved. |
| B6 | The download finishes | The video is in `Downloads/`, named after its title. The status says it was saved, with the file name and an **Open** action that plays it. For several videos it says how many were saved (and how many could not be, if any), and **Open** plays the first. The phone gives a light haptic tick. |
| B7 | The download fails | The status gives a short, plain reason (table below). No technical error text, ever. |
| B8 | The app is left while a download runs | The download continues, with a notification showing progress and a Cancel action. When it ends, the notification says saved (tap to open) or failed. When the link turns out to be a post with several videos while the app is not on screen, a notification asks to choose which videos to save and opens the app on the choice. |
| B9 | The app is opened again later | It starts idle. A download still running is shown as running. |
| B10 | The header is long-pressed | A small sheet shows the app version and the yt-dlp version, with an **Update yt-dlp** button. This is the only "settings" surface and it stays hidden. |
| B11 | The link leads to a post with several videos (an X post with several clips, an Instagram carousel) | After *preparing*, a sheet shows a thumbnail of each video, with its length, all of them selected. Tapping a thumbnail selects or clears it, so one, some or all can be chosen; **Select all** selects every one. **Download** (with the count, disabled when none is selected) saves the chosen videos one after another, each as its own file. Closing the sheet downloads nothing and returns to idle. A post with one video skips the sheet. Pictures in a post are not offered. |

### Failure reasons

| Reason | Shown when | Message (tone, not final wording) |
|---|---|---|
| No internet | No network before or during the download | "No internet right now. Try again when you're back online." |
| Not supported | yt-dlp does not know the site or the link is not a video | "This link doesn't lead to a video I can download." |
| Unavailable | The video is private, removed, age- or region-locked | "This video isn't available to download." |
| Needs login | The site asks for a login or a bot check | "This site wants you logged in, so I can't grab this one." |
| Unknown | Anything else | "Something went wrong. Try again in a moment." |

## What gets downloaded

- The video the link points to; for a post with several videos, the ones chosen in B11. Never a
  whole playlist or profile.
- Best quality up to 1080p, video with audio, saved as `.mp4` (`.webm` only when a site offers
  nothing else).
- Sites: whatever yt-dlp supports. The ones that must work: YouTube (including Shorts), TikTok,
  Instagram (Reels and posts), X.

## Look and feel

Cute, soft and calm:

- Pastel palette, rounded shapes, generous spacing, one big friendly button.
- A rounded font (Nunito) and short, warm wording.
- The mascot idles gently, bobs while working, cheers on success and looks worried on failure.
  With the system's animations turned off, it stays still.
- Light and dark themes, both equally pleasant. Text contrast meets WCAG AA.
- Material 3 components with the app's own colours and shapes, not the stock look.

The exact palette, shapes and motion are set in `ui/theme` and described in
[architecture.md](architecture.md) § *Look and feel*.

## Not in scope

Accounts, logins or cookies, playlists and profiles (a post with several videos is not a playlist:
see B11), audio-only downloads, quality pickers, a download history,
a settings screen, a share-sheet target, Google Play, iOS, ads and analytics. Any of these is
proposed to the owner before it is built.
