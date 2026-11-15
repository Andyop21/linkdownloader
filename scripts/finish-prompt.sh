#!/usr/bin/env bash
# Closes a finished prompt session in one step.
#
#   bash scripts/finish-prompt.sh <ID>      # e.g. P01
#
# Requires the session's handoff at docs/work/handoffs/<ID>-*.md. Then it:
#   1. git mv prompts/not-finished/<ID>-*.md into prompts/finished/;
#   2. deletes the prompt's line from prompts/NEXT-STEPS.md;
#   3. rewrites links to the prompt in every tracked Markdown file;
#   4. sets the prompt's status to Finished in the prompts/README.md index.
# It commits nothing: review the diff and commit it together with the handoff.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

die() { echo "finish-prompt: $*" >&2; exit 1; }

id=${1:?usage: finish-prompt.sh <ID>, e.g. P01}
shopt -s nullglob

prompts=(prompts/not-finished/"$id"-*.md)
[ "${#prompts[@]}" -eq 1 ] || die "expected one prompts/not-finished/$id-*.md, found ${#prompts[@]}"
handoffs=(docs/work/handoffs/"$id"-*.md)
[ "${#handoffs[@]}" -ge 1 ] || die "no handoff at docs/work/handoffs/$id-*.md; write it first"

name=$(basename "${prompts[0]}")
pattern=$(printf '%s' "$name" | sed 's/[.]/\\./g')

git mv "prompts/not-finished/$name" "prompts/finished/$name"

sed -i "/not-finished\/$pattern/d" prompts/NEXT-STEPS.md

git grep -l --fixed-strings "not-finished/$name" -- '*.md' | while read -r file; do
  sed -i "s/not-finished\/$pattern/finished\/$pattern/g" "$file"
done

if grep -q "finished/$pattern" prompts/README.md; then
  sed -i "/(finished\/$pattern)/ s/^| \([^|]*\) | [^|]* |/| \1 | Finished |/" prompts/README.md
else
  echo "finish-prompt: no row for $name in the prompts/README.md index; add it by hand" >&2
fi

echo "finish-prompt: $id moved to prompts/finished/$name; review the diff and commit it with the handoff"
