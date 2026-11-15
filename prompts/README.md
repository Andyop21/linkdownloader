# Prompts

Work on this app is done in **sessions**, and each session is driven by one prompt file. A prompt
is a self-contained brief for a coding agent: its goal, entry condition, scope, deliverables and
definition of done. Prompts run in the order of [`NEXT-STEPS.md`](NEXT-STEPS.md).

## Files

| File | Purpose |
|---|---|
| [`session-protocol.md`](session-protocol.md) | Rules every session follows. Read before the prompt itself. |
| [`NEXT-STEPS.md`](NEXT-STEPS.md) | The ordered queue: one line per prompt still to run. The top line runs next. |
| [`TEMPLATE.md`](TEMPLATE.md) | The shape of a new prompt. |
| [`not-finished/`](not-finished/) | Prompts not yet run, or run but not yet meeting their definition of done. |
| [`finished/`](finished/) | Prompts whose definition of done is met and whose handoff is recorded. |

## Naming

`P<NN>-<short-slug>.md`, numbered in the order they are written: `P01-scaffold-app.md`,
`P02-paste-and-validate.md`. The ID is `P<NN>`. Numbers are never reused.

## Lifecycle

1. **Write** a new prompt from [`TEMPLATE.md`](TEMPLATE.md) directly in `not-finished/`, add its line
   to [`NEXT-STEPS.md`](NEXT-STEPS.md) at the place it should run, and add its row to the index below.
2. **Run** it in a fresh agent session on `worktree`:

   ```text
   Run prompt P01. Read prompts/session-protocol.md, then prompts/not-finished/P01-scaffold-app.md,
   and follow both.
   ```

3. **Finish** it when its definition of done is met: write the handoff in
   `docs/work/handoffs/<ID>-<slug>.md`, run `bash scripts/finish-prompt.sh <ID>` (moves the file to
   `finished/`, removes its queue line, rewrites links, flips the index row), then commit and publish
   everything together.
4. A session too large for one sitting may stop early: it writes a handoff that says what is left and
   leaves the prompt in `not-finished/`. The next session picks it up from that handoff.

## Rules

- A prompt only ever moves `not-finished/` → `finished/` (`git mv`, done by the script). Never back.
- **Finished prompts are immutable.** If the work later turns out wrong, file a bug or write a new
  prompt; do not edit the old one.
- A prompt that is not yet run may be edited freely.
- One prompt, one session, one handoff.

## Index

| ID | Status | Prompt | Summary |
|---|---|---|---|
