# Git workflow

## Branches

```text
  worktree ──(promote.sh, when the owner asks)──► master
```

| Branch | Role | Who moves it |
|---|---|---|
| `worktree` | All work, by humans and agents. | `bash scripts/publish.sh`, after every commit |
| `master` | Stable: the version worth installing. GitHub's default branch. | `bash scripts/promote.sh`, only when the owner asks |

There are no other long-lived branches. A short experiment may use a local topic branch, but it
lands on `worktree` (merged or rebased locally) before it is published; topic branches are not
pushed.

## Rules

- **Never commit on `master`.** It only ever fast-forwards to `worktree`.
- **Never force-push** `worktree` or `master`, and never rewrite a commit that is already
  published. Fix a published mistake with a new commit.
- **Publish after every commit** with `bash scripts/publish.sh`. A plain `git push` skips its checks.
- **Promote only on request.** `bash scripts/promote.sh` is the owner's call, never an agent's own.

## Commits

Format: `type(scope): short imperative summary`, at most about 72 characters.

| Type | For |
|---|---|
| `feat` | New behaviour in the app |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `refactor` | Code change with no behaviour change |
| `test` | Tests only |
| `build` | Gradle, dependencies, signing setup |
| `chore` | Repository upkeep: scripts, config, prompts |

Scope is the area touched: `ui`, `link`, `download`, `app`, `prompts`, `workflow`. Examples:

```text
feat(ui): add the paste button and status line
fix(link): accept links wrapped in share text
docs(prompts): add P03 download worker prompt
```

A body is welcome when the *why* is not obvious.

## No AI attribution

Commit messages never carry AI attribution: no `Co-Authored-By:` line naming an AI tool or vendor,
no `Claude-Session:` line, no "Generated with/by …" line. Three things hold this:

1. `.claude/settings.json` turns Claude Code's attribution off.
2. `.githooks/commit-msg` strips such lines as the commit is made (enabled by
   `bash scripts/attribution.sh install`, which `publish.sh` runs).
3. `publish.sh` and `promote.sh` refuse to push a commit that still carries one.

If `publish.sh` refuses, reword the commit (`git commit --amend` for the last one) and publish again.
That rewrite is allowed because the commit is not yet published.

## When publish meets new commits

If someone published to `worktree` first, `publish.sh` rebases your unpublished commits onto
`origin/worktree` and pushes. On a conflict it stops: resolve the files, `git rebase --continue`, and
run `publish.sh` again.
