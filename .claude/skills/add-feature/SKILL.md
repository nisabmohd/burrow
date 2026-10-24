---
name: add-feature
description: The checklist for adding or changing a feature in Burrow so it stays consistent, small and verifiable. Use whenever starting a new feature, screen or behaviour change.
---

# Add a feature

1. Read `CLAUDE.md` (hard rules, behaviour rules), `docs/DESIGN.md` (the ten rules) and the feature's PNGs in `design/screens/`.
2. No new dependency without asking. No INTERNET in play/foss (github: only the update check). Libraries in use: `gradle/libs.versions.toml`.
3. Pure logic (money maths, rules parsing, bill dates, budgets) goes in plain Kotlin with unit tests in `app/src/test` — write the tests first. Money is `Long` minor units.
4. Build the UI only from `ui/components/` (CenterHeader, Note, ListGroup/ListRow, Widget, PillBarChart, ProgressRing, TonalIconButton, FilterHeaderButtons, ChipWrap, DayStepper, BurrowSheet, EmptyState). Missing one? Add it to `docs/COMPONENTS.md` and `ui/components/` first.
5. Text only via `BurrowType.*`; spacing/sizes/shapes via `Space`, `Size`, `Shapes`; colours by role from `MaterialTheme.colorScheme` / `categoryColors()`; motion from `Motion.kt`.
6. Check the Lite rules: one hero (only Today), centred header, one signal per number, no buttons in cards, notes first, one shape, sheets for pickers.
7. Every clickable ≥ 48 dp with a TalkBack label; gestures get a `CustomAccessibilityAction`; haptics from `Haptics.kt`.
8. All text in `strings.xml` and `values-hi/strings.xml`, using the words in `docs/COPY.md`.
9. Lists: stable keys, `contentType`, immutable state; queries off the main thread; totals in SQL.
10. Build, run, compare with the PNG in light, dark, largest font and a 360 dp phone. Add a check to `tools/smoke.py`.
11. Update `docs/` if behaviour or design changed, tick `TODO.md`, commit as one change saying what and why.
