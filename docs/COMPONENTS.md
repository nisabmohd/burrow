# Components

The only components a screen may use. Each lists its spec and where the Kotlin lives
(`starter/app/src/main/java/app/burrow/money/ui/components/`). A screen never styles anything by hand;
if something is missing, add a component here first.

| Component | Spec | Kotlin |
|---|---|---|
| `TabScreen` / `TabTopBar` | 64 dp; `headline` title 24 dp from the start; right: up to two `TonalIconButton`s, 8 dp apart. `TabScreen` = status bar + top bar + content with the 16 dp margin | Bars.kt |
| `ScreenTopBar` | 64 dp; back or close `TonalIconButton` · optional `title` · one action (Save pill, Edit, Duplicate) | Bars.kt |
| `TonalIconButton` | 48 dp circle, `surfaceContainer`; active `primaryContainer`; optional count badge | Controls.kt |
| `FilterHeaderButtons` | funnel + calendar, 8 dp apart; badge = number of active filters | Controls.kt |
| `PillTextField` | the one text field: 56 dp pill, `surfaceContainer`, optional icon, clear button when typing, **2 dp primary ring while focused** (137, 143); `lines > 1` = a tall box with 24 dp corners, icon and text at the top (Paste a message 121) | Controls.kt |
| `HeaderIcon` / `CenterTitleHeader` | 64 dp primaryContainer icon circle (`neutral` for something not yours: a built-in pack, 118); the centred header when the big line is words ("142 payments recorded") | Blocks.kt |
| `MessageBox` / `MessageText` | a bank message in a surfaceContainer box with pill highlights (amount strong, payee/account soft, ring while teaching); header optional (none under a card that already names the sender, 116) | MessageText.kt |
| `MessageCard` | a message the app found (To review 44, Not understood 116): circle, title, line, optional amount, ✕; the message; optional chips; **one full-width tonal button** (Save / Teach). The only cards with a button; they never swipe (C2) | MessageCard.kt |
| `CenterHeader` | 64 dp icon circle → 12 dp → `titleSmall` → `display` amount → `body` line → thin bar | Blocks.kt |
| `BudgetHero` (Today only) | `primaryContainer` (errorContainer when over), 24 dp, 20 dp inside: label + `display` amount counting up once + line + `WavyProgress` | ui/today/BudgetHero.kt |
| `Note` | 72 dp, 24 dp, solid 40 dp icon circle, title + line, one text button or arrow; info / warn / error | Blocks.kt |
| `ListGroup` + `ListRow` | rows 2 dp apart, 24/6 corners, 72 dp (56 one-line), lead · headline + line · trailing; optional long-press, and `lifted` while dragged | Lists.kt |
| `BlockColumn` / `BlockGap` | a page body: blocks 12 dp apart; `SectionHeader`s inside keep 16 above / 8 below (they take the column gap off their own padding) | Lists.kt |
| `ChipRow` | quick choices in ONE row of equal chips, label centred, no check (Yesterday · Today · Tomorrow); never wraps | Controls.kt |
| `MonthHeader` + `MonthCalendar` | "October 2026" ‹ › over seven equal columns of 38 dp day circles; the Dates sheet and the one `DateSheet` (never Material's DatePicker: its 48 dp cells overflow narrow phones) | ui/filters/FilterSheets.kt |
| `IconCircle` | 40 dp (64 big), duotone icon at 55%; category colours or neutral | Lists.kt |
| `SectionHeader` | `button` style title, meta or text button right, 16 above / 8 below | Lists.kt |
| `TxnRow` / `TxnCircle` | the one transaction row (Today › Recent, Activity): category circle, payee, "Food & drinks · HDFC" (+ time today), amount | ui/txn/TxnRow.kt |
| `LinkButton` | primary text button ("See all", "Clear all") | Controls.kt |
| `WavyProgress` / `rememberCountUp` | the hero's wavy bar; a number that counts up once per screen | Charts.kt / CountUp.kt |
| `AmountTrailing` | `titleSmall` amount over `label` status ("in 6 days", "Paid") | Lists.kt |
| `Widget` / `PairRow` / `halvesPairUp` | half: 184 dp fixed; wide: wraps, text left + visual right. Halves (Today's widgets, Insights' A day + Biggest, More's tiles) pair two to a row, **one per row at large text** (150–152) | Blocks.kt |
| Status tiles (More) | 2 equal columns (`minmax(0,1fr)`), 24 dp, icon top-left, status top-right, title, one line | screen code |
| Category tiles | 3 columns, 56 dp circle + one-word name; dashed "New" tile | screen code |
| `PillBarChart` | 16 dp pill bars, 104 dp tall (40 in widgets), labels, dots for empty, dashed average; one tap area over the chart picks the bar, TalkBack gets a summary and an action per bar | Charts.kt |
| `BurrowNavRail` | the bottom nav's tabs as a side rail on wide screens (tablets, unfolded) | Bars.kt |
| `BurrowSnackbarHost` | where the Undo snackbar shows; the FAB moves up with it | Snackbar.kt |
| `CappedFontScale` | caps font scaling (1.3×) for text inside fixed shapes only: nav labels, keypad digits, ring numbers | CappedFontScale.kt |
| `ThinBar` | a thin progress bar (budget and goal rows) | Blocks.kt |
| `ChartCard` / `AverageLegend` / `ChartHeaderLink` | card with icon + label (+ legend, or "All days" as a `ChartHeaderLink`: plain primary text, never a TextButton in the 24 dp row), the big number, one line, then the chart | ChartCard.kt |
| `CumulativeLineChart` · `Sparkline` · `StackBar` · `DonutChart` · `SplitBar` · `heatColor` | the Insights line vs the period before; widget lines; By category bar; Top category ring; Money in and out; heatmap tones | Charts.kt |
| `RingPercent` | budget / goal ring: 40 dp in rows (tiny % inside), 52 in widgets, 104 wide; red when over, check when reached | Charts.kt |
| `ProgressRing` | ring with a gap, round caps; 40 (rows), 52 (widgets), 104 (wide widget) | Charts.kt |
| Heatmap | 7 columns of 38 dp circles, 5 tonal steps of primary, today ringed, future muted | Charts.kt (`heatLevel`) |
| `ButtonGroup` | settings choices only (theme, text size, week start, Recent 3 / 5 / 10, Bill / Subscription, Repeat every): 2–3 options, 48 dp pills 4 dp apart; selected = primary **with a check**, check + label centred together. Never tabs | Controls.kt |
| `PillTabs` | **tabs** (§ Pill tabs): Plan (22–24) fixed = equal widths, 44 dp; Customise (80–85) `scrolling` = own widths, 40 dp, open tab scrolled into view. 8 dp apart, selected primary / onPrimary, others `surfaceContainerHigh` / onSurfaceVariant, label centred, no check | Controls.kt |
| `Chip` / `ChipWrap` | 36 dp pills; selected `secondaryContainer` + check, else outline; optional icon ("More"); `ChipWrap` wraps in sheets | Controls.kt |
| `BurrowButton` | filled (primary), tonal (secondaryContainer), danger (errorContainer), off (looks disabled, still taps); 48 dp, 56 dp at the bottom | Controls.kt |
| `AddFab` | `primaryContainer`, 56 dp, pill, icon + label; moves up over the snackbar; **icon only at large text** (150–152) | Bars.kt |
| `TypeSheet` / `RepeatOrLendSheet` | from "New expense ▾" (111): Expense · Income · Transfer; Repeat or lend (112): Once · Repeat · Subscription · Lent · Borrowed, then How often or who, Done | ui/add/ExtrasSheets.kt |
| `ComingRow` + `comingUp()` | Plan › Coming up (22): bills and future-dated payments in one list grouped Overdue · This week · This month · Later; circle · name · ↻/📅 "in 5 days · ICICI" · amount over date; swipe right Paid / Add now, left Skip / Delete | ui/plan/PlanScreen.kt, ComingUp.kt |
| `BurrowSheet` + `SheetHeader` / `SheetBody` / `SheetFooter` | every bottom sheet; title + one line + close button; body scrolls; footer buttons pinned | BurrowSheet.kt |
| Pickers | `CategorySheet`, `AccountSheet`, `DateSheet`, `TextSheet`: shared by every form | ui/pickers/Pickers.kt |
| `LabeledField` | a form field with its label above (New bill, New goal, New account) | Fields.kt |
| `KeepTopVisible` | scrolls a list back to the top when a note appears above its first item | Lists.kt |
| `DatesSheet` / `MonthCalendar` | presets row + month calendar (locale's first weekday), range band, today ringed, future muted, "Show 1 – 19 Oct" | ui/filters/FilterSheets.kt |
| `FilterSheet` / `ActiveFilterChips` | Type (Activity only) · Accounts · Categories chips · Reset / "Show 14 transactions"; removable chips + Clear all under the search | ui/filters/FilterSheets.kt |
| `Keypad` | 4 × 4, 52 dp pill keys 6 dp apart, docked under New expense (17); operators `secondaryContainer`, ✓ primary (off until it can save); hold ⌫ to clear | Keypad.kt |
| `ValueSheet` + `AmountSheet` / `DaySheet` / `TimeValueSheet` | **every number** (§ Value sheet): title + line · big value with the blinking `Caret` · optional chips (`ValueChip`) and rows · `NumberPad` · Done, or Cancel / secondary + Save. Amount: budget 32, add money 35, refund 96, fix balance 140, pay card bill 141, prepay / EMI 142, split part, Welcome budget 136; day: due day 28, month start 68, statement / due day; time 135; rate 84. No − / + steppers anywhere | ValueSheet.kt |
| `NumberPad` | the value sheet's 3 × 4 keypad: the same 52 dp keys, 1–9, `.` (a gap for days and time), 0, ⌫ (hold clears) | Keypad.kt |
| `ValueText` / `ValuePill` / `Caret` | a value on a screen that opens its value sheet: big `display` text (Welcome budget 99) or a tonal pill in a row (Split parts 95); the 3 dp blinking caret (also New expense) | ValueSheet.kt |
| `EmptyState` + `Cookie` | 128 dp wavy cookie shape (`primaryContainer`), icon centred at 42%, title, one line, one tonal button | Blocks.kt |
| `UndoSnackbar` (`LocalSnackbar`) | after every change, ~4 s, Undo runs in the app scope; above the nav bar | Snackbar.kt |
| `ConfirmDialog` | delete and replace data only (a wrong password shows inside the password sheet, 144) | Dialogs.kt |
| `BurrowSwitch` | Material 3 switch, primary track, toggle haptic | Controls.kt |
| `Chevron` | trailing arrow of a row that opens a screen; mirrors in RTL (back/forward/chevron/backspace drawables are `autoMirrored`) | Controls.kt |
| `BurrowNavBar` | 5 items, 80 dp on `surfaceContainerLow`, selected icon fill + `secondaryContainer` pill; labels shrink to 10 sp, capped at 1.3×; **at large text only the selected tab shows its label** (150–152) | Bars.kt |
| `FormChip` / `FormChipRow` | New expense's one chip (17): 36 dp pill in a 48 dp target; outlined (a choice), selected (`secondaryContainer`), tonal (an extra: Note · Time · Receipt · Repeat or lend), filled (a favourite: `surfaceContainerHigh`, amount first in bold, then the name, no icon); optional 24 dp category circle and a quieter trailing amount (favourites "Chai ₹20"); the row scrolls sideways | PayeeField.kt |
| `PayeeField` | Paid to: 56 dp pill, storefront or the category circle, 2 dp primary ring while typing, ✕ clears; payee matches show as `FormChip`s under it | PayeeField.kt |
| `ChoiceTiles` (`Choice`) | pick one: equal tiles (`columns` per row; a line shows big as the value, "₹1,240"), or full-width rows when options have icons; selected = `primaryContainer` + `CheckBadge` | Customise.kt |
| `ToggleTiles` (`Toggle`) | pick many: 3-column 112 dp tiles, icon circle + name, check badge when on (empty ring when off), on = `secondaryContainer` | Customise.kt |
| `OrderedRow` | ⠿ drag handle · 40 dp icon · name · switch, inside `DragColumn`; a hidden row's icon dims to 55%. No up/down arrows anywhere (16) | Customise.kt |
| `DragColumn` | reorder by drag (16): hold the ⠿ handle, the row lifts (`surfaceContainerHigh` + shadow), the others slide to make room; saved on drop; TalkBack gets "Move … up / down" actions | DragList.kt |
| `LetterCircle` / `CurrencyCircle` | 40 dp neutral circle with one letter (Import: Cashew, Walnut, Money Manager; never brand logos); a currency's symbol ("$", "€", "AE") in Add a currency (139) and other currencies (83) | Lists.kt |
| `TxnActions` + `TxnActionsHost` (`LocalTxnActions`) | long-press any `TxnRow` (06): one sheet (Change category, Duplicate to today, Split, Add a refund, Save as favourite, Delete in its own group) and the refund sheet (09), hosted once by the nav host | ui/txn/TxnActions.kt |
| `PickRow` / `PickList` | **every pick-one list** (C1): a plain row (never tinted) with the round check on the right — Type 111, Account 20, Option 138, currency, language, backup schedule, lock after, New bill "When it's due"; `PickList` = a group of them from `Choice`s (top card 80, numbers and dates 85). `ring = false` only on the long Category sheet (19): just the chosen row shows its check | Customise.kt |
| `CheckBadge` | the round check (§ Round check): 24 dp ring (2 dp outline) → filled primary with a 14 dp ✓; pick-one rows, tiles, Teach rows | Customise.kt |
| `StatusTile` | More's four tiles: icon circle (category hue), status top-right, title, one line; "on" tiles tinted `secondaryContainer` | Blocks.kt |
| `SwipeRow` (`SwipeAction`) | a row with a swipe each way, set in Customise › Transactions; springs back; TalkBack gets the same as custom actions | SwipeRow.kt |
| `StepDots` / `IntroPanel` / `IntroCard` / `IntroTile` | Welcome and Auto-capture intro: step dots (current = 24 dp pill), the rose panel, tilted white cards | Intro.kt |
| `BurrowLogo` | the Burrow mark on a primary rounded square (Welcome 40 dp, About 112 dp) | Intro.kt |
| `PinScreen` | lock in a cookie, title, four dots, 3 × 4 pad of 72 dp round keys (fingerprint, 0, ⌫) | ui/more/LockScreens.kt |

## Haptics (`Haptics.kt`)
toggle (switch, mark paid) · tick (chip, segment, stepper) · stepTick (slider) · threshold (swipe far
enough) · confirm (save, Done). Long-press keeps the system long-press.

## Gestures
- Coming up row (bills and future-dated payments): swipe right = Paid / Add now (or open it, when Customise › Transactions › Swipe right is Edit), swipe left = Skip / Delete (Undo snackbar). Tap = open.
- **To review and Messages not understood never swipe** (C2): each card has its own full-width tonal button (Save / Teach); ✕ ignores; "Save N payments" saves all.
- Activity row: swipe right = Edit or Duplicate, swipe left = Delete, when chosen in Customise › Transactions (off by default).
- Transaction row: long-press = sheet (Duplicate, Change category, Delete).
- Pull down on a sheet closes it; predictive back closes sheets and pops screens.


## Pick list
One-of-many choice with 3+ options or long values (number format, date format). A `ListGroup` of `PickRow`s:
title = the example value (tabular figures), subtitle = what it is, trailing round check. Use choice tiles only for 2–3 short options.

## Pill tabs
`PillTabs`: 44 dp pills, 8 dp gap. Fixed set (Plan) = equal widths; scrolling set (Customise) = own widths, 40 dp. Selected =
primary/onPrimary, others surfaceContainerHigh/onSurfaceVariant. No check mark. Settings choices stay a `ButtonGroup` (with a check).

## Round check (pick one)
24 dp ring (outline) → filled primary with a 14 dp ✓. Used by every pick-one list, sheet or screen, and by Teach rows (C1).

## Update sheet
BurrowSheet: title, "Version x · you have y", What's new block (surfaceContainerLow, 24 dp, primary 6 dp dots), state line (wavy
progress / hint / error), footer Later (tonal) + main (filled). States 128–132; About's row states 124–127.

## Value sheet (one pattern for every number, user 2026-10-24)
Amount, time, day of month, rate, budget, add money, refund, fix balance, pay in — all the same `ValueSheet`:
title + muted line · **big value (`display`) with a blinking caret** · optional quick chips / AM-PM / one or two rows ·
**the same 3 × 4 keypad** (1–9, `.`, 0, ⌫; no `.` for days and time) · footer (Done, or Cancel + Save).
No − / + steppers anywhere. On screens (Welcome budget, Split parts) the number is a tappable value that opens this sheet.
The first key typed replaces the value shown; a chip sets it. Over a maximum (a refund) the value turns red and Save looks off.

## Swatch grid (`SwatchGrid.kt`)
`SwatchGrid` lays round choices in six equal columns, rows filled from the start (12 colours = two
full rows); `HuePicker` (the 12 category hues) and `IconPicker` are built on it. Used by Edit category
(62), Edit account (106/107) and the accent colours on Appearance (seven columns). Never a FlowRow: on a large display size it wrapped to 5 and spread
the last row to the edges.
