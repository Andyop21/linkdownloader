# Definition of done

A task is finished only when every item below holds.

1. **Git.** Every commit is on `worktree`, uses `type(scope): short imperative summary`, carries no
   AI attribution, and is published with `bash scripts/publish.sh`. The tree is clean.
2. **Builds and checks.** `./gradlew assembleDebug testDebugUnitTest lintDebug` passes, with no new
   lint warnings.
3. **Tests.** New or changed logic in `link` or `MainViewModel` has unit tests. A bug fix carries the
   test that failed before it, where the bug can be tested on the JVM.
4. **Seen working.** A change to the screen or the download was tried on a device or emulator, and
   the handoff (or commit message, outside prompt sessions) says what was tried. If it could not be
   tried, say so.
5. **Docs.** `docs/product.md` matches the behaviour, `docs/architecture.md` matches the structure,
   `docs/development.md` matches the commands, and a user-visible change has its line under
   *Unreleased* in `CHANGELOG.md`.
6. **Public-safe.** No secrets, keystores, `local.properties`, personal data, cookies or private
   links in the diff.
7. **Loose ends filed.** Unrelated defects are filed in `docs/work/bugs/`; open questions are in the
   handoff.
8. **Prompt sessions.** The handoff is in `docs/work/handoffs/`, `bash scripts/finish-prompt.sh <ID>`
   has run, and its result is committed with the handoff and published.
