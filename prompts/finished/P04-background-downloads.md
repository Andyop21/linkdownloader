# P04 — Background downloads, notifications and yt-dlp updates

**Goal.** Downloads keep going when the app is left, show their progress in a notification that
can cancel them, and yt-dlp stays current on its own. This wraps P03's `VideoDownloader` in
WorkManager; the screen comes in P05.

**Entry condition.** P03 finished.

**Read first.** `docs/architecture.md` (§ *Background work*, *Permissions*, *Download pipeline*),
`docs/product.md` (B4, B5, B8, B10), P03's handoff.

## Scope

`DownloadWorker`, `UpdateWorker`, the notification channel and notifications, the enqueue/observe
API the ViewModel will use, and the manifest entries they need.

## Out of scope

The screen and the ViewModel (P05). The visual polish of the notification icon (P06 supplies the
final small icon; use a simple placeholder vector until then).

## Deliverables

1. **WorkManager** in the version catalog. Default initialisation is fine unless a custom
   `Configuration` is needed; if so, use `Configuration.Provider` on `LinkDownloaderApp`.
2. **`DownloadWorker`** (`CoroutineWorker`):
   - Input: the link. Tag: `download`.
   - Calls `setForeground` immediately with a `ForegroundInfo` of type
     `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
   - Runs `VideoDownloader.download`, publishing `setProgress` data: stage (preparing,
     downloading, saving), title and fraction. Throttle progress updates (about four per second at
     most) so neither WorkManager nor the notification is flooded.
   - Input: the link and, optionally, the chosen video positions of a post with several videos
     (product B11, DEC-005); passed through to `VideoDownloader.download(link, jobId, selection, …)`.
   - Output on success (`Saved`): each video's uri, file name and title, plus the `failed` count.
     On `ChooseVideos`: success with the choices written to a JSON file in the cache and only its
     path (and the post's title) in the output `Data`, which WorkManager caps at 10 KB. On failure:
     the `FailureReason` name. A failed download returns `Result.failure`, never `retry`.
   - Progress data also carries the position and count from `TitleKnown` ("2 of 3").
   - Stopping the work cancels the coroutine, which P03 already turns into a clean cancel.
3. **Notifications** (`download/Notifications.kt`):
   - One channel, "Downloads", low importance, created at app start.
   - The ongoing notification: title (or "Getting ready…"), determinate progress once known, and a
     **Cancel** action using `WorkManager.createCancelPendingIntent`.
   - On `ChooseVideos`, only when the app is not in the foreground: "Choose which videos to save",
     tapping opens the app on the choice (the choice is offered by P05's screen).
   - On completion: "Saved to Downloads" with the file name (or "3 videos saved"); tapping opens the (first) video
     (`ACTION_VIEW` on the content uri with `FLAG_GRANT_READ_URI_PERMISSION`). On failure: the
     friendly reason from `docs/product.md`. Neither is ongoing; both auto-cancel on tap.
   - Tapping the ongoing notification opens the app.
4. **Manifest**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, and
   WorkManager's `androidx.work.impl.foreground.SystemForegroundService` merged in with
   `android:foregroundServiceType="dataSync"`. The runtime request for `POST_NOTIFICATIONS` belongs
   to the screen (P05).
5. **`UpdateWorker`**: calls `YoutubeDL.getInstance().updateYoutubeDL(context, STABLE)` and stores
   the time of the last successful check. Needs a network connection. Fails silently (logs only).
6. **`DownloadQueue`** (the API P05 uses), in `download`:
   - `startDownload(link, selection = null)`: enqueues a `DownloadWorker` on the unique work name `engine` with
     `APPEND_OR_REPLACE`.
   - `updateEngineIfDue()`: enqueues `UpdateWorker` on `engine` with `APPEND_OR_REPLACE` when the
     last successful check is older than 24 hours. Called from `LinkDownloaderApp.onCreate`.
   - `updateEngineNow()`: the same, unconditionally (for the About sheet).
   - `cancel(id)`, and `observeDownloads(): Flow<List<WorkInfo>>` filtered to the `download` tag.
   - `engineVersion()`: yt-dlp's version string, for the About sheet.
7. **Manual checks on the phone**: start a download from a debug-only trigger or the probe, leave
   the app, watch the notification progress, cancel from the notification, start another, let it
   finish, tap the notification to play it. Turn on aeroplane mode mid-download and confirm the
   *no internet* failure. Trigger an update while a download runs and confirm it waits.
   Remove any debug-only trigger before finishing, or keep it strictly in the `debug` source set.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- [ ] Every manual check in deliverable 7 passed on a real phone and is listed in the handoff.
- [ ] Unit tests cover what is pure here (for example the progress throttling and the
      "update due" rule, with an injectable clock).
- [ ] `docs/architecture.md` § *Background work* and § *Permissions* match the code.

## Handoff

Write `docs/work/handoffs/P04-background-downloads.md`, then run
`bash scripts/finish-prompt.sh P04`.
