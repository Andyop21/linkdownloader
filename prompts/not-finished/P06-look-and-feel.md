# P06 — Look and feel: make it cute

**Goal.** Turn the working screen into something that feels lovely to use: the pastel theme, the
rounded font, the mascot with its moods, soft motion, and a matching app icon. It stays calm and
simple, with no clutter, in light and dark. This session changes how things look and move, never
what they do.

**Entry condition.** P05 finished.

**Read first.** `docs/product.md` § *The screen* and *Look and feel*, `docs/architecture.md`
§ *Look and feel*, P05's handoff, the `ui` package.

## Scope

`ui.theme` (colours, type, shapes, motion), restyling the P05 components, the mascot, the launcher
icon and the notification's small icon.

## Out of scope

Any change to behaviour, state or strings' meaning (wording may be polished). New screens, new
features, settings.

## Deliverables

1. **Theme tokens** in `ui.theme`:
   - Colour schemes from `docs/architecture.md` § *Look and feel*, filled out to full Material 3
     light and dark schemes. Check every text/background pair used on screen against WCAG AA
     (4.5:1 for body text, 3:1 for large text and icons). Adjust colours that fail, and write the
     final values back into the architecture table.
   - Nunito bundled in `res/font` (400, 600, 800) from Google Fonts' OFL release, with the OFL
     licence text committed beside it (for example `app/src/main/assets/licenses/OFL-Nunito.txt`).
     A Material 3 `Typography` built on it.
   - Shapes: pill bar and button, 24 dp status card, rounded sheet.
   - Motion constants: one spring spec and one 200–300 ms easing, used everywhere. A
     `rememberReducedMotion()` that reads the system animator duration scale; when it is 0, all
     motion is off.
2. **Screen styling.** The cream background; the header with the app name in Nunito ExtraBold and
   the mascot; the bar as a soft filled pill; Paste as the biggest element (lavender, with a
   clipboard icon and a gentle press-scale); the status card that changes tint with the state
   (neutral, lavender while working, mint on done, soft red on failure) and animates its content
   size smoothly. Generous spacing; the content sits a little above centre.
3. **Mascot** (`ui/Mascot.kt`): a round, friendly blob with a small download-arrow, drawn in
   Compose (`Canvas` or `ImageVector`, no bitmaps). Four moods: idle (slow breathing float),
   working (bobbing, in step with progress), happy (a little hop and sparkle on done), worried
   (a small wobble on failure). It follows reduced motion. It has a content description per mood.
4. **Progress.** A rounded linear (or soft circular) indicator in the card, animated between
   updates so it glides rather than jumps.
5. **App icon.** An adaptive launcher icon from the mascot on a lavender background, with a
   monochrome layer for themed icons, and a matching small notification icon (white silhouette).
   Replace P01's placeholder.
6. **Edge to edge.** Content respects system bars and the keyboard (`imePadding`); the bar stays
   visible while typing.
7. **Previews.** Compose `@Preview`s of the screen in each `UiState`, in light and dark, so the look
   can be reviewed in Android Studio without running downloads.

## Definition of done

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- [ ] On a real phone, every state looks right in light and dark mode, with the largest font size,
      and with animations turned off in system settings.
- [ ] Every text/background pair passes WCAG AA; the final palette is recorded in
      `docs/architecture.md`.
- [ ] The launcher icon looks right on the home screen, including themed icons where supported.
- [ ] No behaviour changed: P05's unit tests pass unchanged.
- [ ] `CHANGELOG.md` *Unreleased* mentions the new look.

## Handoff

Write `docs/work/handoffs/P06-look-and-feel.md`, describing each state's look and anything the
owner should judge by eye under *Open questions*. Then run `bash scripts/finish-prompt.sh P06`.
