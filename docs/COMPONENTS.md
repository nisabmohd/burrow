# Components

The only components a screen may use. Each lists its spec and where the Kotlin lives
(`starter/app/src/main/java/app/burrow/money/ui/components/`). A screen never styles anything by hand;
if something is missing, add a component here first.

| Component | Spec | Kotlin |
|---|---|---|
| `TabScreen` / `TabTopBar` | 64 dp; `headline` title 24 dp from the start; right: up to two `TonalIconButton`s, 8 dp apart. `TabScreen` = status bar + top bar + content with the 16 dp margin | Bars.kt |
| Top bar (detail) | 64 dp; back `TonalIconButton` · optional `title` · one action (edit / filter) | screen code |
| Top bar (form) | close · title · filled **Save** pill (disabled = `surfaceContainer`) | screen code |
| `TonalIconButton` | 48 dp circle, `surfaceContainer`; active `primaryContainer`; optional count badge | Controls.kt |
| `FilterHeaderButtons` | funnel + calendar, 8 dp apart; badge = number of active filters | Controls.kt |
| Search bar | 56 dp pill, `surfaceContainer`, search icon, clear button when typing | screen code |
| `CenterHeader` | 64 dp icon circle → 12 dp → `titleSmall` → `display` amount → `body` line → thin bar | Blocks.kt |
| Hero (Today only) | `primaryContainer`, 24 dp, label + `display` + line + wavy progress | screen code |
| `Note` | 72 dp, 24 dp, solid 40 dp icon circle, title + line, one text button or arrow; info / warn / error | Blocks.kt |
| `ListGroup` + `ListRow` | rows 2 dp apart, 24/6 corners, 72 dp (56 one-line), lead · headline + line · trailing | Lists.kt |
| `IconCircle` | 40 dp (64 big), duotone icon at 55%; category colours or neutral | Lists.kt |
| `SectionHeader` | `button` style title, meta or text button right, 16 above / 8 below | Lists.kt |
| `AmountTrailing` | `titleSmall` amount over `label` status ("in 6 days", "Paid") | Lists.kt |
| `Widget` | half: 184 dp fixed; wide: wraps, text left + visual right | Blocks.kt |
| Next-up card | `surfaceContainerLow` card: label, 64 dp icon circle + name/amount/when, one filled "Mark paid" | screen code |
| Status tiles (More) | 2 equal columns (`minmax(0,1fr)`), 24 dp, icon top-left, status top-right, title, one line | screen code |
| Category tiles | 3 columns, 56 dp circle + one-word name; dashed "New" tile | screen code |
| `PillBarChart` | 16 dp pill bars, 104 dp tall, labels, dots for empty, dashed average, tap to select | Charts.kt |
| `ProgressRing` | ring with a gap, round caps; 40 (rows), 52 (widgets), 104 (wide widget) | Charts.kt |
| Heatmap | 7 columns of 38 dp circles, 5 tonal steps of primary, today ringed, future muted | Charts.kt (`heatLevel`) |
| Connected button group | 2–3 options, 48 dp pills; selected = primary with a check | Material `SingleChoiceSegmentedButtonRow` styled as pills |
| Chips | 36 dp pills; selected `secondaryContainer` + check; `ChipWrap` wraps in sheets | Controls.kt |
| Buttons | filled (primary), tonal (secondaryContainer), text; 48 dp, 56 dp at the bottom | Material, `Shapes.pill` |
| Extended FAB | `primaryContainer`, 56 dp, pill, icon + label; moves up over the snackbar | Material |
| `BurrowSheet` | every bottom sheet; title + one line + close button; footer buttons pinned | BurrowSheet.kt |
| `DayStepper` | − day +, quick chips, result line, Done — month start and bill due day | Controls.kt |
| Dates sheet | presets row + month calendar, range highlight, "Show 1 – 19 Oct" | build in week 2 |
| Filter sheet | Type (button group, Activity only) · Accounts chips · Categories chips · Reset / "Show 14 transactions" | build in week 2 |
| Keypad | 4 × 4, 60 dp pill keys; operators `secondaryContainer`, ✓ primary | build in week 1 |
| `EmptyState` | 128 dp wavy cookie shape (`primaryContainer`), icon centred at 42%, title, one line, one tonal button | Blocks.kt |
| Snackbar | after every change, ~4 s, Undo; above the nav bar | Material |
| Dialog | delete, replace data, wrong password only | Material |
| Switch | Material 3 switch, primary track | Material |
| `BurrowNavBar` | 5 items, 80 dp on `surfaceContainerLow`, selected icon fill + `secondaryContainer` pill; labels shrink to 10 sp, capped at 1.3× | Bars.kt (Material `NavigationBar`) |

## Haptics (`Haptics.kt`)
toggle (switch, mark paid) · tick (chip, segment, stepper) · stepTick (slider) · threshold (swipe far
enough) · confirm (save, Done). Long-press keeps the system long-press.

## Gestures
- Bill row: swipe right = Paid, swipe left = Skip (Undo snackbar). Tap = open.
- Review card: swipe right = Save, swipe left = Ignore; ✕ ignores; "Save N payments" saves all.
- Transaction row: long-press = sheet (Duplicate, Change category, Delete).
- Pull down on a sheet closes it; predictive back closes sheets and pops screens.
