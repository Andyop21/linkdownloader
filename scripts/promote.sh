#!/usr/bin/env bash
# Moves `master` forward to the published `worktree`. Run it only when the human asks.
#
#   bash scripts/promote.sh
#
# Fast-forward only: it refuses when master holds a commit worktree lacks, and refuses commits
# that carry AI attribution. It promotes origin/worktree, so publish local work first.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

die() { echo "promote: $*" >&2; exit 1; }

git fetch --quiet origin
git rev-parse --verify --quiet origin/worktree > /dev/null || die 'origin/worktree does not exist'
git rev-parse --verify --quiet origin/master > /dev/null || die 'origin/master does not exist'

if [ "$(git branch --show-current)" = worktree ] && [ -n "$(git rev-list origin/worktree..HEAD)" ]; then
  die 'this checkout has unpublished commits on worktree; run bash scripts/publish.sh first'
fi

git merge-base --is-ancestor origin/master origin/worktree ||
  die 'master holds commits worktree lacks; it cannot be fast-forwarded. Ask the human.'

range=origin/master..origin/worktree
count=$(git rev-list --count "$range")
if [ "$count" -eq 0 ]; then
  echo 'promote: master already matches worktree'
  exit 0
fi

bash scripts/attribution.sh check "$range" || die 'worktree carries AI attribution; not promoted'

git push origin origin/worktree:refs/heads/master
git fetch --quiet origin
echo "promote: master fast-forwarded by $count commit(s) to $(git rev-parse --short origin/master)"
