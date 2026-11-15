---
id: DEC-007
title: "Look and feel: one variable Nunito file, a deeper lavender, drawn icons and a contrast test"
date: 2026-11-15
status: accepted
---

# DEC-007 — Look and feel: one variable Nunito file, a deeper lavender, drawn icons and a contrast test

## Context

P06 gave the screen its look: palette, Nunito, shapes, motion, the mascot and the app icon. A few
choices in it are not obvious from the code and would be easy to undo by accident.

## Decision

- **Font.** Google Fonts' OFL release of Nunito ships only a variable font (`Nunito[wght].ttf`,
  weight 200–1000), no static files. It is bundled once as `res/font/nunito.ttf` and used at
  three weights (400, 600, 800) through Compose's `FontVariation.weight`, which works from API 26
  (`minSdk` is 29). The licence is `assets/licenses/OFL-Nunito.txt`. The italic file is not
  bundled; the app has no italic text.
- **Lavender.** The starting light primary `#7C6CF2` gives white text a contrast of 3.97:1, below
  WCAG AA for body text. It moved to `#6352D9` (5.62:1 with white, 4.58:1 on its own container),
  which still reads as lavender. The light error colour moved from `#D9534A` to `#C4433B` for the
  same reason. Mint `#3FAE93` stays, with dark text on it, since white fails there. The original
  `#7C6CF2` lives on as the launcher icon's background, where no text sits on it.
- **Contrast is tested.** `ThemeContrastTest` (JVM) lists every text/background pair the screen
  draws, in both themes, and checks 4.5:1 for text and 3:1 for icons, the progress bar and the
  mascot's face. A colour change that fails it fails the build.
- **Icons are drawn, not imported.** The clipboard and link icons (`ui/Icons.kt`) are a few stroked
  paths, and the mascot is drawn with `Canvas`. No `material-icons-extended`, no bitmaps, no new
  dependency.
- **Motion.** One spring (damping 0.6, medium-low stiffness) and one 250 ms ease
  (`CubicBezierEasing(0.2, 0, 0, 1)`) in `ui.theme.Motion`. The mascot's loops (breathing,
  bobbing) use the same easing curve over their own longer periods. `LinkDownloaderTheme` provides
  `LocalReducedMotion`, read from the system animator duration scale and kept current with a
  content observer; when it is 0 every transition snaps and the mascot stands still.

## Consequences

- One 270 KB font file instead of three static ones.
- Changing a colour means changing `ui/theme/Color.kt`, the table in `docs/architecture.md`, and
  passing `ThemeContrastTest`.
- A new text/background pair on screen should be added to `ThemeContrastTest`.

## Alternatives considered

- Three static Nunito files instanced from the variable font with fonttools: more bytes, and they
  would no longer be the release as published.
- Downloadable Google Fonts: needs Play services and a network call, and the app has neither need.
- `material-icons-extended` for two icons: a large dependency for two shapes.
- Keeping `#7C6CF2` and relying on the large-text rule for Paste: Cancel, the focused bar label
  and the Open button would still fail.
