#!/usr/bin/env bash
# No AI attribution in a commit message (AGENTS.md / CLAUDE.md). This script holds that rule.
#
#   bash scripts/attribution.sh strip <message-file>   # remove attribution lines in place (commit-msg hook)
#   bash scripts/attribution.sh check <rev-list args>  # list commits that carry attribution; exit 1 if any
#   bash scripts/attribution.sh install                # point git at .githooks so the commit-msg hook runs
#
# Only trailer-shaped lines count: a commit that mentions an AI tool in its subject or body passes.
set -euo pipefail

AI_NAMES='claude|anthropic|openai|chatgpt|gpt-?[0-9]|codex|copilot|cursor|gemini|grok'
ATTRIBUTION_RE="^[[:space:]]*((co-authored-by|co-committed-by):.*($AI_NAMES)|claude-session:|(🤖[[:space:]]*)?generated (with|by) .*($AI_NAMES))"

die() { echo "attribution: $*" >&2; exit 2; }

has_attribution() { grep -Eiq "$ATTRIBUTION_RE"; }

# Drop the attribution lines and the blank lines they leave at the end of the message.
strip_stream() {
  { grep -Eiv "$ATTRIBUTION_RE" || true; } |
    awk 'NF { while (blank > 0) { print ""; blank-- } print; next } { blank++ }'
}

cmd_strip() {
  local file=${1:?usage: attribution.sh strip <message-file>} tmp
  [ -f "$file" ] || die "no such message file: $file"
  has_attribution < "$file" || return 0
  tmp=$(mktemp)
  strip_stream < "$file" > "$tmp"
  cat "$tmp" > "$file"
  rm -f "$tmp"
  echo "attribution: removed AI attribution lines from the commit message" >&2
}

cmd_check() {
  [ "$#" -gt 0 ] || die 'usage: attribution.sh check <rev-list args>, e.g. origin/worktree..HEAD'
  local sha found=0
  while read -r sha; do
    [ -n "$sha" ] || continue
    if git log -1 --format=%B "$sha" | has_attribution; then
      [ "$found" -eq 1 ] || echo 'attribution: these commits carry AI attribution, which AGENTS.md forbids:' >&2
      found=1
      echo "  $(git log -1 --format='%h %s' "$sha")" >&2
      git log -1 --format=%B "$sha" | grep -Ei "$ATTRIBUTION_RE" | sed 's/^/      /' >&2
    fi
  done < <(git rev-list "$@")
  [ "$found" -eq 0 ]
}

cmd_install() {
  local current
  current=$(git config --get core.hooksPath || true)
  [ "$current" = .githooks ] && return 0
  [ -z "$current" ] || echo "attribution: core.hooksPath was '$current'; now .githooks" >&2
  git config core.hooksPath .githooks
}

case ${1:-} in
  strip) shift; cmd_strip "$@" ;;
  check) shift; cmd_check "$@" ;;
  install) cmd_install ;;
  *) die 'usage: attribution.sh strip <file> | check <rev-list args> | install' ;;
esac
