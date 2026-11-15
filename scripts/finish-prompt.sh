#!/usr/bin/env bash
# Closes a finished prompt session in one step.
#
#   bash scripts/finish-prompt.sh <ID>      # e.g. P01
#
# Requires the session's handoff at docs/work/handoffs/<ID>-*.md. Then it:
#   1. git mv prompts/not-finished/<ID>-*.md into prompts/finished/;
#   2. deletes the prompt's entry, `- [<ID>](not-finished/<name>)`, under ## Queue in
#      prompts/NEXT-STEPS.md;
#   3. rewrites Markdown links `](.../not-finished/<name>)` to `finished/` in every tracked or
#      untracked (not ignored) Markdown file, so a handoff not yet committed is covered too;
#   4. sets the prompt's status to Finished in the prompts/README.md index.
# Lines inside fenced code blocks and plain mentions of the path are left alone: they are examples
# and instruction templates, not links. It commits nothing: review the diff and commit it together
# with the handoff.
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

# Replaces $1 with the output of the awk program in the remaining arguments, keeping its mode.
rewrite() {
  local file=$1 tmp
  shift
  tmp=$(mktemp)
  awk "$@" "$file" > "$tmp"
  cat "$tmp" > "$file"
  rm -f "$tmp"
}

git mv "prompts/not-finished/$name" "prompts/finished/$name"

# Step 2: delete only the queue entry, outside code fences and under the ## Queue heading.
entry="- [$id](not-finished/$name)"
before=$(wc -l < prompts/NEXT-STEPS.md)
rewrite prompts/NEXT-STEPS.md -v entry="$entry" '
  /^[ \t]*(```|~~~)/ { fence = !fence; print; next }
  !fence && /^## / { queue = ($0 ~ /^## Queue[ \t\r]*$/) }
  !fence && queue && index($0, entry) == 1 { next }
  { print }
'
[ "$(wc -l < prompts/NEXT-STEPS.md)" -lt "$before" ] ||
  echo "finish-prompt: no queue entry for $id under ## Queue in prompts/NEXT-STEPS.md; check it by hand" >&2

# Step 3: rewrite real Markdown links only, outside code fences. A link target may carry a
# relative prefix ending in / and an #anchor.
name_re=$(printf '%s' "$name" | sed 's/[.]/[.]/g')
tail_len=$(( ${#name} + 14 ))   # "not-finished/" + name + the closing ) or #
git ls-files -z --cached --others --exclude-standard -- '*.md' |
  xargs -0 grep -lF -- "not-finished/$name" 2> /dev/null |
  while read -r file; do
    rewrite "$file" -v name="$name" -v name_re="$name_re" -v tail_len="$tail_len" '
      BEGIN { link = "\\]\\(([^) \t]*/)?not-finished/" name_re "[)#]" }
      /^[ \t]*(```|~~~)/ { fence = !fence; print; next }
      fence { print; next }
      {
        rest = $0; out = ""
        while (match(rest, link)) {
          seg = substr(rest, RSTART, RLENGTH)
          seg = substr(seg, 1, RLENGTH - tail_len) "finished/" name substr(seg, RLENGTH, 1)
          out = out substr(rest, 1, RSTART - 1) seg
          rest = substr(rest, RSTART + RLENGTH)
        }
        print out rest
      }
    '
  done

# Step 4: flip the index row whose link now points into finished/.
if grep -q "(finished/$pattern)" prompts/README.md; then
  sed -i "/(finished\/$pattern)/ s/^| \([^|]*\) | [^|]* |/| \1 | Finished |/" prompts/README.md
else
  echo "finish-prompt: no row for $name in the prompts/README.md index; add it by hand" >&2
fi

echo "finish-prompt: $id moved to prompts/finished/$name; review the diff and commit it with the handoff"
