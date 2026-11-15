# Product

## What it is

A tiny Android app for one person's phone. Copy a video link anywhere, open the app, tap
**Paste**, and the video is saved to `Downloads/`. Nothing else.

It is installed by sideloading the APK. It is not published on Google Play.

## The screen

One screen, top to bottom:

1. **A small header**: the app name and a friendly little illustration.
2. **The bar**: a rounded, single-line text field that holds the link.
3. **The Paste button**: large, round and the obvious thing to tap.
4. **A status area** under the button: one short line or card that says what is happening.

No menus, no settings, no history list, no second screen.

## Behaviour

| # | When | Then |
|---|---|---|
| B1 | **Paste** is tapped and the clipboard holds an `http`/`https` link | The link fills the bar and the download starts at once. If the clipboard text holds other words around the link, the first link is used. |
| B2 | **Paste** is tapped and the clipboard holds no link, or is empty | The bar is left unchanged and the status says, kindly, that there is no link on the clipboard. Nothing starts. |
| B3 | A link is typed or pasted into the bar by the keyboard, and the keyboard's action key is pressed | Same as B1, using the bar's text. |
| B4 | A download is running | The status shows the video's title (once known) and the progress. **Paste** is disabled until the download ends. One download at a time. |
| B5 | The download finishes | The video is in `Downloads/`, named after its title. The status says it was saved and gives the file name. |
| B6 | The download fails | The status gives a short, plain reason: no internet, link not supported, video private or removed, or "something went wrong, try again". No technical error text. |
| B7 | The app is left while a download runs | The download continues, with a notification showing progress. The notification says when it is done or failed. |

## What gets downloaded

- The single video the link points to, never a whole playlist or profile.
- Best available quality, video with audio, saved as `.mp4`.
- Sites: whatever yt-dlp supports. The ones that must work: YouTube (including Shorts),
  TikTok, Instagram (Reels and posts), X, Reddit.

## Look and feel

Cute, soft and calm:

- Pastel palette, rounded shapes, generous spacing, one big friendly button.
- Light and dark themes, both equally pleasant.
- Short, warm wording in the status line.
- Material 3 components with the app's own colours and shapes, not the stock look.

Exact colours, font and illustration are decided when the screen is built and recorded here.

## Not in scope

Accounts or logins, cookies, playlists, audio-only downloads, quality pickers, a download history,
a settings screen, a share-sheet target, Google Play, iOS, ads and analytics. Any of these is
proposed to the owner before it is built.
