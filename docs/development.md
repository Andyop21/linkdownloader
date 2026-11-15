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

These commands work once the app has been scaffolded.

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
