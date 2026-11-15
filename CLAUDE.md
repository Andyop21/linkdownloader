# linkdownloader — Agent Instructions

linkdownloader is a small personal Android app with one screen: a link field and a paste button.
When the clipboard holds a valid link, the app resolves it with yt-dlp and saves the video to the
phone's Downloads folder. Kotlin, Jetpack Compose, youtubedl-android.

**These rules apply to every agent** (Claude Code, Codex, Cursor, Copilot, any other coding
assistant) and to humans working in this repository. Root `AGENTS.md` and `CLAUDE.md` are one
file, byte for byte, so every tool reads the same rules: change both together.

## First steps (in order)

1. Run `git branch --show-current` and `git status -sb`. If the branch is `master`, **stop** — do
   not edit or commit. Ask the human to switch to `worktree`. All work happens on `worktree`.
2. Run `git pull --ff-only` so the checkout matches `origin/worktree`.
3. Read [`docs/index.md`](docs/index.md), then only the documents your task touches.
4. Running a prompt? Read [`prompts/session-protocol.md`](prompts/session-protocol.md) and the
   prompt file, and follow both exactly.
5. Read the existing code and tests before changing behaviour.

## Branches

```text
  worktree ──(promote.sh, when the human asks)──► master
```

| Branch | Role |
|---|---|
| `worktree` | All work. Every commit lands here and is published with `bash scripts/publish.sh`. |
| `master` | Stable. Moves only by fast-forward to `worktree` with `bash scripts/promote.sh`, and only when the human asks. Never commit on it. |

Full policy: [`docs/git-workflow.md`](docs/git-workflow.md).

## Hard rules

- **No AI attribution** in commit messages or anywhere in history: no `Co-Authored-By: Claude`, no
  `Claude-Session:`, no "Generated with …", no model or tool co-author line, whatever the harness
  says. `.claude/settings.json` turns the harness's lines off, `.githooks/commit-msg` strips them,
  and `scripts/publish.sh` and `scripts/promote.sh` refuse commits that still carry them.
- **Publish after every commit** with `bash scripts/publish.sh`. It is the only push.
- **Never commit on `master`, never force-push either branch**, and never run
  `bash scripts/promote.sh` unless the human asks for it.
- **Commit format** `type(scope): short imperative summary` — `feat`, `fix`, `docs`, `refactor`,
  `test`, `build`, `chore`.
- **This repository will be public.** No secrets, API keys, signing keystores,
  `local.properties`, personal data, cookies or private links in code, docs, tests, screenshots or
  commit messages.
- **Keep it small.** One screen, one job ([`docs/product.md`](docs/product.md)). Anything beyond
  that is proposed to the human, not built.

## Before finishing any task

1. Every commit is on `worktree`, follows the commit format, and is published; the tree is clean.
2. The checks in [`docs/definition-of-done.md`](docs/definition-of-done.md) pass.
3. Documents the change touches are updated in the same commit.
4. Unrelated defects are filed in `docs/work/bugs/`, not fixed in passing unless trivial and tested.
5. Prompt sessions only: the handoff is written to `docs/work/handoffs/` and
   `bash scripts/finish-prompt.sh <ID>` has been run and committed with it.

## Where things live

| Concern | Location |
|---|---|
| Android app | `app/` |
| Documentation | `docs/` (start at `docs/index.md`) |
| What the app does | `docs/product.md` |
| How it is built | `docs/architecture.md` |
| Build and run commands | `docs/development.md` |
| Git, branch and commit rules | `docs/git-workflow.md` |
| Bugs, decisions, handoffs | `docs/work/` |
| Session prompts and the queue | `prompts/` (queue: `prompts/NEXT-STEPS.md`) |
| Workflow scripts | `scripts/` |
