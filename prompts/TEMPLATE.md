# P<NN> — <title>

<!-- Copy this file to prompts/not-finished/P<NN>-<slug>.md, fill it in, and delete these comments.
     Add its line to NEXT-STEPS.md and its row to the index in README.md. -->

**Goal.** One short paragraph: what exists after this session that did not before, and why.

**Entry condition.** Prompts that must be finished first, and anything that must already exist.

**Read first.** The documents and code this session needs, and nothing else.

## Scope

What this session builds or changes, concretely.

## Out of scope

What it must not touch, so it stays small.

## Deliverables

1. Each deliverable on its own line, small enough to build and commit on its own.

## Definition of done

- [ ] Every deliverable above exists and works.
- [ ] The checks in `docs/definition-of-done.md` pass.
- [ ] Prompt-specific checks (tests to add, behaviour to see on a device or emulator).
- [ ] Docs updated: list which.

## Handoff

Write `docs/work/handoffs/P<NN>-<slug>.md` from `docs/work/templates/handoff.md`, then run
`bash scripts/finish-prompt.sh P<NN>`.
