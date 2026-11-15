# P07 — Release 1.0.0

**Goal.** A signed, installable release APK that has been tested end to end on the owner's phone,
with the README, changelog and docs ready for the repository to go public.

**Entry condition.** P06 finished.

**Read first.** `docs/development.md` (§ *Release builds*, *Commands*), `docs/definition-of-done.md`,
`docs/product.md`, `README.md`, `CHANGELOG.md`, the handoffs of P03–P06.

## Scope

Signing, shrinking, versioning, the release build, the final test pass, and the public-facing docs.

## Out of scope

New features and redesigns. Bugs found here are fixed only if they are small and safe; anything
larger is filed in `docs/work/bugs/` and listed in the handoff for the owner to decide.

## Deliverables

1. **Signing.** The release build type reads `keystore.properties` from the repository root
   (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); when the file is absent, release
   builds fail with a clear message rather than producing an unsigned APK. Give the owner the
   exact `keytool -genkeypair` command to create a keystore **outside** the repository, and wait
   for them to create it. Never generate, see or commit their passwords; confirm `.gitignore`
   covers the files.
2. **Shrinking.** Turn on R8 (`isMinifyEnabled`, `isShrinkResources`) with the keep rules
   youtubedl-android and its Jackson mapper need and the kotlinx.serialization rules. If R8 cannot
   be made to work reliably, leave it off and record why in a decision record; the native
   libraries dominate the size anyway.
3. **Version** `versionCode 100`, `versionName "1.0.0"`. Document the scheme in
   `docs/development.md` (`versionCode = major*10000 + minor*100 + patch`).
4. **Release test pass** on the owner's phone with the release APK
   (`./gradlew assembleRelease`, then `adb install -r`): every behaviour B1–B10 in
   `docs/product.md`, all must-work sites, light and dark, cancel, no internet, leaving the app
   during a download, the About sheet's update, and a reboot followed by a fresh download. Record
   the results as a table in the handoff.
5. **Size and start-up.** Record the release APK size and the time from tap to first frame on a
   cold start (the first launch unpacks Python; say how long that takes, and make sure the screen
   shows immediately and the first download says "Getting ready…" while it finishes).
6. **Public-ready docs.**
   - `README.md`: what it is, one screenshot (light) without personal content, how to install the
     APK, how to build, supported sites, responsible-use note, credits (yt-dlp,
     youtubedl-android, Nunito).
   - `CHANGELOG.md`: move *Unreleased* into `## [1.0.0] - <date>`.
   - Check every doc in `docs/` against the code one last time and fix what drifted.
   - Ask the owner which license to use. Point out that youtubedl-android is GPL-3.0 (DEC-001),
     so GPL-3.0 is the natural choice for an app that bundles it. Add `LICENSE` only after they
     answer.
7. **Hand over for promotion.** Do **not** run `scripts/promote.sh`. In the handoff, tell the owner
   that `worktree` is ready and that `bash scripts/promote.sh` moves `master` to 1.0.0 when they
   choose. Offer to tag `v1.0.0` on `master` after promotion and to attach the APK to a GitHub
   release; do neither unasked.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease` passes.
- [ ] The signed release APK was installed on the owner's phone and passed the test table.
- [ ] No keystore, `keystore.properties`, password or personal content in the repository or its
      history.
- [ ] README, CHANGELOG and every doc match the app; the license question was put to the owner.

## Handoff

Write `docs/work/handoffs/P07-release.md` with the test table, APK size and start-up time, filed
bugs, and the promotion note. Then run `bash scripts/finish-prompt.sh P07`.
