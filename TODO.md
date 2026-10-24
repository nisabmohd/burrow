# TODO

The plan from `docs/ROADMAP.md` as tick boxes. Tick a box only when the screen matches its PNG in
light and dark, builds, and is committed. Screen numbers point to `design/screens/`.

## Week 0 — setup
- [ ] **Day 0 · Project**
  - [x] New Android Studio project (Kotlin, Compose) with package `app.burrow.money`
  - [x] Copy `starter/` over it; copy `.claude/`, `docs/`, `design/`, `tools/`, `CLAUDE.md`, `TODO.md`, `.gitignore`
  - [x] Add a `:baselineprofile` module (Android Studio › New Module › Baseline Profile Generator)
  - [x] `./gradlew :app:assemblePlayDebug :app:assembleFossDebug` builds
  - [x] `./gradlew :app:testFossDebugUnitTest` (RuleEngineTest) passes
  - [x] Merged manifest has no INTERNET permission
  - [ ] First commit, push to GitHub, CI green (committed locally; push once the GitHub repo exists)

## Week 1 — money in and out
- [x] **Day 1 · Theme + shell**
  - [x] `BurrowTheme` reading ThemeSettings from DataStore
  - [x] Edge-to-edge, bottom nav with 5 tabs (fade through), empty tab screens, top bars
  - [x] Fonts, colours, dark mode checked against 04 / 11 / 22 / 36 / 53
- [x] **Day 2 · Data**
  - [x] DAOs for accounts, categories, transactions; repositories in `AppContainer`
  - [x] Default categories + sample data (flagged `isSample`)
  - [x] Money formatting per locale; balance unit tests
- [x] **Day 3 · Add expense (17–20)**
  - [x] Keypad, Expense / Income / Transfer
  - [x] Category and account sheets, date, payee, note
  - [ ] Date sheet: future date = upcoming (78)
  - [ ] Extras chips: Note + payee + tags, Repeat (creates a bill), Attach (app storage), Lend or borrow (74–79); Paid to field with payee suggestions (87)
  - [x] Can't-save-yet state, Undo snackbar
- [x] **Day 4 · Activity (11, 21, 73)**
  - [ ] Later: long-press row sheet (Duplicate, Change category, Delete); "Recorded from SMS" box (Day 7); paging by month (Day 28)
  - [x] Day groups in a keyed LazyColumn, search
  - [x] Transaction detail, edit, delete with Undo, duplicate
- [x] **Day 5 · Today v1 (04, 06, 09)**
  - [ ] Later: replace raw dp inside the starter components (Blocks, Charts, Controls, Lists) with Dimens tokens
  - [x] Hero budget with count-up, Recent
  - [x] Sample-data note, empty state
  - [x] First baseline profile; measure cold start and APK size (play release 1.9 MB; cold start ~120 ms on the API 35 emulator)

## Week 2 — auto-capture
- [x] **Day 6 · Rules engine**
  - [x] India pack `assets/rules/in.json` (10+ banks and UPI apps), a test per sender
  - [x] Rule entity + repository, packs on/off
- [x] **Day 7 · SMS + notifications (43)**
  - [x] `SmsReceiver` with goAsync, `PaymentNotificationListener` with the allow-list
  - [x] Dedupe (10 min)
  - [x] Bill match (±10%, 3 days): needs bills, do it on Day 11
  - [x] Capture notification with two category buttons + Ignore
- [x] **Day 8 · Intro + Review (44–47)**
  - [x] 3-step intro with permission requests
  - [x] To review: chips, ✕, swipe, "Save N payments", All caught up
- [x] **Day 9 · Message rules (49–51)**
  - [x] Rules list, rule detail with Test it, Add-rule sheet
  - [x] Import / export rules file (validated regexes)
- [x] **Day 10 · Teach (48, 52, 07)**
  - [ ] Later: payee names for UPI ids ("thirdwave@okicici" → "Third Wave Coffee", payee aliases); rename a rule (the pencil on 50); more country packs (UAE, UK); Hindi strings for week 2
  - [x] Tap-to-build rule, re-run on waiting messages
  - [x] Auto-capture settings screen, paused note on Today

## Week 3 — Plan
- [x] **Day 11 · Bills data**
  - [x] Occurrences from repeat rules; reminders with AlarmManager + boot receiver
  - [x] Ask me / Record it / Only remind on the due day
- [x] **Day 12 · Bills tab (22, 25–28)**
  - [x] Overdue note, Next up card, week lists, swipe Paid / Skip
  - [x] Bill detail, New bill, DayStepper sheet
- [x] **Day 13 · Subscriptions + EMIs (29, 58)**
  - [x] Subscriptions screen + suggestion note
  - [x] Loan account with EMI schedule, prepay
- [x] **Day 14 · Budgets (23, 30–32, 68)**
  - [x] Month start day, category rows with rings, budget detail, edit sheet
  - [x] 80% / 100% notifications
- [x] **Day 15 · Goals (24, 33–35)**
  - [x] Goals tab, goal detail, Add money (transfer), Goal reached note

## Week 4 — Insights and widgets
- [x] **Day 16 · Filters (12–16)**
  - [x] Filter sheet (chips), Dates sheet, header buttons with badge, active chips
  - [x] Activity chart card with tap-a-day
- [x] **Day 17 · Insights 1 (36–37, 42)**
  - [x] Spend chart vs previous period, A day, Biggest, By category, Category insights
- [x] **Day 18 · Insights 2 (38–41)**
  - [x] Patterns widgets, heatmap → Activity for a day, empty range
- [x] **Day 19 · Today widgets (05, 10)**
  - [x] This week, Next bill, Net worth, Top category, Goal (wide), Money in and out, Edit home
- [x] **Day 20 · Accounts (55–59)**
  - [ ] Later (weeks 3–4): weekly / yearly repeat when creating a bill (monthly only today; edit keeps yearly); the deposit's
    "Remind me a week before" switch is stored but no maturity reminder is scheduled yet; Month starts on sheet (68) opens from More (Day 21);
    check budget alerts and bill match on a device; net worth: decided by the 2026-10-20 kit, cards and loans are off by default (see Design update)
  - [x] Accounts list; bank, card, loan, deposit details; fix balance; Pay bill

## Week 5 — settings, backup, motion
- [x] **Day 21 · More + customise (53–54, 60–62, 67, 80–86)**
  - [x] Status tiles, Categories list / grid + edit (New category FAB, counts), Month start (now in Region)
  - [x] More v2 (53, 54): Money = Accounts, Categories; Customise group; App group
  - [x] Customise: one screen, tabs Today · Expense · Transactions · Region · Currency; blocks ChoiceTiles,
    ToggleTiles, OrderedRow, PickList, switch rows (in COMPONENTS.md)
    - [x] Today (80): top card, widget order + show/hide, recent count, To review note
    - [x] Expense (81): extras, opens as, default account
    - [x] Transactions (82): when a payment is found (Ask / Open to edit first / Save it for me), lists, swipes
  - [x] Currencies with typed-in rates (83, 84), Language and region (85): numbers, dates, week start, month start
  - [x] Count in net worth per account (86); Lent and borrowed list with Settle up
- [x] **Day 22 · Appearance + language (63–65)**
  - [x] Theme, accent, dynamic colour, font + text size, per-app language
  - [x] Hindi strings (values-hi, every string; placeholders checked); locale config generated by Gradle
- [x] **Day 23 · Backup (69)**
  - [x] Encrypted backup / restore via SAF (now with people, tags, attachments, rates), schedule, keep 7, CSV
  - [x] Wrong password and replace-data dialogs
  - [ ] Check a real backup → restore round trip and CSV export / import on a device (system pickers)
- [x] **Day 24 · Lock + notifications (66, 70–71)**
  - [x] App lock (PIN + biometric), hide amounts, notification settings with preview, monthly summary
- [x] **Day 25 · Motion + welcome (01–03)**
  - [x] Every animation spec in Motion.kt (bar growth moved there); shared axis, fade through, count-up
  - [x] Welcome and the two setup steps

### Design update (kit of 2026-10-20) — changes to screens already built
- [x] Data: Room v4 (AutoMigration, cards/loans out of net worth): includeInNetWorth, status, personId, lend;
  person, attachment, tag, txn_tag, rate tables; backups include them
- [x] New expense (17, 74–79, 87): Paid to + suggestions, extras chips and sheets, upcoming dates, time picker
- [x] Today widgets (05): order from Customise; net worth leaves out cards and loans
  (Burrow still counts money set aside in goals: ₹7.01L with the sample, the design shows ₹3.61L)
- [x] Plan › Bills (22): subscription icons, EMI progress, section totals, Upcoming section
- [x] Auto-capture (48): "When a payment is found" row
- [x] Accounts (55): "excludes …" line; Categories (60, 61): FAB and counts
- [x] Capture notification (43): Burrow mark small icon

## Week 6 — quality and release
- [x] **Day 26 · Responsive** — rail from 840 dp, content max 640 dp (900 dp on a landscape phone), side
  insets, landscape keypad beside the form, 200% text (rows wrap, notes stack, buttons shrink), RTL-safe
  layout and auto-mirrored arrows
  - [ ] Look at RTL on a device with an RTL language (the emulator falls back to English)
- [x] **Day 27 · Accessibility + edge cases** — `tools/smoke.py` incl. 48 dp audit; swipe actions also as
  TalkBack actions; fixed: Undo lost when deleting without the dialog
- [ ] **Day 28 · Performance** — release APK 2.66 MB ✓; regenerate the baseline profile; 20k-transaction test
- [ ] **Day 29 · Store** — listing, PRIVACY.md, data safety, SMS declaration, fastlane metadata ✓;
  screenshots with `tools/store_shots.py` still to take
- [ ] **Day 30 · Release** — CHANGELOG ✓, local test builds in build/dist/v0.1.0 (debug key) ✓;
  tag `v0.1.0`, CI signed APKs, Play closed testing, F-Droid MR: **waiting for you** (public, can't undo)

## Later
- [ ] Home-screen widgets (Glance)
- [ ] Splits and refunds
- [ ] More country rule packs from the community
- [ ] More languages

## Every day
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone
- [ ] No raw colour, size or string in screens; components only
- [ ] play and foss merged manifests have no INTERNET (github: only the update check)
- [ ] Committed with a clear message

## Next round (burrow-next, 2026-10-22) — `docs/NEXT.md`, PNGs in `design/next/`
- [x] **Charts and type**
  - [x] Fix charts: donut caps overlapped (Top category), stacked bar overflowed with small shares (By category), ring caps touched at small sizes, labels collided ("21 22"), clipped labels on dense charts, line strokes cut off, average rounded down
  - [x] Fonts drawn a step heavier to match the PNGs (variable Lexend / Outfit / Atkinson)
- [x] **Add**
  - [x] Home-screen widget 4×2 Safe to spend today + 2×2 Add expense, Next bill (02)
  - [x] App shortcuts: New expense, New income, Review N payments, Search (02)
  - [x] Quick settings tile Add expense (03)
  - [x] Favourites on New expense, Favourite chip, Favourites screen with drag and remove (04, 05)
  - [x] Import: Cashew, Walnut, Money Manager, any spreadsheet; check screen; duplicates; remembered mappings (10, 11)
  - [x] Split across categories and refunds (07–09)
- [x] **Improve**
  - [x] Welcome step 2 of 3: monthly budget (12)
  - [x] Sunday review notification and screen; switch in Notifications (13, 14, 17)
  - [x] Trust in capture: "92% recorded by itself", Teach row with senders, More tile (15)
  - [x] Long-press any transaction row (06)
  - [x] Customise reorders by drag, no arrows (16); Customise Today button gone from Today (01)
- [x] Smoke suite: a check per feature with edge cases (51 checks; crash log read after each)

## 2026-10-24 — phone feedback round
- [x] Same pills for every tab row: Plan and Customise use `ButtonGroup` / `ScrollingButtonGroup`, label centred, no check
- [x] Choice tiles: check on the title line only; the value shrinks to fit ("1,240.00")
- [x] Auto-capture: switches show on only with Android access; other options hidden while off; Restricted settings note with App info
- [x] New goal icons in the shared swatch grid; Activity "All days" not clipped; small chart days are round dots
- [x] Haptics fall back to classic types on phones without vibration primitives; tabs and swatches tick
- [x] Launcher mark a step bigger (0.62 → 0.70)
- [x] GitHub build: check for updates (About + once a day), download, verify SHA-256, install (as Sprout)
- [x] Wording: "never sends your data anywhere", not "no internet" (app strings in all languages, docs, listing, store shot 08)
- [x] Store shot 08 re-rendered with the new caption (`tools/store_shots.py` now skips raws it doesn't have)
- [ ] **Google Drive backup**: encrypted backup to the user's own Drive, alongside the SAF folder backup. Decide the
      approach first (needs INTERNET + Google sign-in in play; foss/F-Droid can't use Google code). Update PRIVACY.md,
      data safety form and the About line when it lands.
- [ ] **Rate on Play Store** (play flavour): turn on `PLAY_LISTED` once the listing is live; About row is already wired.
      Consider a one-time gentle ask after a few weeks of use (one sheet, never again after "No thanks").
- [ ] Play Protect / sideload blocking: register the app in Android developer verification and submit the github/foss
      APKs to the Play Protect appeals form; point README install steps at F-Droid / Obtainium or Play.
