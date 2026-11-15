---
id: DEC-005
title: "Posts with several videos: offer a choice, then download the chosen ones; Reddit dropped"
date: 2026-11-15
status: accepted
---

# DEC-005 — Posts with several videos: offer a choice, then download the chosen ones; Reddit dropped

## Context

P03's pipeline took one video per link. A post with several videos (an X post with several clips,
an Instagram carousel) comes back from yt-dlp as a playlist, which the pipeline reported as *not
supported*. After P03 the owner decided:

- such a post shows a thumbnail of each video, and the user chooses one, some or all to download;
- Reddit is no longer a must-work site.

Whole playlists and profiles stay out of scope (`docs/product.md`).

## Decision

- **Product.** Behaviour B11 in `docs/product.md`: after *preparing*, a sheet of thumbnails with
  each video's length, all selected; tap to toggle, **Select all**, **Download (n)**; dismissing it
  downloads nothing. Each chosen video is saved as its own file, one after another. A post with one
  video skips the sheet. Reddit is removed from the must-work list and the README.
- **Telling a post from a playlist.** Extract with `--flat-playlist`. A post's entries come back
  as full videos with formats; a real playlist's or profile's entries are only links, so it has no
  video entries and fails as *not supported*, without extracting every video in it.
- **Two jobs, no saved state.** `download(link, jobId, selection = null)` returns
  `ChooseVideos(title, choices)` for a post with several videos; each choice has its position,
  title, thumbnail URL and length. The screen enqueues a second download with the chosen
  positions, which extracts the post again and downloads those entries. Each entry is written to
  its own JSON file for `--load-info-json`. Choices are identified by their position among the
  post's videos.
- **Partial success.** One chosen video's failure does not stop the rest. `Saved(videos, failed)`
  reports what was saved and how many failed. When every chosen video fails, the job is `Failed`.
  Cancelling keeps the videos already saved and deletes only the one in progress.
- **Worker and screen** (P04, P05): the worker passes the selection through and returns the
  choices via a JSON file in the cache, because WorkManager's output `Data` is capped at 10 KB. The
  screen loads thumbnails with `HttpURLConnection` + `BitmapFactory`; an image library would be a
  new dependency.

## Consequences

- A post with several videos costs two extractions. On X and Instagram an extraction takes 2–4 s,
  so the second one is cheap, and the format URLs are fresh when the download starts, however long
  the user took to choose.
- `DownloadOutcome.Saved` holds a list. P04's notification and P05's *Done* state handle one or
  several videos.
- Verified on the owner's phone with an X post with two videos: two choices with thumbnails, then
  both saved as separate MP4 files. An Instagram carousel has not been tried yet.
- Positions are stable for a post. If the post changes between the two jobs, a position that no
  longer exists fails that one video.

## Alternatives considered

- **Download every video of the post without asking**: the owner wants to choose.
- **Keep the first job's JSON for the second**: needs state that outlives a job, and the format URLs
  can expire while the user decides.
- **Choosing inside one long-running job**: a worker would have to wait on the user with a
  foreground notification up, and the choice is a screen concern.
- **No `--flat-playlist`**: a playlist link would extract every video in it before failing.
