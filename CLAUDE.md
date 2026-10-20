# Burrow — project rules

Burrow is an open-source Android expense tracker. It records payments by itself from bank messages,
reminds you of bills and shows what is left. **It never goes online.**
Package `app.burrow.money` · GNU GPL v3 · Play title "Burrow: Expense Tracker".

Read before working: `docs/DESIGN.md` (rules), `docs/TOKENS.md`, `docs/COMPONENTS.md`,
`docs/SCREENS.md` (with `design/screens/*.png`), `docs/MOTION.md`, `docs/PERFORMANCE.md`,
`docs/CAPTURE.md`, `docs/DATA.md`, `docs/COPY.md`, `docs/ICONS.md`. Today's work: `TODO.md`.
The PNGs are the source of truth for layout, sizes and colours. `design/prototype/burrow-prototype.html`
(not in git) is the clickable version.

## What the app is (USP, in order)
1. **It records itself.** A payment message asks for a category; one tap saves it; a payment matching a
   due bill marks it Paid. Message rules make it work for any bank, region and language.
2. **Private by design.** No INTERNET permission. No account, no sync, no AI, no analytics.
3. **One right way to do each thing.** Few settings, calm design, small and fast.

Not in the app: email parsing, OCR, voice, AI, cloud sync, shared household, live exchange rates,
investments with live prices, tax module, document vault, multiple ledgers.

## Hard rules
- **No INTERNET permission**, ever. No library that needs one. Check the merged manifest after any dependency.
- **Ask before adding any dependency.** No DI framework (one `AppContainer`), no chart library (Canvas),
  no image library, no networking, no analytics, no WorkManager, no `material-icons-extended`.
- **Money is `Long` minor units.** Never Float/Double. Interest maths in BigDecimal, round once, HALF_EVEN.
- All user-facing text in `strings.xml` (plurals for counts). No raw colour outside `Color.kt`, no raw
  text size/weight outside `Type.kt`, no raw spacing/size/shape outside `Dimens.kt`, no `tween` outside `Motion.kt`.
- Screens use only the components in `docs/COMPONENTS.md`. Missing one? Add it there first.
- Pickers, filters and choices are bottom sheets (`BurrowSheet`). Dialogs only confirm delete, replace data, wrong password.
- Icons: Phosphor vector drawables only (`ic_<name>_<regular|fill|duotone>`), see `docs/ICONS.md`.
- Release: R8 full, minify + shrink resources, baseline profile committed. APK < 5 MB.
- Build and run after every change; one commit per working step. Never `./gradlew clean` bare: use `:app:clean`.
- Release files go in `build/dist/v<version>/`.

## Design rules (short form of docs/DESIGN.md)
- Rose 345 seed, Lexend, two weights (400/500), six text styles, 4 dp grid, 16 dp screen margin.
- **One shape rule:** 24 dp surfaces; circles/pills for everything small or tappable (round checkboxes too).
- **One hero** (Today). Everything else opens with the **centred header**.
- **One signal per number.** **No buttons inside cards.** **Notes go first**, one `Note` component.
- **Filters** = funnel + calendar header buttons → one chip Filter sheet (fits a screen) + Dates sheet.
- Every day choice is the **DayStepper** sheet. Settings screens are plain lists.
- Charts: pill bars + dashed average, rings with a gap, circle heatmap. Insights keeps everything on one page.
- Half widgets 184 dp fixed; a lone widget goes wide (text left, 104 dp ring right).
- Amounts never coloured except "₹X over" in red. Red only for overdue / over budget / errors.
- Motion: shared axis X for push/pop, fade through for tabs, predictive back, count-up once. See MOTION.md.

## Stack
minSdk 26, targetSdk 36, Kotlin 2.x, KSP, version catalog. Compose + Material 3. Room (Flow DAOs),
DataStore Preferences, Navigation Compose (type-safe routes), ViewModel + StateFlow, kotlinx-serialization
(rules files, backup JSON). Capture: `RECEIVE_SMS`/`READ_SMS` (foss/github; play after the declaration)
+ `NotificationListenerService`. Reminders: AlarmManager + boot receiver. Backup: encrypted file via SAF.
Flavors: `play`, `foss` (F-Droid, no Google code), `github` (APK). Fonts: Lexend, Outfit, Atkinson (static TTF), Space Grotesk (variable TTF, drawn at 470/600).

## Behaviour rules
- New expense: amount, then a **Paid to** free-text field (any text; suggestion chips of frequent payees filter as you type and set the usual category), then Category, Account, Date + keypad; extras as chips (Note, Repeat, Attach, Lend or borrow), chosen in
  More › Customise › New expense form. Future date = upcoming. Repeat creates a bill. Attachments stay on the phone.
- **Customise** is one screen with tabs Today · Expense · Transactions · Region · Currency, built only from choice tiles,
  toggle tiles, ordered rows (show/hide + ↑↓) and switch rows. Every new customisation goes there.
- Every account has "Count in net worth"; credit cards and loans are off by default.
- Transfers never count as spending/income (card bill, ATM, EMI principal, savings, Add money).
- Bills: one row per occurrence; swipe right Paid, left Skip; a past one stays "Not paid yet" until marked.
- Budgets monthly from the user's month-start day; 80% and 100% notifications once each per month;
  "₹X a day" = left ÷ days left including today.
- Auto-capture: user rules first, then packs; drop OTP/offers/failed; dedupe 10 min; bill match ±10% / 3 days;
  message text deleted after 30 days.
- Undo snackbar after every change (~4 s); FAB moves up with it.
- First run: Welcome → currency → accounts → Today with sample data + note; samples go on the first real entry.
- Nothing is drawn under a header until its data has loaded (no "₹0" flash).
- Filters stay while the app is open, reset on restart.
- Notifications: app name + time, title, one line, no buttons (except the capture notification).
  Small icon is always the Burrow mark, `R.drawable.ic_notification` (user, 2026-10-20), never a Phosphor glyph.

## Working with Claude
- Start each session with `TODO.md`: pick the next unticked day, build it screen by screen against its PNGs.
- Skills in `.claude/skills/`: `build-screen`, `add-feature`, `capture-rules`, `verify-app`, `release`.
- After each screen: run, compare with the PNG (light + dark), fix, commit, tick the box.
