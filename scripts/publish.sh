#!/usr/bin/env bash
# Publishes the local `worktree` branch to origin. Run it after every commit; it is the only push.
#
#   bash scripts/publish.sh
#
# Refuses when not on `worktree`, when the tree is dirty, or when a commit carries AI attribution.
# If origin/worktree moved on, the unpublished local commits are rebased onto it first.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

die() { echo "publish: $*" >&2; exit 1; }

bash scripts/attribution.sh install

branch=$(git branch --show-current)
[ "$branch" = worktree ] || die "on '$branch'; publish runs only on worktree"
[ -z "$(git status --porcelain)" ] || die 'uncommitted changes; commit or stash them first'

git fetch --quiet origin

if git rev-parse --verify --quiet origin/worktree > /dev/null; then
  if ! git merge-base --is-ancestor origin/worktree HEAD; then
    echo 'publish: origin/worktree has commits this checkout lacks; rebasing the local commits onto it'
    git rebase origin/worktree ||
      die 'the rebase stopped on a conflict: resolve it, run git rebase --continue, then publish again'
  fi
  range=origin/worktree..HEAD
else
  range=HEAD
fi

bash scripts/attribution.sh check "$range" ||
  die 'reword those commits without the attribution lines (git commit --amend for the last one), then publish again'

count=$(git rev-list --count "$range")
if [ "$count" -eq 0 ]; then
  echo 'publish: nothing to publish; worktree matches origin/worktree'
  exit 0
fi

git push origin worktree:worktree
echo "publish: pushed $count commit(s) to origin/worktree"
