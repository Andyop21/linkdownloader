# Development

## Setup

1. Install Android Studio. It bundles a JDK (21) and the Android SDK manager. Gradle 9 needs
   JDK 17 or newer to run; the app itself compiles with a JDK 17 toolchain, which Gradle downloads
   on first build if none is installed (foojay resolver, `settings.gradle.kts`). AGP installs a
   missing SDK platform on first build too.
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
Outside Android Studio, point `JAVA_HOME` at a JDK 17+ first (for example the one Android Studio
bundles) if `java -version` reports something older:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"      # Git Bash
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"      # PowerShell
```

Command-line builds also need the SDK path: `local.properties` with
`sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk` (Android Studio writes it on sync), or
`ANDROID_HOME` set.

| Command | Does |
|---|---|
| `./gradlew assembleDebug` | Builds the debug APK into `app/build/outputs/apk/debug/` |
| `./gradlew testDebugUnitTest` | Runs the JVM unit tests |
| `./gradlew lintDebug` | Runs Android lint |
| `./gradlew installDebug` | Installs the debug APK on the connected device or running emulator |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | All checks from the definition of done, in one run |
| `./gradlew assembleRelease` | Builds the signed, shrunk release APK into `app/build/outputs/apk/release/` (needs `keystore.properties`, below) |
| `adb install -r app/build/outputs/apk/release/app-release.apk` | Installs (or updates) the release APK on the connected phone |

Lint treats every warning as an error, except the dependency-freshness checks (`GradleDependency`,
`NewerVersionAvailable`, `AndroidGradlePluginVersion`). Kotlin compiler warnings are errors too.

## Engine probe

`EngineProbeTest` runs real extractions and downloads on the connected device or emulator, one per
link, and prints what it found (formats picked, files fetched, time taken). It is skipped unless
links are passed:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.andyop21.linkdownloader.EngineProbeTest \
  "-Pandroid.testInstrumentationRunnerArguments.links=https://example.com/a https://example.com/b"
```

Results print to Logcat under the tag `EngineProbe`. The probe names links by number and host, but
titles and yt-dlp's own messages still show video IDs and account names. Read them with `adb logcat -d -s EngineProbe` (clear first with `adb logcat -c`). With more than one
device connected, pick one with `ANDROID_SERIAL=<serial>` before `./gradlew`.

The probe runs every link once with the yt-dlp the app has, then updates yt-dlp
(`updateYoutubeDL(STABLE)`) and runs every link again. Add
`-Pandroid.testInstrumentationRunnerArguments.update=false` to skip the update and the second pass.
For each link it picks formats by the rules in DEC-003 and tries the candidates in order (HTTPS
pair, HLS pair, single) until one downloads; when every candidate gets HTTP 403 it extracts once
more. `-Pandroid.testInstrumentationRunnerArguments.allCandidates=true` fetches every candidate.
`connectedDebugAndroidTest` installs the app before the run and uninstalls it afterwards, so every
run starts from the bundled yt-dlp. The test fails if any link fails in any pass, and deletes
everything it downloaded either way.

Besides the formats, the log shows each downloaded file's real container (read from its first
bytes), its tracks as `MediaExtractor` sees them, and whether `MediaMuxer` accepts each track for an
MP4.

### The whole pipeline

`-Pandroid.testInstrumentationRunnerArguments.pipeline=true` runs the app's own `VideoDownloader`
on every link instead, after the update (unless `update=false`), exactly as a download from the app
would. For each link it logs the events with their times, the saved file's name, size, real
container and tracks, and it fails if a link fails, if the progress ever goes backwards or does not
end at 1, or if a job folder is left behind. Two more arguments go with it:

| Argument | Does |
|---|---|
| `keep=true` | Leaves the saved videos in `Downloads/`, so they can be played in the gallery or Files app. They stay there after the test uninstalls the app; delete them by hand (Files app, or `adb shell rm "/sdcard/Download/<name>"`). Without it the probe deletes them. |
| `select=all` | For a post with several videos: the probe logs the choices it was offered (count, lengths, thumbnail hosts), then downloads the chosen ones in a second job, as the screen does. `all` (the default) or 0-based positions such as `0,2`. |
| `cancel=true` | Also downloads the first link twice more and cancels it, once during the fetch (past 20 %) and once while it is being written to `Downloads/` (past 95 %), and checks that no `Downloads/` entry, job folder or yt-dlp process is left. Put a link with a large file first (a YouTube video), so the fetch passes 20 % in more than one step. |

Read the pipeline's own lines with `adb logcat -d -s EngineProbe VideoDownloader YtDlp`; they show
the plan it picked and how long each step took. Pass links without their tracking parameters
(`?utm_source=…`, `&s=20`): the sites do not need them, and the arguments pass through a shell on
the device.

Use public videos only, and never commit the links you probed with. Sites rate-limit repeated
requests (Instagram did after eight extractions in eleven minutes), so probe sparingly.

## Trying the screen by hand

Install the debug build (`./gradlew installDebug`) and use the screen. Over `adb`, a link can be
typed into the bar and sent with the keyboard's action key, without touching the clipboard:

```bash
P=io.github.andyop21.linkdownloader
adb shell am start -n $P/.MainActivity
adb shell input tap <x> <y>                                   # the bar
adb shell "input text 'https://example.com/v/1'"              # quote links with ? or &
adb shell input keyevent KEYCODE_ENTER                        # Go
```

`uiautomator dump` lists the screen's texts and bounds (in Git Bash, prefix the command with
`MSYS_NO_PATHCONV=1` so `/sdcard/...` is not rewritten). To see the notification permission request
again, revoke it and clear its flags, then reinstall or clear the app's data (the app asks only once):

```bash
adb shell pm revoke $P android.permission.POST_NOTIFICATIONS
adb shell pm clear-permission-flags $P android.permission.POST_NOTIFICATIONS user-set user-fixed
```

Read the workers' lines with
`adb logcat -d -s DownloadWorker UpdateWorker VideoDownloader WM-WorkerWrapper`.

To lose the network for this app only (aeroplane mode would also cut wireless debugging):

```bash
adb shell cmd connectivity set-chain3-enabled true
adb shell cmd connectivity set-package-networking-enabled false $P    # true to restore
adb shell cmd connectivity set-chain3-enabled false
```

In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb` commands with device paths such as `/sdcard`.

## Emulator

Debug builds include `x86_64`, so the app runs on a standard Android Studio emulator on a PC.
Release builds are `arm64-v8a` only and need a real phone (or an ARM emulator).

## Running on a phone

1. On the phone, enable Developer options and USB debugging.
2. Connect it, accept the debugging prompt, and check `adb devices` lists it.
3. `./gradlew installDebug`, then open Link Downloader.

## Release builds

A signed release APK uses a keystore kept **outside** the repository. Its path and passwords go in
`keystore.properties` at the repository root, which is ignored. Never commit a keystore, a
`keystore.properties`, or a password.

Create the keystore once, outside the repository, with the JDK's `keytool` (Android Studio's own
JDK has it in `jbr/bin`). It asks for a password; with the default PKCS12 format the key uses the
same one. `-dname "CN=linkdownloader"` keeps a personal name out of the certificate, which anyone
can read from the APK:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\keystores" | Out-Null
keytool -genkeypair -v -keystore "$env:USERPROFILE\keystores\linkdownloader-release.jks" `
  -alias linkdownloader -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=linkdownloader"
```

Then write `keystore.properties` at the repository root (`storeFile` may be absolute, or relative to
the repository root):

```properties
storeFile=C:/Users/<you>/keystores/linkdownloader-release.jks
storePassword=<password>
keyAlias=linkdownloader
keyPassword=<password>
```

Without the file, with a key missing from it, or with a `storeFile` that does not exist,
`assembleRelease` stops at `checkReleaseSigning` with a message saying which; it never produces an
unsigned APK. Debug builds do not need the file. Keep a backup of the keystore and its password:
an APK signed with another key cannot be installed over the existing app.

The release build runs R8 (`isMinifyEnabled`, `isShrinkResources`) with the rules in
`app/proguard-rules.pro`, and packages `arm64-v8a` only.

## Versions

`versionName` is `major.minor.patch`; `versionCode = major*10000 + minor*100 + patch` (1.0.0 is
`100`, 1.2.3 is `10203`). Both are set in `app/build.gradle.kts`. Raise them together for every
release APK, and move *Unreleased* in `CHANGELOG.md` under the new version.

## Making a release

Only from `master`, after `bash scripts/promote.sh`, and only when the owner asks. In Git Bash
from the repository root, with `keystore.properties` in place and the GitHub CLI signed in:

```bash
V=1.0.0                                            # the versionName in app/build.gradle.kts
git switch master && git pull --ff-only
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk "LinkDownloader-$V.apk"
sha256sum "LinkDownloader-$V.apk"                  # goes in the release notes
git tag -a "v$V" -m "Link Downloader $V" && git push origin "v$V"
gh release create "v$V" "LinkDownloader-$V.apk" --title "Link Downloader $V" --notes-file notes.md
rm "LinkDownloader-$V.apk" && git switch worktree
```

`notes.md` (not committed) holds the version's section of `CHANGELOG.md`, the requirements, the
install steps and the checksum. A release adds only a tag on `master`; nothing is committed
there. Keep the keystore and its password backed up: a release signed with another key
cannot be installed over the previous one.

## Workflow scripts

| Command | Does |
|---|---|
| `bash scripts/publish.sh` | Pushes `worktree` to origin after checking it. Run after every commit. |
| `bash scripts/promote.sh` | Fast-forwards `master` to `worktree`. Only when the owner asks. |
| `bash scripts/finish-prompt.sh <ID>` | Closes a finished prompt session. |
| `bash scripts/tests/finish-prompt-test.sh` | Tests `finish-prompt.sh` in a throwaway repository. Run it after changing that script. |
| `bash scripts/attribution.sh install` | Enables the commit-msg hook. `publish.sh` runs it too. |

On Windows, run them from Git Bash.
