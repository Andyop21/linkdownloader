# Handoff — P04 Background downloads, notifications and yt-dlp updates

Date: 2026-11-15 · Prompt: [P04](../../../prompts/finished/P04-background-downloads.md)

## What changed

All in `203768b`:

1. **WorkManager** `work-runtime` 2.12.0 in the catalog (its coroutine and `Flow` APIs need no
   `-ktx`), default initialisation; kotlinx-coroutines-test for JVM tests. Recorded in
   [DEC-006](../decisions/DEC-006-workmanager-background-downloads.md).
2. **`DownloadWorker`**: `setForeground` at once (`dataSync`), `VideoDownloader.download` with the
   work ID as job ID and the optional selection, progress folded into `DownloadProgress` (stage,
   title, "2 of 3", overall fraction) and published at most every 250 ms through `throttleLatest`.
   Output: `Saved` (uris, file names, titles, saved and failed counts), `Choose` (title and the
   path of the choices JSON in `cache/choices/`), or `Failed` (reason name, `Result.failure`). If
   Android refuses the foreground service, the download goes on as ordinary work.
3. **`DownloadData`**: every input, progress and output key, with typed readers (`progressOf`,
   `resultOf`) for P05. Titles cut to 200 characters; a saved list that would pass 10 KB drops its
   last videos but keeps their count. **`ChoicesFile`** writes and reads the choices; only the
   newest list is kept.
4. **`Notifications`**: the low-importance "Downloads" channel, created at app start; ongoing
   (ID 1) with title or "Getting ready…", stage, "2 of 3", progress and **Cancel**; result (ID 2):
   "Saved to Downloads" with the file name or "N videos saved", tap plays the first video, or
   "Couldn't download" with the friendly reason; "Choose which videos to save" (ID 3) only when no
   activity is on screen, tap opens the app with `EXTRA_CHOOSE_WORK_ID`. A placeholder small icon,
   `ic_stat_download`. The failure messages are string resources, mapped by `FailureReason.messageRes`.
5. **Manifest**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, and
   `SystemForegroundService` merged with `foregroundServiceType="dataSync"`.
6. **`UpdateWorker`**: `updateYoutubeDL(STABLE)`, records the last successful check, rechecks the
   daily rule when it runs unless forced. No network constraint and always `success` (it skips
   offline), so it never holds back or fails a download queued after it (DEC-006).
7. **`DownloadQueue`**: `startDownload(link, selection)`, `updateEngineIfDue()` (from
   `LinkDownloaderApp.onCreate`), `updateEngineNow()`, `cancel(id)`, `observeDownloads()`,
   `engineVersion()` (stored version after an update, else `yt-dlp --version`). `EngineUpdateRule`
   is the pure "due" rule.
8. **Debug-only trigger**: `debug.DebugDownloadActivity` in `app/src/debug`, started from `adb`;
   absent from the release manifest (checked).
9. `VideoDownloader` now uses the shared `Context.isOnline()`; `VideoChoice` is `@Serializable`.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease`: BUILD SUCCESSFUL, 112 JVM
  tests pass (28 new: `DownloadProgress`, `throttleLatest` in virtual time, `EngineUpdateRule`
  with explicit clock values, `DownloadData` including the 10 KB fallback, `ChoicesFile`), lint
  clean.
- **On the owner's phone** (Samsung SM-S948B, Android 17 / API 37, wireless debugging), debug build,
  through `DebugDownloadActivity`, with a public YouTube video (a 10-minute Blender film, 288 MB)
  and a public X post with two videos:

| Check | Result |
|---|---|
| Fresh install, app start | The update ran first (`DONE`, yt-dlp 2026.10.26); the download queued meanwhile waited for it, then ran |
| Leave the app, watch the notification | Ongoing notification with the title, "Downloading", a determinate bar; Cancel shown when expanded |
| Finish | "Saved to Downloads" with the file name; ongoing notification gone |
| Tap the finished notification | `ACTION_VIEW` on the content uri (`video/mp4`, read grant); played in the phone's Video Player; notification auto-cancelled |
| Cancel from the notification | Work `CANCELLED` while saving, no result notification, no job folder, no pending or extra file in `Download/` |
| Start another, let it finish | Saved (that run also hit HTTP 403 once and re-extracted, the first time the P03 retry was seen working) |
| Update while a download runs | `updateEngineNow()` 3 s into a download: the update started only after the download succeeded (`ALREADY_UP_TO_DATE`) |
| Network lost mid-download | The app's network was cut with `cmd connectivity set-package-networking-enabled false` 3 s into the fetch: failed as *no internet* after yt-dlp's retries (~20 s), notification "Couldn't download / No internet right now…", no leftovers; tapping it opened the app |
| Post with two videos, app on screen | `ChooseVideos`, choices file with 2 entries, no notification |
| Same, app left at once | "Choose which videos to save" with the post's title; tap opened `MainActivity` with the work-ID extra; the older choices file was deleted |
| Both videos chosen (`select=all`) | Notification "1 of 2", then "2 of 2", then "2 videos saved"; the "Choose" notification was removed at start; both files in `Download/` |

  Every test video was deleted from the phone afterwards; the per-app network block was turned off.
- **Not checked:** real aeroplane mode (it drops wireless debugging; the owner accepted the
  per-app network cut instead); the fallback when Android refuses the foreground service; a
  refused `POST_NOTIFICATIONS`; an offline app start (update skipped); the 10 KB saved-list
  fallback on a device (unit-tested only); Android versions below 17.

## Docs updated

- `docs/architecture.md`: § *Code layout* (new pure parts, the debug activity, the root package),
  § *Background work* (rewritten: chain, `DownloadQueue`, `DownloadData`, worker, notification
  table, `UpdateWorker`), § *Permissions*, § *Dependencies*, § *Tests*.
- `docs/product.md` B8: the "Choose which videos" notification.
- `docs/development.md`: new § *Background downloads by hand* (debug trigger, per-app network cut,
  Git Bash path note).
- `docs/work/decisions/DEC-006` and its row in `docs/work/README.md`.
- `CHANGELOG.md` *Unreleased*: background downloads with notifications; daily yt-dlp update.

## Bugs filed

None.

## Open questions

- **Result notification while the app is open.** The "Saved" / "Couldn't download" notification is
  posted whether or not the app is on screen, as the prompt asks. With P05's status card this may
  feel doubled; P05 could cancel ID 2 when the screen shows the result, or the worker could skip it
  when the app is visible (as it does for "Choose").
- **Old choices file.** A choices file stays in `cache/choices/` until the next post with several
  videos replaces it (a few hundred bytes). P05 could delete it once the choice is made.

## Next

P05 builds the screen on `DownloadQueue` and `DownloadData`:

- `startDownload` returns the work ID; map `observeDownloads()` to `UiState` only for IDs this
  ViewModel started (B9). `DownloadData.progressOf(info.progress)` is null until the first update;
  `resultOf(info.outputData)` gives `Saved`, `Choose` or `Failed`. `Choose.choicesFile` is read with
  `ChoicesFile.read` (null if the cache was cleared).
- The "Choose" notification opens the launcher activity with
  `Notifications.EXTRA_CHOOSE_WORK_ID` and `FLAG_ACTIVITY_CLEAR_TOP | SINGLE_TOP`, so a running
  `MainActivity` gets it in `onNewIntent`. That is the one case where a finished job not seen by
  this ViewModel should be shown (after a process death).
- Ask for `POST_NOTIFICATIONS` the first time a download starts (API 33+).
- Use `FailureReason.messageRes` for the failure text.
- A cancel or a failure ends the `engine` chain, and the next enqueue deletes the old chain's
  finished work from WorkManager.
