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

Finished: P03, P04.

### Stage 3 — The screen

Finished: P05, P06.

### Stage 4 — Ship

- [P07](not-finished/P07-release.md): signed release APK, full test pass on the phone, public-ready docs. Entry: P06. Needs the owner's keystore and license choice.
