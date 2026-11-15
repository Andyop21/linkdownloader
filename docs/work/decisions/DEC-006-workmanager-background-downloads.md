---
id: DEC-006
title: WorkManager for background downloads; the yt-dlp update never blocks or fails a download
date: 2026-11-15
status: accepted
---

# DEC-006 — WorkManager for background downloads; the yt-dlp update never blocks or fails a download

## Context

P04 runs `VideoDownloader` in the background, with a progress notification that can cancel it,
and keeps yt-dlp current. `docs/architecture.md` already planned WorkManager, with downloads and
updates on one unique chain, `engine`, appended with `APPEND_OR_REPLACE`, so an update never
replaces the yt-dlp file under a running download. Two consequences of that chain needed deciding:
a job in a WorkManager chain that fails makes the jobs queued after it fail too, and a job blocked
on a constraint holds back everything after it.

## Decision

- **androidx.work `work-runtime` 2.12.0** (the latest release on Google's Maven on 2026-11-15).
  Its Kotlin extensions (`CoroutineWorker`, `getWorkInfosByTagFlow`) are in `work-runtime` itself,
  so `work-runtime-ktx` is not needed. Default initialisation; no custom `Configuration`.
- **kotlinx-coroutines-test** (already in the catalog, at the `coroutines` version) as a test
  dependency, to test the progress throttle in virtual time.
- **`UpdateWorker` always succeeds and has no network constraint.** Offline, it skips the check and
  records nothing, so the next app start tries again; a failed check is logged only. With a
  constraint, a download queued behind a pending update would wait for the network instead of
  failing at once as *no internet*; with a failure result, it would fail with no reason at all.
- **The once-a-day rule is checked twice**: by `DownloadQueue.updateEngineIfDue()` before it
  enqueues, and again by `UpdateWorker` when it runs (unless the About sheet forced it), so several
  app starts in a row queue at most one real check.
- **Progress is throttled** with a conflating `throttleLatest(250 ms)` on a `StateFlow` of the
  job's state: at most four updates a second, and the latest state is never lost.
- **Notification IDs** are fixed, since only one download runs at a time: 1 for the ongoing
  (foreground) notification, 2 for the result, 3 for "Choose which videos". The result never uses
  the foreground ID, which WorkManager removes when the work ends.
- **"Is the app on screen"** for the *choose* notification is read with
  `ActivityManager.getMyMemoryState`: importance `FOREGROUND` means an activity is visible, while a
  running foreground service alone gives `FOREGROUND_SERVICE`. No lifecycle-process dependency.

## Consequences

- A download that fails ends its chain; the next `APPEND_OR_REPLACE` starts a new chain and
  WorkManager deletes the old one's finished work, so `observeDownloads()` only lists the current
  chain. The screen only cares about the job it started.
- Cancelling a download also cancels an update queued after it (WorkManager cancels dependent
  work). The update runs at the next app start instead.
- An update queued behind a download that fails is dropped the same way, for the same reason.
- If Android refuses the foreground service (a download that starts while the app is in the
  background), the worker goes on as ordinary work with the notification posted directly.

## Alternatives considered

- **Network constraint on `UpdateWorker`**: holds queued downloads back while offline (above).
- **A separate chain for updates**: an update could replace yt-dlp while a download runs it.
- **`work-runtime-ktx`**: not needed; the app compiles against `work-runtime` alone, which holds
  `CoroutineWorker` and the `Flow` APIs.
- **`ProcessLifecycleOwner`** for "app on screen": a new dependency for one check.
- **Sampling progress with a pure throttle class and a clock**: can drop the last update before a
  quiet period; the conflating flow cannot.
