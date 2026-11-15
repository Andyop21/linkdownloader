---
id: BUG-001
title: finish-prompt.sh rewrites the example lines that name the finished prompt
status: fixed
found: 2026-11-15
fixed-by:
---

# BUG-001 — finish-prompt.sh rewrites the example lines that name the finished prompt

## What happens

`bash scripts/finish-prompt.sh P01` changes more than the prompt's own queue line and index row:

- In `prompts/NEXT-STEPS.md` the `sed "/not-finished\/<name>/d"` also deletes the *Line format*
  example, which uses `P01-scaffold-app.md`, leaving an empty code block.
- In `prompts/README.md` the link rewrite also changes the *Run* example
  (`prompts/not-finished/P01-scaffold-app.md` becomes `prompts/finished/...`), although that line is
  an instruction template, not a link to the prompt.
- It rewrites links only in tracked files, so a handoff written in the same session (still
  untracked) keeps its `not-finished/` link.

## What should happen

Only the prompt's queue entry (`- [<ID>](...)` lines under *Queue*) is deleted, only real Markdown
links `](.../not-finished/<name>)` are rewritten, and the new handoff's link is rewritten too.

## How to reproduce

1. On a checkout where P01 is not finished, write `docs/work/handoffs/P01-scaffold-app.md` with a
   link to `prompts/not-finished/P01-scaffold-app.md`.
2. Run `bash scripts/finish-prompt.sh P01`.
3. `git diff`: the *Line format* example in `prompts/NEXT-STEPS.md` is gone, the *Run* example in
   `prompts/README.md` now says `finished/`, and the handoff still says `not-finished/`.

## Notes

Seen while finishing P01; the three lines were restored by hand in the P01 handoff commit. Only P01
is named in the examples, so later prompts are affected only by the untracked-handoff case.

## Fix

`scripts/finish-prompt.sh` now:

- deletes only a line starting `- [<ID>](not-finished/<name>)` under the `## Queue` heading of
  `prompts/NEXT-STEPS.md`, outside fenced code blocks;
- rewrites only Markdown link targets `](<prefix>/not-finished/<name>)` (optionally with an
  `#anchor`), outside fenced code blocks, so plain mentions and examples keep `not-finished/`;
- looks for links in tracked and untracked (not ignored) Markdown files, which covers the handoff
  written in the same session;
- warns when it finds no queue entry, and matches the index row by `(finished/<name>)` only.

`bash scripts/tests/finish-prompt-test.sh` builds a throwaway repository with all three cases and
fails on the old script (4 failures); it passes on the fixed one.
