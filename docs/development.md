# Development

## Setup

1. Install Android Studio. It bundles JDK 17 and the Android SDK manager.
2. Clone the repository and switch to the work branch:

   ```bash
   git clone https://github.com/Andyop21/linkdownloader.git
   cd linkdownloader
   git switch worktree
   bash scripts/attribution.sh install   # enables the commit-msg hook (.githooks)
   ```

3. Open the folder in Android Studio and let Gradle sync. It writes `local.properties` with your SDK
   path; that file is ignored and never committed.

## Commands

Run from the repository root. In PowerShell or cmd use `.\gradlew.bat` instead of `./gradlew`.

| Command | Does |
|---|---|
| `./gradlew assembleDebug` | Builds the debug APK into `app/build/outputs/apk/debug/` |
| `./gradlew testDebugUnitTest` | Runs the JVM unit tests |
| `./gradlew lintDebug` | Runs Android lint |
| `./gradlew installDebug` | Installs the debug APK on the connected device or running emulator |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | All checks from the definition of done, in one run |
| `./gradlew assembleRelease` | Builds the signed release APK (needs `keystore.properties`, below) |

These commands work once the app has been scaffolded.

## Engine probe

`EngineProbeTest` runs real extractions and downloads on the connected device or emulator, one per
link, and prints what it found (formats picked, files fetched, time taken). It is skipped unless
links are passed:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.andyop21.linkdownloader.EngineProbeTest \
  "-Pandroid.testInstrumentationRunnerArguments.links=https://example.com/a https://example.com/b"
```

Results print to Logcat under the tag `EngineProbe`. Use public videos only, and never commit the
links you probed with.

## Emulator

Debug builds include `x86_64`, so the app runs on a standard Android Studio emulator on a PC.
Release builds are `arm64-v8a` only and need a real phone (or an ARM emulator).

## Running on a phone

1. On the phone, enable Developer options and USB debugging.
2. Connect it, accept the debugging prompt, and check `adb devices` lists it.
3. `./gradlew installDebug`, then open linkdownloader.

## Release builds

A signed release APK uses a keystore kept **outside** the repository. Its path and passwords go in
`keystore.properties` at the repository root, which is ignored. Never commit a keystore, a
`keystore.properties`, or a password.

## Workflow scripts

| Command | Does |
|---|---|
| `bash scripts/publish.sh` | Pushes `worktree` to origin after checking it. Run after every commit. |
| `bash scripts/promote.sh` | Fast-forwards `master` to `worktree`. Only when the owner asks. |
| `bash scripts/finish-prompt.sh <ID>` | Closes a finished prompt session. |
| `bash scripts/attribution.sh install` | Enables the commit-msg hook. `publish.sh` runs it too. |

On Windows, run them from Git Bash.
