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
| `PillTextField` | the one text field: 56 dp pill, `surfaceContainer`, optional icon, clear button when typing (search, payee, note) | Controls.kt |
| `HeaderIcon` / `CenterTitleHeader` | 64 dp primaryContainer icon circle; the centred header when the big line is words ("142 payments recorded") | Blocks.kt |
| `MessageBox` / `MessageText` | a bank message in a surfaceContainer box with pill highlights (amount strong, payee/account soft, ring while teaching); can report taps | MessageText.kt |
| `CenterHeader` | 64 dp icon circle → 12 dp → `titleSmall` → `display` amount → `body` line → thin bar | Blocks.kt |
| `BudgetHero` (Today only) | `primaryContainer` (errorContainer when over), 24 dp, 20 dp inside: label + `display` amount counting up once + line + `WavyProgress` | ui/today/BudgetHero.kt |
| `Note` | 72 dp, 24 dp, solid 40 dp icon circle, title + line, one text button or arrow; info / warn / error | Blocks.kt |
| `ListGroup` + `ListRow` | rows 2 dp apart, 24/6 corners, 72 dp (56 one-line), lead · headline + line · trailing; optional long-press, and `lifted` while dragged | Lists.kt |
| `IconCircle` | 40 dp (64 big), duotone icon at 55%; category colours or neutral | Lists.kt |
| `SectionHeader` | `button` style title, meta or text button right, 16 above / 8 below | Lists.kt |
| `TxnRow` / `TxnCircle` | the one transaction row (Today › Recent, Activity): category circle, payee, "Food & drinks · HDFC" (+ time today), amount | ui/txn/TxnRow.kt |
| `LinkButton` | primary text button ("See all", "Clear all") | Controls.kt |
| `WavyProgress` / `rememberCountUp` | the hero's wavy bar; a number that counts up once per screen | Charts.kt / CountUp.kt |
| `AmountTrailing` | `titleSmall` amount over `label` status ("in 6 days", "Paid") | Lists.kt |
| `Widget` | half: 184 dp fixed; wide: wraps, text left + visual right | Blocks.kt |
| Next-up card | `surfaceContainerLow` card: label, 64 dp icon circle + name/amount/when, one filled "Mark paid" | ui/plan/PlanScreen.kt |
| Status tiles (More) | 2 equal columns (`minmax(0,1fr)`), 24 dp, icon top-left, status top-right, title, one line | screen code |
| Category tiles | 3 columns, 56 dp circle + one-word name; dashed "New" tile | screen code |
| `PillBarChart` | 16 dp pill bars, 104 dp tall (40 in widgets), labels, dots for empty, dashed average; one tap area over the chart picks the bar, TalkBack gets a summary and an action per bar | Charts.kt |
| `ChartCard` / `AverageLegend` | card with icon + label (+ legend or "All days"), the big number, one line, then the chart | ChartCard.kt |
| `CumulativeLineChart` · `Sparkline` · `StackBar` · `DonutChart` · `SplitBar` · `heatColor` | the Insights line vs the period before; widget lines; By category bar; Top category ring; Money in and out; heatmap tones | Charts.kt |
| `RingPercent` | budget / goal ring: 40 dp in rows (tiny % inside), 52 in widgets, 104 wide; red when over, check when reached | Charts.kt |
| `ProgressRing` | ring with a gap, round caps; 40 (rows), 52 (widgets), 104 (wide widget) | Charts.kt |
| Heatmap | 7 columns of 38 dp circles, 5 tonal steps of primary, today ringed, future muted | Charts.kt (`heatLevel`) |
| `ButtonGroup` | 2–3 options, 48 dp pills 4 dp apart; selected = primary with a check | Controls.kt |
| `Chip` / `ChipWrap` | 36 dp pills; selected `secondaryContainer` + check, else outline; optional icon ("More"); `ChipWrap` wraps in sheets | Controls.kt |
| `BurrowButton` | filled (primary), tonal (secondaryContainer), danger (errorContainer), off (looks disabled, still taps); 48 dp, 56 dp at the bottom | Controls.kt |
| `AddFab` | `primaryContainer`, 56 dp, pill, icon + label; moves up over the snackbar | Bars.kt |
| `BurrowSheet` + `SheetHeader` / `SheetBody` / `SheetFooter` | every bottom sheet; title + one line + close button; body scrolls; footer buttons pinned | BurrowSheet.kt |
| Pickers | `CategorySheet`, `AccountSheet`, `DateSheet`, `TextSheet`: shared by every form | ui/pickers/Pickers.kt |
| `AmountStepperSheet` | − amount + with quick chips and an extra row (budget edit 32, Add money 35, Pay bill, Prepay) | StepperSheet.kt |
| `LabeledField` | a form field with its label above (New bill, New goal, New account) | StepperSheet.kt |
| `KeepTopVisible` | scrolls a list back to the top when a note appears above its first item | Lists.kt |
| `DayStepper` | − day +, quick chips, result line, Done — month start and bill due day | Controls.kt |
| `DatesSheet` / `MonthCalendar` | presets row + month calendar (locale's first weekday), range band, today ringed, future muted, "Show 1 – 19 Oct" | ui/filters/FilterSheets.kt |
| `FilterSheet` / `ActiveFilterChips` | Type (Activity only) · Accounts · Categories chips · Reset / "Show 14 transactions"; removable chips + Clear all under the search | ui/filters/FilterSheets.kt |
| `Keypad` | 4 × 4, 60 dp pill keys 8 dp apart; operators `secondaryContainer`, ✓ primary (off until it can save); hold ⌫ to clear | Keypad.kt |
| `EmptyState` + `Cookie` | 128 dp wavy cookie shape (`primaryContainer`), icon centred at 42%, title, one line, one tonal button | Blocks.kt |
| `UndoSnackbar` (`LocalSnackbar`) | after every change, ~4 s, Undo runs in the app scope; above the nav bar | Snackbar.kt |
| `ConfirmDialog` | delete, replace data, wrong password only | Dialogs.kt |
| `BurrowSwitch` | Material 3 switch, primary track, toggle haptic | Controls.kt |
| `Chevron` | trailing arrow of a row that opens a screen; mirrors in RTL (back/forward/chevron/backspace drawables are `autoMirrored`) | Controls.kt |
| `BurrowNavBar` | 5 items, 80 dp on `surfaceContainerLow`, selected icon fill + `secondaryContainer` pill; labels shrink to 10 sp, capped at 1.3× | Bars.kt (Material `NavigationBar`) |
| `ExtraChip` / `ExtraChipRow` | New expense extras under the rows (Note, Repeat, Attach, Lend): outlined 36 dp chip, icon + label; set = `secondaryContainer` with the value ("Every month", "1 file"); the row scrolls sideways | PayeeField.kt |
| `PayeeField` + `SuggestionChips` | Paid to: 56 dp pill under the amount, storefront or the category circle, 2 dp primary ring while typing, ✕ clears; up to 4 quiet outlined chips centred under it (with the category circle while typing) | PayeeField.kt |
| `ChoiceTiles` (`Choice`) | pick one: equal tiles (`columns` per row; a line shows big as the value, "₹1,240"), or full-width rows when options have icons; selected = `primaryContainer` + `CheckBadge` | Customise.kt |
| `ToggleTiles` (`Toggle`) | pick many: 3-column 112 dp tiles, icon circle + name, check badge when on (empty ring when off), on = `secondaryContainer` | Customise.kt |
| `OrderedRow` | ⠿ drag handle · 40 dp icon · name · switch, inside `DragColumn`; a hidden row's icon dims to 55%. No up/down arrows anywhere (16) | Customise.kt |
| `DragColumn` | reorder by drag (16): hold the ⠿ handle, the row lifts (`surfaceContainerHigh` + shadow), the others slide to make room; saved on drop; TalkBack gets "Move … up / down" actions | DragList.kt |
| `LetterCircle` | 40 dp neutral circle with one letter (Import: Cashew, Walnut, Money Manager; never brand logos) | Lists.kt |
| `FavouriteChip` | New expense favourites (04): outlined 48 dp pill, 24 dp category circle, "Chai ₹20"; one tap saves | PayeeField.kt |
| `AmountKeypadSheet` | type an exact amount in a sheet (split part, refund, Welcome budget): amount, keypad, ✓; optional maximum | StepperSheet.kt |
| `TxnActions` + `TxnActionsHost` (`LocalTxnActions`) | long-press any `TxnRow` (06): one sheet (Change category, Duplicate to today, Split, Add a refund, Save as favourite, Delete in its own group) and the refund sheet (09), hosted once by the nav host | ui/txn/TxnActions.kt |
| `PickList` | a grouped list with round radio marks on the right (top card 80, numbers and dates 85) | Customise.kt |
| `CheckBadge` | 22 dp primary circle with a check; empty outline ring for "off" | Customise.kt |
| `StatusTile` | More's four tiles: icon circle (category hue), status top-right, title, one line; "on" tiles tinted `secondaryContainer` | Blocks.kt |
| `SwipeRow` (`SwipeAction`) | a row with a swipe each way, set in Customise › Transactions; springs back; TalkBack gets the same as custom actions | SwipeRow.kt |
| `StepDots` / `IntroPanel` / `IntroCard` / `IntroTile` | Welcome and Auto-capture intro: step dots (current = 24 dp pill), the rose panel, tilted white cards | Intro.kt |
| `BurrowLogo` | the Burrow mark on a primary rounded square (Welcome 40 dp, About 112 dp) | Intro.kt |
| `PinScreen` | lock in a cookie, title, four dots, 3 × 4 pad of 72 dp round keys (fingerprint, 0, ⌫) | ui/more/LockScreens.kt |

## Haptics (`Haptics.kt`)
toggle (switch, mark paid) · tick (chip, segment, stepper) · stepTick (slider) · threshold (swipe far
enough) · confirm (save, Done). Long-press keeps the system long-press.

## Gestures
- Bill row: swipe right = Paid (or open it, when Customise › Transactions › Swipe right is Edit), swipe left = Skip (Undo snackbar). Tap = open.
- Review card: swipe right = Save (or open it in New expense when set to Edit), swipe left = Ignore; ✕ ignores; "Save N payments" saves all.
- Activity row: swipe right = Edit or Duplicate, swipe left = Delete, when chosen in Customise › Transactions (off by default).
- Transaction row: long-press = sheet (Duplicate, Change category, Delete).
- Pull down on a sheet closes it; predictive back closes sheets and pops screens.


## Pick list
One-of-many choice with 3+ options or long values (number format, date format). A `ListGroup` of rows:
title = the example value (tabular figures), subtitle = what it is, trailing 24 dp round radio (filled primary + check when on).
Use choice tiles only for 2–3 short options.
