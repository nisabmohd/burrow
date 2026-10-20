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
    check budget alerts and bill match on a device; decide whether net worth counts loans (design shows ₹6.84L, the sample with the home loan is −₹14.47L)
  - [x] Accounts list; bank, card, loan, deposit details; fix balance; Pay bill

## Week 5 — settings, backup, motion
- [ ] **Day 21 · More (53–54, 60–62, 67)**
  - [ ] Status tiles, Categories list / grid + edit, Main currency, Month start
- [ ] **Day 22 · Appearance + language (63–65)**
  - [ ] Theme, accent, dynamic colour, font + text size, per-app language, Hindi strings
- [ ] **Day 23 · Backup (69)**
  - [ ] Encrypted backup / restore via SAF, schedule, keep 7, CSV export / import
  - [ ] Wrong password and replace-data dialogs
- [ ] **Day 24 · Lock + notifications (66, 70–71)**
  - [ ] App lock (PIN + biometric), hide amounts, notification settings with preview
- [ ] **Day 25 · Motion + welcome (01–03)**
  - [ ] Shared axis, predictive back, count-ups, bar growth, animateItem, Remove-animations check
  - [ ] Welcome and the two setup steps

## Week 6 — quality and release
- [ ] **Day 26 · Responsive** — medium / expanded layouts, landscape keypad, 200% font, RTL
- [ ] **Day 27 · Accessibility + edge cases** — TalkBack, chart descriptions, 48 dp audit (`tools/smoke.py`), every edge case
- [ ] **Day 28 · Performance** — Macrobenchmark, baseline profile, 20k-transaction DB, APK < 5 MB
- [ ] **Day 29 · Store** — screenshots (`tools/store_shots.py`), feature graphic, listing, privacy policy, SMS declaration, data safety
- [ ] **Day 30 · Release** — CHANGELOG, tag `v0.1.0`, CI signed APKs, Play closed testing, F-Droid metadata

## Later
- [ ] Home-screen widgets (Glance)
- [ ] Splits and refunds
- [ ] More country rule packs from the community
- [ ] More languages

## Every day
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone
- [ ] No raw colour, size or string in screens; components only
- [ ] Merged manifest has no INTERNET
- [ ] Committed with a clear message
