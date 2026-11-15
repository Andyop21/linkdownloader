---
id: BUG-002
title: After a post's choice sheet, every new link downloads that post instead
status: fixed
found: 2026-11-16
fixed-by:
---

# BUG-002 — After a post's choice sheet, every new link downloads that post instead

## What happens

Once a link to a post with several videos has shown the choice sheet, every link pasted or typed
after it, from any site, opens the choice sheet for that same post again. The app is stuck on that
post until WorkManager prunes the old work.

## What should happen

Each link downloads its own video, whatever the job before it ended as.

## How to reproduce

1. Paste a link to an X post with two videos; the choice sheet opens. Close it.
2. Paste a link to a different X post with one video, or to an Instagram post.
3. The choice sheet for the first post opens again. Logcat (`VideoDownloader`) shows the job
   extracted with the `twitter` extractor and 2 entries, even for the Instagram link.

## Notes

Seen on a Samsung phone (Android 16) with the release build of 1.0.0 and yt-dlp 2026.10.26. The
same link run through yt-dlp on a PC gave the right single video, which pointed away from yt-dlp.

Downloads are appended to the unique `engine` chain (DEC-006). For a job with a prerequisite,
WorkManager builds the input by merging the job's own input with the prerequisite's output, using
`OverwritingInputMerger`, where the later value wins: the prerequisite's output. The input kept the
link under `link`, and the *choose* output (`DownloadData.choose`) also stores the post's link
under `link`. So the post's link replaced the new one, and that job ended as *choose* again, which
carried the same link to the next job. A *saved* or *failed* output has no `link`, which is why
the chain only got stuck after a choice.

## Fix

The input keys are now `input_link` and `input_selection`, which no download or update output
uses. `DownloadDataTest` › *input survives being merged with the previous job's output* merges the
input with every kind of output through `OverwritingInputMerger`, as WorkManager does; it failed
before the fix. Tried on the phone: the X link that had shown another post's two videos saved its
own video, and the Instagram link reached the Instagram extractor.
