# P01 — Scaffold the Android project

**Goal.** A clean, buildable Android project that matches `docs/architecture.md`: Gradle with a
version catalog, Kotlin, Jetpack Compose and Material 3, one `app` module and one placeholder
screen. Every later prompt builds on this, so it has to be tidy and current.

**Entry condition.** None. The repository holds only docs, prompts and scripts.

**Read first.** `docs/architecture.md` (§ *Stack*, *Android targets*, *Code layout*,
*Dependencies*), `docs/development.md`, `docs/definition-of-done.md`.

## Scope

- The Gradle build, the wrapper, the version catalog, and the `app` module.
- `LinkDownloaderApp`, `MainActivity`, a placeholder `MainScreen`, and a minimal `ui.theme` that
  later prompts replace.

## Out of scope

youtubedl-android, WorkManager, the download pipeline, the real UI and the real theme. Do not add
those dependencies yet. No CI configuration.

## Deliverables

1. **Gradle wrapper and build files.**
   - The Gradle wrapper at the latest stable Gradle version that the chosen Android Gradle Plugin
     supports, committed with `gradle/wrapper/gradle-wrapper.jar`. Check that `./gradlew --version`
     runs from Git Bash and `.\gradlew.bat --version` runs from PowerShell.
   - `settings.gradle.kts`, the root `build.gradle.kts`, `gradle.properties`
     (`kotlin.code.style=official`, `android.useAndroidX=true`, a sensible `org.gradle.jvmargs`,
     configuration cache on if the plugins support it).
   - `gradle/libs.versions.toml` with **the latest stable versions** of AGP, Kotlin, the Compose
     compiler plugin, the Compose BOM, AndroidX core-ktx, activity-compose and lifecycle, plus JUnit
     4 and kotlinx-coroutines-test for later tests. Look the versions up rather than guessing, and
     pin exact versions (no `+`, no ranges).
2. **The `app` module** (`app/build.gradle.kts`):
   - `namespace` and `applicationId` `io.github.andyop21.linkdownloader`; `versionCode 1`,
     `versionName "0.1.0"`.
   - `minSdk 29`; `compileSdk` and `targetSdk` the latest stable API level.
   - Java and Kotlin toolchain 17.
   - ABI filters: debug `arm64-v8a` and `x86_64`; release `arm64-v8a` only.
   - Compose turned on through the Kotlin Compose compiler plugin.
   - Kotlin compiler warnings as errors.
   - Lint: `abortOnError = true`, `warningsAsErrors = true`, with only the dependency-freshness
     checks (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`) disabled so
     the build does not fail every time a library releases.
   - Release build type: not yet signed and not minified. P07 sets that up.
3. **Manifest and resources.** App label "Link Downloader", no permissions yet, one launcher
   activity, a theme with no action bar, and a placeholder launcher icon (P06 makes the real one).
   `INTERNET` and the rest arrive with the prompts that need them.
4. **Code.**
   - `LinkDownloaderApp : Application` (empty body for now, registered in the manifest).
   - `MainActivity` with `enableEdgeToEdge()` that sets `MainScreen` inside the app theme.
   - `ui/MainScreen.kt`: a centred title. Placeholder only, no logic.
   - `ui/theme/`: a minimal `LinkDownloaderTheme` built on Material 3 with light and dark colour
     schemes taken from `docs/architecture.md` § *Look and feel*. Dynamic colour off.
5. **Checks run clean.** `./gradlew assembleDebug testDebugUnitTest lintDebug` passes with no
   warnings, and the app installs and opens on an emulator or the owner's phone
   (`./gradlew installDebug`).

## Definition of done

- [ ] Deliverables 1–5 exist and match `docs/architecture.md`.
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- [ ] The placeholder screen was seen on a device or emulator, in light and dark mode.
- [ ] No generated or local files committed (`build/`, `.idea/`, `local.properties`).
- [ ] `docs/architecture.md` and `docs/development.md` still match. If the latest stable versions
      forced a change (for example a different JDK), the docs say so.

## Handoff

Write `docs/work/handoffs/P01-scaffold-app.md` from `docs/work/templates/handoff.md`. List every
pinned version. Then run `bash scripts/finish-prompt.sh P01`.
