# Next steps

The execution queue: one line per prompt still to run, in order. The top line runs next.
`bash scripts/finish-prompt.sh <ID>` deletes a prompt's line when it finishes.

Line format:

```text
- [P01](not-finished/P01-scaffold-app.md): one-line summary. Entry: what must be finished first.
```

## Queue

### Stage 1 — Foundation

Finished: P01, P02.

### Stage 2 — The engine

- [P03](not-finished/P03-download-pipeline.md): extract, pick, fetch, join and save, with progress, cancel and failure reasons. Entry: P02.
- [P04](not-finished/P04-background-downloads.md): WorkManager worker, notifications with Cancel, daily yt-dlp update. Entry: P03.

### Stage 3 — The screen

- [P05](not-finished/P05-main-screen.md): the one screen wired end to end: paste, progress, open, failures, About sheet. Entry: P04.
- [P06](not-finished/P06-look-and-feel.md): pastel theme, Nunito, the mascot, motion and the app icon. Entry: P05.

### Stage 4 — Ship

- [P07](not-finished/P07-release.md): signed release APK, full test pass on the phone, public-ready docs. Entry: P06. Needs the owner's keystore and license choice.
