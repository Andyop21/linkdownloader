# Session protocol

Applies to every prompt session. Read it before the prompt itself.

1. **Rules first.** Read root `AGENTS.md` / `CLAUDE.md` and [`docs/index.md`](../docs/index.md),
   then only the documents the prompt names under *Read first*.
2. **Branch.** Work only on `worktree`. On `master` → **stop** and ask the human to switch. Run
   `git pull --ff-only` before the first edit.
3. **Entry condition.** Confirm what the prompt's entry condition names (earlier prompts finished,
   files present) actually exists in the checkout. If it does not, stop and report.
4. **Small steps.** Work in increments that each build. After each one, run the checks from
   [`docs/definition-of-done.md`](../docs/definition-of-done.md) that apply, and fix failures before
   moving on.
5. **Commit and publish.** Commit with `type(scope): short imperative summary` and no AI
   attribution, then `bash scripts/publish.sh` after **every** commit.
6. **Stay in scope.** Build what the prompt asks, nothing more. An idea outside it goes in the
   handoff under *Open questions*. A defect outside it is filed in `docs/work/bugs/` from the
   template and left alone unless it blocks the session.
7. **Docs as you go.** A change to behaviour updates `docs/product.md`; a change to structure
   updates `docs/architecture.md`; a user-visible change adds a line under *Unreleased* in
   `CHANGELOG.md`. A choice worth remembering becomes a decision record in `docs/work/decisions/`.
8. **Public repository.** No secrets, keystores, personal data, cookies or private links, anywhere.
9. **Finish.** Write the handoff from
   [`docs/work/templates/handoff.md`](../docs/work/templates/handoff.md) to
   `docs/work/handoffs/<ID>-<slug>.md`, run `bash scripts/finish-prompt.sh <ID>`, commit the
   handoff and the move together, and publish. Never run `scripts/promote.sh` unless the human asks.
10. **Conflicts.** If the prompt contradicts `AGENTS.md`, a decision record or the existing code in a
    way you cannot resolve, stop and ask the human instead of guessing.
