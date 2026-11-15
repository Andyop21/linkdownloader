---
id: DEC-008
title: "A professional look from the Enamel design tokens: no mascot, Lato, cool grays and one blue"
date: 2026-11-16
status: accepted
---

# DEC-008 — A professional look from the Enamel design tokens

## Context

The P06 look (pastel lavender, Nunito, a mascot that cheered and worried) read as a children's
app. The owner asked for a cleaner, more professional and more precise look, built from the design
tokens of their other project, Enamel, without copying its logo or branding. The owner also asked
that the screen think for the user: no selecting text to start again.

## Decision

- **Tokens taken from Enamel:** the cool-gray neutral ramp and its semantic aliases (canvas,
  surface, ink, muted, faint, border, control border), the console blue for actions with a deeper
  fill in the dark theme so white labels pass, the success, warning and danger ramps with their
  soft tints, the dark overlay for the snackbar, the corner scale with capsule buttons and the
  concentric rule, the type roles (title, lead, heading, card title, eyebrow, body), and the motion
  curves and durations (ease-out, ease-in, standard, spring; 120 to 640 ms), with its rise, tactile
  press and live dot.
- **Left out:** Enamel's brand colour, its logo and mark, and every name or wording of its own.
  The app's accent is the neutral blue.
- **Lato**, the face Enamel uses, from Google Fonts' OFL release, unmodified: three static files
  (regular, bold, black), not Enamel's own converted subsets. About 2 MB together, small next to
  the bundled Python and yt-dlp. The licence ships as `assets/licenses/OFL-Lato.txt`.
- **No mascot; an orb instead.** A first pass put a status card at the top left under a large
  title, and the owner found it empty and soulless: there is not enough content to fill a
  left-aligned page. The screen is now centred around one living element, a disc in the status's
  colour with the progress as a ring around it and the percentage inside, much as a store shows an
  app downloading. Its motion is all meaning: a breathing halo when ready, the arrow dropping into
  the tray while the link is read, the ring filling, a pop and a ripple when saved, one shake on a
  failure. The status line and its buttons sit centred under it. The launcher and notification
  icons become the same arrow into a tray.
- **The field and Paste link at the bottom**, together and in reach of the thumb; the app's name
  in a small centred title bar at the top.
- **One spacing scale** (`Space`, 4 dp steps), so every gap is deliberate.
- **The screen thinks for the user:** a clear button in the field; the field empties itself on a
  save or a cancel; **Try again** after a failure that may pass; and the link saved last is not
  downloaded twice by accident (product B12, B13).
- `ThemeContrastTest` keeps checking every pair the screen draws, now against `AppColors`.

DEC-007's contrast test, drawn icons and reduced-motion handling stay. Its Nunito file, lavender
palette, mascot and single spring are replaced by this decision.

## Consequences

- Colours are read by name from `AppTheme.colors`; Material's scheme is derived from them for the
  Material parts that remain (sheets, snackbar).
- The README screenshot and every document that described the mascot changed with it.
- "Already saved" is remembered in memory only, for the last saved link: it guards against an
  accidental second tap, not against every duplicate.

## Alternatives considered

- The system font (Roboto): no extra bytes and clean, but it would not carry Enamel's type, which
  the owner asked for.
- Copying Enamel's own subset Lato files: smaller, but they are modified fonts under Lato's reserved
  font name; the unmodified release is the safe choice for a public repository.
- Keeping a smaller, quieter mascot: it would still set a playful tone the owner wanted gone.
- A left-aligned page with a status card (the first pass): tidy, but bare on a tall phone.
- Reading the clipboard on launch to offer the link before Paste is tapped: Android 12+ shows a
  "pasted from your clipboard" notice on every read, and architecture.md keeps reading it to the
  tap. Left as a possible proposal.
