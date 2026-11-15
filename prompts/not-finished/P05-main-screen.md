# P05 — The main screen, wired end to end

**Goal.** The one screen works exactly as `docs/product.md` describes: paste, keyboard action,
progress, cancel, open, failure messages and the hidden About sheet, driven by a `MainViewModel`
with a single `UiState`. It uses P01's simple theme; P06 makes it beautiful. Function first, but
lay out the components so P06 only has to restyle, not restructure.

**Entry condition.** P04 finished.

**Read first.** `docs/product.md` (all of it), `docs/architecture.md` (§ *Download pipeline* on
`UiState`, *Code layout*, *Permissions*), P04's handoff.

## Scope

`LinkParser`, `MainViewModel`, `UiState`, `MainScreen` and its components, the About sheet, the
notification permission request, and the user-facing strings.

## Out of scope

The final palette, font, mascot artwork, motion and icon (P06). A share-sheet target or any other
entry point.

## Deliverables

1. **`LinkParser`** (`link`, pure Kotlin): `firstLink(text): String?` finds the first `http`/`https`
   URL in any text, trims trailing punctuation and closing brackets that are not part of the URL,
   and rejects anything without a host. Unit tests with real-world share-text shapes (a title plus
   a link, a link with tracking parameters, two links, no link, `www.` without a scheme: treat as
   no link unless the owner says otherwise).
2. **`UiState` and `MainViewModel`**:
   - `UiState`: `Idle`, `NoLink`, `Preparing`, `Choosing(title, choices)`,
     `Downloading(title, position, count, progress)`, `Saving(title)`, `Done(videos, failed)`,
     `Failed(reason)`, plus the bar's text (`docs/architecture.md` § *Download pipeline*).
   - `onPaste(clipText)`, `onSubmit(barText)`, `onCancel()`, `onBarChanged(text)`, `onUpdateEngine()`,
     `onChoose(positions)`, `onDismissChoice()`.
   - A `ChooseVideos` result from P04's worker becomes `Choosing`; `onChoose` enqueues the same link
     again with the chosen positions; `onDismissChoice` returns to `Idle`.
   - Maps `DownloadQueue.observeDownloads()` to `UiState`. A finished job shows `Done`/`Failed` only
     if this ViewModel enqueued it or saw it running (product B9).
   - Takes `DownloadQueue` behind a small interface so unit tests use a fake. Unit tests cover
     every row of the product's behaviour table that is not pure UI.
3. **`MainScreen`**, built from small components (`LinkBar`, `PasteButton`, `StatusCard`,
   `HeaderWithMascot`) that take state and callbacks and hold no logic:
   - Paste reads the clipboard only on tap (`ClipboardManager`, first item, `coerceToText`). Never
     reads it on resume.
   - The bar is single-line with `ImeAction.Go`; Go submits. Paste and Go are disabled while
     `Preparing`, `Downloading` or `Saving`.
   - The status card shows the state's message, a progress indicator while downloading, **Cancel**
     while running, and **Open** when done (`ACTION_VIEW` on the uri; if no app can open it, say so
     kindly).
   - A light haptic tick on `Done`.
   - The mascot slot shows a simple placeholder per mood (P06 replaces it).
   - Layout works from small phones to large ones, in portrait and landscape, and with the largest
     system font size; the content scrolls rather than clips.
4. **Strings.** Every user-facing text in `res/values/strings.xml`, in the warm tone of
   `docs/product.md`. No raw error text, ever.
5. **Notification permission.** On API 33+, the first time a download starts, request
   `POST_NOTIFICATIONS` once. A refusal does not block the download and is not asked again.
6. **Choice sheet** (product B11, DEC-005): a modal bottom sheet with the post's title and a grid
   of the videos' thumbnails, each with its length and a selected mark; all start selected. Tapping
   a thumbnail toggles it; **Select all**; **Download (n)** disabled at zero; dismissing it calls
   `onDismissChoice()`. Thumbnails are loaded from their URLs off the main thread and scaled down
   (plain `HttpURLConnection` + `BitmapFactory`, cached in memory for the sheet's life, a neutral
   placeholder on failure); an image library would be a new dependency and needs a decision
   record first. Each thumbnail has a content description ("Video 2, 0:08, selected").
7. **About sheet** (product B10): a long-press on the header opens a modal bottom sheet with the app
   version, the yt-dlp version, and an **Update yt-dlp** button that calls `onUpdateEngine()` and
   shows "Checking…", then "Up to date" or the new version.
8. **Remove** any debug-only download trigger left by P04 now that the screen exists.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes; `LinkParser` and `MainViewModel`
      are unit-tested.
- [ ] Every behaviour B1–B11 in `docs/product.md` was tried on a real phone and is ticked off in the
      handoff, plus the must-work sites from the screen (YouTube and a Short, Instagram, X, TikTok)
      and a post with several videos (choose one, some, and all).
- [ ] TalkBack reads every control sensibly; touch targets are at least 48 dp.
- [ ] `docs/product.md` and `docs/architecture.md` match what was built; any wording or behaviour
      the owner should look at is listed under *Open questions*.
- [ ] `CHANGELOG.md` *Unreleased* says the app can download from a pasted link.

## Handoff

Write `docs/work/handoffs/P05-main-screen.md` with screenshots described in words (no personal
content in any screenshot that is committed). Then run `bash scripts/finish-prompt.sh P05`.
