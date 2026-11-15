#!/usr/bin/env bash
# Regression test for scripts/finish-prompt.sh (BUG-001).
#
#   bash scripts/tests/finish-prompt-test.sh
#
# Builds a throwaway repository with the shape of prompts/ and docs/work/handoffs/, runs
# finish-prompt.sh P01 in it, and checks that only the prompt's queue entry, its real Markdown
# links (in tracked and untracked files) and its index row change. Prints PASS or the failures.
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cd "$work"
git init --quiet .
git config user.name test
git config user.email test@example.invalid
git config core.hooksPath /dev/null
git config core.autocrlf false

mkdir -p scripts prompts/not-finished prompts/finished docs/work/handoffs docs/other
cp "$root/scripts/finish-prompt.sh" scripts/

cat > prompts/not-finished/P01-scaffold-app.md <<'EOF'
# P01 — Scaffold
EOF
cat > prompts/not-finished/P02-next.md <<'EOF'
# P02 — Next
EOF

cat > prompts/NEXT-STEPS.md <<'EOF'
# Next steps

Line format:

```text
- [P01](not-finished/P01-scaffold-app.md): one-line summary. Entry: what must be finished first.
```

## Queue

- [P01](not-finished/P01-scaffold-app.md): scaffold. Entry: none.
- [P02](not-finished/P02-next.md): next. Entry: P01.
EOF

cat > prompts/README.md <<'EOF'
# Prompts

2. **Run** it:

   ```text
   Run prompt P01. Read prompts/session-protocol.md, then prompts/not-finished/P01-scaffold-app.md,
   and follow both.
   ```

Plain mention of prompts/not-finished/P01-scaffold-app.md stays as written.

## Index

| ID | Status | Prompt | Summary |
|---|---|---|---|
| P01 | Not started | [Scaffold](not-finished/P01-scaffold-app.md) | Gradle |
| P02 | Not started | [Next](not-finished/P02-next.md) | More |
EOF

cat > docs/other/notes.md <<'EOF'
See [P01](../../prompts/not-finished/P01-scaffold-app.md) and [its goal](../../prompts/not-finished/P01-scaffold-app.md#goal).
EOF

git add -A
git commit --quiet -m 'chore: fixture'

cat > docs/work/handoffs/P01-scaffold-app.md <<'EOF'
# Handoff — P01

Prompt: [P01](../../../prompts/not-finished/P01-scaffold-app.md)
EOF

bash scripts/finish-prompt.sh P01 > /dev/null 2>&1

failures=0
expect() {
  local file=$1 want=$2
  if ! grep -qF -- "$want" "$file"; then
    echo "FAIL: $file should contain: $want"
    failures=$((failures + 1))
  fi
}
reject() {
  local file=$1 unwanted=$2
  if grep -qF -- "$unwanted" "$file"; then
    echo "FAIL: $file should not contain: $unwanted"
    failures=$((failures + 1))
  fi
}

[ -f prompts/finished/P01-scaffold-app.md ] || { echo 'FAIL: prompt not moved'; failures=$((failures + 1)); }

expect prompts/NEXT-STEPS.md '- [P01](not-finished/P01-scaffold-app.md): one-line summary. Entry: what must be finished first.'
reject prompts/NEXT-STEPS.md '- [P01](not-finished/P01-scaffold-app.md): scaffold. Entry: none.'
reject prompts/NEXT-STEPS.md '- [P01](finished/P01-scaffold-app.md): scaffold.'
expect prompts/NEXT-STEPS.md '- [P02](not-finished/P02-next.md): next. Entry: P01.'

expect prompts/README.md 'then prompts/not-finished/P01-scaffold-app.md,'
expect prompts/README.md 'Plain mention of prompts/not-finished/P01-scaffold-app.md stays as written.'
expect prompts/README.md '| P01 | Finished | [Scaffold](finished/P01-scaffold-app.md) | Gradle |'
expect prompts/README.md '| P02 | Not started | [Next](not-finished/P02-next.md) | More |'

expect docs/other/notes.md '[P01](../../prompts/finished/P01-scaffold-app.md)'
expect docs/other/notes.md '[its goal](../../prompts/finished/P01-scaffold-app.md#goal)'

expect docs/work/handoffs/P01-scaffold-app.md '[P01](../../../prompts/finished/P01-scaffold-app.md)'

if [ "$failures" -ne 0 ]; then
  echo "finish-prompt-test: $failures failure(s)"
  exit 1
fi
echo 'finish-prompt-test: PASS'
