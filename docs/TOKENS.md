# Tokens

The numbers behind every screen. Source of truth: `design/tokens.json` (machine-readable) and the
Kotlin in `starter/…/ui/theme/` (`Color.kt`, `Type.kt`, `Dimens.kt`, `Motion.kt`). If this file, the
JSON and the Kotlin ever disagree, the Kotlin is wrong: fix it to match the prototype.

## Colour

One seed hue makes every chrome colour (HSL formulas in `Color.kt`). Default seed **Rose 345**.
Other accents in Appearance: Rose 345, Rust 12, Amber 38, Green 150, Teal 192, Blue 215, Purple 275. Dynamic colour (wallpaper) is opt-in, Android 12+, off by default.

### Roles (Rose, light / dark)
| Token | Light | Dark | Used for |
|---|---|---|---|
| `background` | #FDF9FA | #130F10 | Page |
| `surfaceLow` | #F6EEF0 | #1F191B | c1 · cards, rows, widgets, tiles (surfaceContainerLow) |
| `surface` | #EFE4E7 | #2A2325 | c2 · search, keys, icon buttons, neutral circles, tracks (surfaceContainer) |
| `surfaceHigh` | #E6DBDE | #372F31 | c3 · pressed, chart dots (surfaceContainerHigh) |
| `onSurface` | #20181A | #EBE5E6 | Text |
| `onSurfaceVariant` | #5F5356 | #BCAEB2 | Supporting text, labels, inactive icons |
| `outline` | #BEB1B4 | #5C5154 | Dashed average line, chip outline |
| `primary` | #9E2E4A | #F9AABD | The one strong action, selected nav, chart highlight, links |
| `onPrimary` | #FFFFFF | #45111E | On primary |
| `primaryContainer` | #FCD4DE | #602937 | Today hero, header icon circles, welcome panel |
| `onPrimaryContainer` | #4E1221 | #FDD8E1 | On primaryContainer |
| `secondaryContainer` | #E9D3D8 | #49373B | Selected chip / row / tab pill, info note, bar track |
| `onSecondaryContainer` | #381E25 | #F0DBE0 | On secondaryContainer |
| `error` | #B82832 | #F9949B | Overdue, over budget, failure text |
| `errorContainer` | #FCD9DB | #62282C | Error note |
| `onErrorContainer` | #5A161B | #FDD8DB | On errorContainer |
| `warningContainer` | #FBE6C1 | #524128 | Paused / attention note (Burrow extra) |
| `onWarningContainer` | #533313 | #FBE6C1 | On warningContainer |

**Colour rules**
- Amounts are never coloured. The only exception: "₹710 over" and an over-budget header amount in `error`.
- Red means overdue, over budget or an error. Never decoration.
- Category colours appear only in category circles, chart bars/rings for that category and the colour picker.
- One `primary`-filled thing per screen (the main button, or the selected bar). Everything else is tonal.
- Shadows: none on cards. Only the FAB, sheets and snackbar float.

### Category hues
Each category stores a hue; its colours come from it and only follow light/dark (never the seed or dynamic colour).
| Name | Hue | Light circle (bg / icon) | Light solid | Dark circle (bg / icon) | Dark solid |
|---|---|---|---|---|---|
| Lavender | 275 | #E9D3F8 / #4C1A70 | #8636BF | #402B50 / #DBB9F4 | #BA83E2 |
| Indigo | 245 | #D6D3F8 / #211A70 | #4136BF | #2E2B50 / #BEB9F4 | #8B83E2 |
| Blue | 215 | #D3E2F8 / #1A3E70 | #366FBF | #2B3A50 / #B9D1F4 | #83ABE2 |
| Cyan | 192 | #D3F1F8 / #1A5E70 | #36A4BF | #2B4850 / #B9E8F4 | #83CFE2 |
| Teal | 168 | #D3F8F1 / #1A705E | #36BFA4 | #2B5048 / #B9F4E8 | #83E2CF |
| Green | 140 | #D3F8DF / #1A7037 | #36BF64 | #2B5037 / #B9F4CC | #83E2A3 |
| Lime | 95 | #E2F8D3 / #3E701A | #6FBF36 | #3A502B / #D1F4B9 | #ABE283 |
| Mustard | 50 | #F8F2D3 / #70611A | #BFA836 | #50492B / #F4EAB9 | #E2D283 |
| Orange | 28 | #F8E4D3 / #70421A | #BF7636 | #503C2B / #F4D4B9 | #E2AF83 |
| Coral | 10 | #F8D9D3 / #70281A | #BF4D36 | #50312B / #F4C3B9 | #E29383 |
| Rose | 345 | #F8D3DC / #701A30 | #BF3658 | #502B34 / #F4B9C8 | #E2839B |
| Pink | 318 | #F8D3ED / #701A56 | #BF3696 | #502B45 / #F4B9E2 | #E283C5 |

## Type
Lexend (default), Outfit, Atkinson Hyperlegible, Space Grotesk or the system font; user picks in More › Font and text size.
Text size: Small 0.92× · Default · Large 1.12×, on top of the system font scale. **Two weights only: 400 and 500.**
Numbers use tabular figures (`tnum`) so amounts line up.

| Style (`BurrowType.`) | Size / line | Weight | Used for |
|---|---|---|---|
| `display` | 44 / 52, −0.5 tracking | 500 | Today hero amount, centred-header amount, keypad amount |
| `headline` | 28 / 36 | 500 | Tab titles; welcome/intro headline uses 36 / 44 |
| `title` | 22 / 28 | 500 | Widget values, sheet and top-bar titles, stats |
| `titleSmall` | 16 / 24 | 500 | Row amounts, header title, note title, card titles |
| `bodyLarge` | 16 / 24 | 400 | Row headline, text fields |
| `body` | 14 / 20 | 400 | Supporting line, descriptions |
| `button` | 14 / 20 | 500 | Buttons, chips, section headers, text buttons |
| `label` | 12 / 16 | 500 | Widget labels, nav labels, chart axis, legends |
| `keypad` | 24 / 32 | 400 | Keypad digits only |
| `intro` | 36 / 44 | 500 | Welcome and intro headlines only |
| `tiny` | 10 / 12 | 500 | The percent inside a 40 dp ring only |

Never set a size or weight in a screen. Long translations wrap; nav labels may shrink to 10 sp, never below.

## Space (4 dp grid)
| Token | dp | Where |
|---|---|---|
| `Space.screen` | 16 | Left/right margin of every screen |
| `Space.block` | 12 | Between cards / blocks on a page |
| `Space.row` | 2 | Between rows of one group |
| `Space.sectionTop` / `sectionBottom` | 16 / 8 | Section header above / to its group |
| `Space.card` | 16 | Padding inside cards, widgets, notes, rows (horizontal) |
| `Space.sheet` | 24 | Sheet side padding |

## Sizes
Row 72 (56 one-line) · icon circle 40 (big 64) · icon 24 (22 in circles) · button 48 (56 at the bottom of a sheet/screen)
· icon button 48 · chip 36 · keypad key 60 · top bar 64 · bottom nav 80 · search 56 · note 72 · half widget 184
· heatmap day 38 · chart bar 16 wide, chart 104 tall. Touch targets are never under 48 dp.

## Shape — the one-shape rule
Only two shapes exist (user, 2026-10-19):
- **Surfaces: 24 dp corners.** Cards, widgets, list groups (24 outside / 6 between rows), sheets (top), dialogs, notes, snackbar, tiles.
- **Everything small or tappable: full circle or pill.** Icon circles, buttons, icon buttons, chips, checkboxes (round), radios,
  switches, keypad keys, badges, highlights in SMS text, heatmap days, chart bars.
No 4/8/12 dp squares anywhere. App icon: 112/512 corner (the launcher masks it anyway).

## Elevation
Tonal, not shadows. Level 0: page, cards, rows. Level 1: sheets. Level 3: FAB, snackbar.

## Motion
See `docs/MOTION.md`. Durations 150 / 300 / 450 ms. Easing: standard `(0.2, 0, 0, 1)`,
emphasized decelerate `(0.05, 0.7, 0.1, 1)`, emphasized accelerate `(0.3, 0, 0.8, 0.15)`.
