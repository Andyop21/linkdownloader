# Handoff — P06 Look and feel: make it cute

Date: 2026-11-15 · Prompt: [P06](../../../prompts/finished/P06-look-and-feel.md)

## What changed

All in `d5109ac`:

1. **Theme tokens** (`ui.theme`):
   - `Color.kt`: full Material 3 light and dark schemes, the status card tones (`StatusTone`,
     `cardColors`) and the mascot's colours. Two starting colours moved to pass WCAG AA: light
     primary `#7C6CF2` → `#6352D9` (white text was 3.97:1) and light error `#D9534A` → `#C4433B`.
     Mint stays `#3FAE93` with dark text on it. The final table is in `docs/architecture.md`
     § *Look and feel*.
   - `ThemeContrastTest` (JVM, 2 tests) checks every text/background pair the screen draws, in both
     themes: 4.5:1 for text, 3:1 for icons, the progress bar and the mascot's face.
   - `Type.kt`: Nunito from Google Fonts' OFL release. The release is only a variable font, so it
     is bundled once (`res/font/nunito.ttf`) and used at 400, 600 and 800 with
     `FontVariation.weight`; licence in `app/src/main/assets/licenses/OFL-Nunito.txt`. Headings and
     titles 800, labels 600, body 400.
   - `Shape.kt`: `PillShape` (bar, every button), `CardShape` 24 dp, `SheetShape` 32 dp top corners,
     `TileShape` 16 dp.
   - `Motion.kt`: `Motion.spring` and a 250 ms `Motion.tween`, used through `springSpec()` /
     `tweenSpec()`; `rememberReducedMotion()` reads the animator duration scale and follows changes
     with a content observer; `LinkDownloaderTheme` provides it as `LocalReducedMotion`.
   - Recorded in [DEC-007](../decisions/DEC-007-look-and-feel-tokens.md).
2. **Screen styling**: cream (light) or deep plum (dark) background; the mascot (104 dp) and "Link
   Downloader" in Nunito ExtraBold centred at the top; the bar as a filled pill with a link icon,
   the "Video link" label clear of the curve, and a lavender ring when focused; **Paste** as a
   76 dp lavender pill with a clipboard icon that shrinks to 95 % on press (spring); the status
   card with 24 dp corners whose tint cross-fades with the state and whose height changes with the
   spring. The content sits a little above the middle (free space 2:3).
3. **Mascot** (`ui/Mascot.kt`), drawn with `Canvas`: a lavender mochi blob with a download-arrow on
   its head, blush and a face. Idle breathes; working has an "o" mouth, bobs with the arrow pushing
   down, and fills from the bottom with the download's progress; happy has "^ ^" eyes, an open
   smile, one hop and three sparkles; worried has raised brows, a frown, a sweat drop and one
   wobble. Content descriptions "Mascot, relaxed / busy downloading / cheering / worried".
4. **Progress**: a 10 dp rounded bar on one pill track, animated with the 250 ms ease so it glides
   between the 250 ms updates; indeterminate while preparing; a still track (announced as
   indeterminate) with motion off.
5. **App icon**: adaptive icon with the idle mascot on `#7C6CF2`, a monochrome layer (silhouette with
   eyes and smile cut out) for themed icons, and the same silhouette as the notification's small
   icon. P01's placeholders are gone.
6. **Edge to edge**: the screen already used `safeDrawing` insets and `imePadding`; the activity now
   also has `adjustResize`, and the column fills the space above the keyboard and scrolls.
7. **Previews**: `MainScreen.kt` previews all 12 kinds of `UiState` (idle, both no-link states,
   preparing, choosing, downloading with and without progress, saving, done with one and several
   videos, failed, About open) in light and dark, plus both sheets' content, light and dark. To
   make the sheets previewable, `ChoiceSheet` and `AboutSheet` now wrap `ChoiceSheetContent` and
   `AboutSheetContent`.
8. **Line icons** (`ui/Icons.kt`): clipboard and link, drawn as stroked paths; no icon library.

No behaviour, state or wording changed; four content descriptions were added for the mascot.

## How it was checked

- `./gradlew assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, lint clean, 176 JVM tests
  pass (P05's 174 unchanged, plus `ThemeContrastTest`'s 2).
- **On the owner's phone** (Samsung SM-S948B, Android 17 / API 37, wireless debugging), debug build,
  driven over `adb` and checked from screenshots kept outside the repository. Links were typed into
  the bar; **Paste** was never tapped, so the owner's clipboard was not read. Public YouTube
  videos only ("Me at the zoo"; "Big Buck Bunny", cancelled at about 24 %) and `https://example.com/`.

| State | Theme | Seen |
|---|---|---|
| Idle | light, dark | Neutral white (light) / plum (dark) card with a thin edge; mascot breathing |
| No link in the bar ("example" + Go) | light | Neutral card, "That doesn't look like a link"; focused bar with lavender ring and label |
| Preparing | light, dark | Lavender card, indeterminate bar on one continuous track, Paste in its soft disabled look, mascot bobbing with "o" mouth |
| Downloading | light, dark | Title in two lines, "Downloading · 24 %", rounded bar, Cancel; the mascot fills to the same level |
| Cancel | light | Back to the idle card at once |
| Done | dark | Mint card, "Saved to Downloads / Me at the zoo.mp4", lavender **Open** pill; mascot hopped and kept its sparkles |
| Failed | dark, light | Soft red card, "This link doesn't lead to a video I can download."; worried mascot with a sweat drop |
| About sheet | light, dark | Rounded sheet, versions, full-width **Update yt-dlp** pill |
| Font scale 200 % | light | Everything fits; the bar's floating label stays clear of the curve |
| Animations off | light | Two frames 1.2 s apart differ only in the text cursor; with animations back on the mascot area changes (breathing) |
| Status bar | light, dark | The mascot silhouette as the notification icon during downloads |
| Launcher icon | — | In App info (Samsung squircle): the mascot on lavender, centred; then enlarged a little (scale 0.62 → 0.68, still inside the safe zone) |

  Afterwards: the test video was deleted from `Download/` (through MediaStore), Big Buck Bunny left
  no file, and dark mode, the font scale (1.0), the animator scale (1.0) and the screen timeout were
  as before.
- **Not checked**: the enlarged icon on the home screen, and themed icons (One UI's support depends
  on its settings); the choice sheet on a device (it needs a post with several videos; it is in the
  previews); the done state in light and the failed state with the largest font in dark (both are
  in the previews and pass the contrast test); TalkBack reading the mascot's moods; the press-scale
  on Paste (needs a finger: adb taps are too short to see it); landscape.

## Docs updated

- `docs/architecture.md`: § *Look and feel* (final palette table, card tones, type, shapes, motion,
  the screen's layout, mascot, icons), § *Code layout* (`ui`, `ui.theme`), § *The screen*
  (components, sheet contents, insets, previews), § *Tests* (`ThemeContrastTest`).
- `docs/work/decisions/DEC-007-look-and-feel-tokens.md` and its line in `docs/work/README.md`.
- `CHANGELOG.md` *Unreleased*: the new look.
- `docs/product.md` unchanged: no behaviour changed.

## Bugs filed

None.

## Open questions

Things for the owner to judge by eye:

- **Mascot**: its shape, face and colours; whether the working "o" mouth reads as busy rather than
  surprised; whether the sparkles should fade out after a while instead of staying.
- **Lavender**: light-mode Paste is the deeper `#6352D9` (needed for AA with white text); the softer
  `#7C6CF2` survives only on the launcher icon.
- **Dark progress track**: the working card's track in dark is the darker surface colour, so it
  reads as a groove; a lighter track is possible but needs a check against the 3:1 rule.
- **Launcher and themed icon** on the home screen, and the notification icon at its small size.
- **Header size**: the mascot and title take about a quarter of the screen; product.md calls it a
  "small header". It can shrink to a row (mascot beside the name) if it feels too big.
- From P05, still open: the bar keeps focus after a hardware (or adb) Enter, so the keyboard comes
  back when a download ends; the result notification is still posted while the screen shows the
  result.

## Next

P07 (release): signed release APK, full test pass on the phone, public-ready docs. It needs the
owner's keystore and licence choice. The checks skipped in P05 (the other must-work sites, a post
with several videos, TalkBack, the haptic tick, the "Choose" notification) and those listed above
belong in P07's full pass. The font licence file is in `assets/licenses/`; P07's public docs may
want to mention it.
