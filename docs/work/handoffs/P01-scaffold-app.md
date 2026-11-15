# Handoff — P01 Scaffold the Android project

Date: 2026-11-15 · Prompt: [P01](../../../prompts/finished/P01-scaffold-app.md)

## What changed

All in `24c7b0e build(app): scaffold the Android project with Compose`.

1. **Gradle wrapper and build files.** Gradle 9.9.1 wrapper (bin distribution, pinned SHA-256),
   `gradle-wrapper.jar` committed. `settings.gradle.kts` (repositories, foojay toolchain resolver,
   `:app`), root `build.gradle.kts` (plugins `apply false`), `gradle.properties`
   (`org.gradle.jvmargs=-Xmx4g`, build cache and configuration cache on, `android.useAndroidX=true`,
   `kotlin.code.style=official`), and `gradle/libs.versions.toml`.
2. **The `app` module.** Namespace and application ID `io.github.andyop21.linkdownloader`,
   `versionCode 1`, `versionName "0.1.0"`, `minSdk 29`, `compileSdk`/`targetSdk 37`, Java 17
   compile options and `kotlin { jvmToolchain(17) }`, ABI filters (debug `arm64-v8a` + `x86_64`,
   release `arm64-v8a`), Compose through the Kotlin Compose compiler plugin, `allWarningsAsErrors`,
   lint `abortOnError` + `warningsAsErrors` with only `GradleDependency`, `NewerVersionAvailable` and
   `AndroidGradlePluginVersion` disabled. Release is unsigned and not minified.
3. **Manifest and resources.** Label "Link Downloader", no permissions, `MainActivity` as the
   launcher, `Theme.LinkDownloader` on `android:Theme.Material(.Light).NoActionBar` with a window
   background matching the palette (light and night), and a placeholder adaptive launcher icon
   (lavender background, white download arrow, monochrome layer).
4. **Code.** `LinkDownloaderApp` (empty), `MainActivity` (`enableEdgeToEdge()`, `MainScreen` in
   `LinkDownloaderTheme`), `ui/MainScreen.kt` (centred title, with a preview), and `ui/theme/`
   (`Color.kt`, `Theme.kt`: the architecture palette as light and dark schemes, dynamic colour off).
5. **Checks run clean** (below).

### Pinned versions

| What | Version |
|---|---|
| Gradle | 9.9.1 |
| Android Gradle Plugin | 9.5.1 (built-in Kotlin) |
| Kotlin / Compose compiler plugin | 2.4.21 |
| foojay-resolver-convention (settings plugin) | 1.0.0 |
| Compose BOM | 2026.09.00 |
| androidx.core:core-ktx | 1.19.1 |
| androidx.activity:activity-compose | 1.13.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.11.0 |
| junit:junit | 4.13.2 |
| kotlinx-coroutines-test | 1.11.0 |
| minSdk / compileSdk / targetSdk | 29 / 37 / 37 |
| JDK toolchain | 17 |

Versions were read from Google Maven, Maven Central, the Gradle Plugin Portal and
services.gradle.org on 2026-11-15. AGP 9.5 supports at most API 37 and needs Gradle 9.6+, so the
SDK repository's stable minor platforms 37.1 and 37.2 were not used.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug --warning-mode all`: BUILD SUCCESSFUL, lint
  "No issues found", no Gradle deprecation warnings. `testDebugUnitTest` has no tests yet
  (NO-SOURCE).
- `./gradlew clean assembleDebug assembleRelease`: both build; the debug APK carries
  `lib/arm64-v8a` and `lib/x86_64`, the release APK only `lib/arm64-v8a`.
- `./gradlew buildEnvironment`: AGP's built-in Kotlin dependency (2.2.10) resolves to 2.4.21 through
  the Compose compiler plugin.
- `./gradlew --version` from Git Bash and `.\gradlew.bat --version` from PowerShell both report
  Gradle 9.9.1. `javaToolchains` shows a Temurin 17 auto-provisioned by Gradle.
- `./gradlew installDebug` on the `S25_Ultra` emulator (API 36, x86_64): the app opens and shows the
  centred lavender title on the cream background in light mode and on `#1B1825` in dark mode
  (switched with `adb shell cmd uimode night yes/no`), edge to edge.
- Not checked: the owner's phone; the launcher icon was not looked at in the app drawer.

## Docs updated

- `docs/architecture.md`: Stack row for the build (AGP 9 built-in Kotlin, Compose plugin sets the
  Kotlin version, toolchain 17); Android targets (API 37, SDK levels in the catalog, the one
  `ChromeOsAbiSupport` suppression).
- `docs/development.md`: JDK notes (Android Studio bundles 21; Gradle needs 17+; toolchain 17
  auto-provisioned), `JAVA_HOME` and `sdk.dir` for command-line builds, lint policy, release APK is
  unsigned until P07; removed "These commands work once the app has been scaffolded".
- `README.md`: build requirement reads "Android Studio (or any JDK 17+)".
- `CHANGELOG.md`: no entry; the placeholder screen is not a user-facing feature yet.

## Bugs filed

- [BUG-001](../bugs/BUG-001-finish-prompt-rewrites-examples.md): `finish-prompt.sh` also edits the
  example lines in `prompts/NEXT-STEPS.md` and `prompts/README.md`. Restored by hand this session.

## Open questions

- **Lint vs. release ABI.** With `warningsAsErrors`, lint's `ChromeOsAbiSupport` fails on the
  release `arm64-v8a`-only filter that the architecture requires. It is suppressed with
  `//noinspection ChromeOsAbiSupport` on that one line rather than disabled globally, so the lint
  configuration stays as the prompt specified. Owner may prefer a different choice.
- **Toolchain provisioning.** `jvmToolchain(17)` needs a JDK 17; Android Studio now bundles 21, so
  the foojay resolver settings plugin was added to download one automatically. The alternative is
  dropping the toolchain and keeping only `sourceCompatibility`/`targetCompatibility` 17.
- On this machine the default `java` is Java 8, so command-line builds need `JAVA_HOME` set (see
  `docs/development.md`).

## Next

P02: add youtubedl-android and probe the five sites on the owner's phone. That prompt adds the
dependency, `INTERNET`, `extractNativeLibs` and legacy JNI packaging, and a decision record (DEC-003).
`kotlinx-coroutines-test` and JUnit are in the catalog but not yet wired into `app`; add them as
`testImplementation` with the first tests.
