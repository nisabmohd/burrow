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
- [ ] **Day 1 · Theme + shell**
  - [ ] `BurrowTheme` reading ThemeSettings from DataStore
  - [ ] Edge-to-edge, bottom nav with 5 tabs (fade through), empty tab screens, top bars
  - [ ] Fonts, colours, dark mode checked against 04 / 11 / 22 / 36 / 53
- [ ] **Day 2 · Data**
  - [ ] DAOs for accounts, categories, transactions; repositories in `AppContainer`
  - [ ] Default categories + sample data (flagged `isSample`)
  - [ ] Money formatting per locale; balance unit tests
- [ ] **Day 3 · Add expense (17–20)**
  - [ ] Keypad, Expense / Income / Transfer
  - [ ] Category and account sheets, date, payee, note
  - [ ] Can't-save-yet state, Undo snackbar
- [ ] **Day 4 · Activity (11, 21, 73)**
  - [ ] Day groups in a keyed LazyColumn, search
  - [ ] Transaction detail, edit, delete with Undo, duplicate
- [ ] **Day 5 · Today v1 (04, 06, 09)**
  - [ ] Hero budget with count-up, Recent
  - [ ] Sample-data note, empty state
  - [ ] First baseline profile; measure cold start and APK size

## Week 2 — auto-capture
- [ ] **Day 6 · Rules engine**
  - [ ] India pack `assets/rules/in.json` (10+ banks and UPI apps), a test per sender
  - [ ] Rule entity + repository, packs on/off
- [ ] **Day 7 · SMS + notifications (43)**
  - [ ] `SmsReceiver` with goAsync, `PaymentNotificationListener` with the allow-list
  - [ ] Dedupe (10 min), bill match (±10%, 3 days)
  - [ ] Capture notification with two category buttons + Ignore
- [ ] **Day 8 · Intro + Review (44–47)**
  - [ ] 3-step intro with permission requests
  - [ ] To review: chips, ✕, swipe, "Save N payments", All caught up
- [ ] **Day 9 · Message rules (49–51)**
  - [ ] Rules list, rule detail with Test it, Add-rule sheet
  - [ ] Import / export rules file (validated regexes)
- [ ] **Day 10 · Teach (48, 52, 07)**
  - [ ] Tap-to-build rule, re-run on waiting messages
  - [ ] Auto-capture settings screen, paused note on Today

## Week 3 — Plan
- [ ] **Day 11 · Bills data**
  - [ ] Occurrences from repeat rules; reminders with AlarmManager + boot receiver
  - [ ] Ask me / Record it / Only remind on the due day
- [ ] **Day 12 · Bills tab (22, 25–28)**
  - [ ] Overdue note, Next up card, week lists, swipe Paid / Skip
  - [ ] Bill detail, New bill, DayStepper sheet
- [ ] **Day 13 · Subscriptions + EMIs (29, 58)**
  - [ ] Subscriptions screen + suggestion note
  - [ ] Loan account with EMI schedule, prepay
- [ ] **Day 14 · Budgets (23, 30–32, 68)**
  - [ ] Month start day, category rows with rings, budget detail, edit sheet
  - [ ] 80% / 100% notifications
- [ ] **Day 15 · Goals (24, 33–35)**
  - [ ] Goals tab, goal detail, Add money (transfer), Goal reached note

## Week 4 — Insights and widgets
- [ ] **Day 16 · Filters (12–16)**
  - [ ] Filter sheet (chips), Dates sheet, header buttons with badge, active chips
  - [ ] Activity chart card with tap-a-day
- [ ] **Day 17 · Insights 1 (36–37, 42)**
  - [ ] Spend chart vs previous period, A day, Biggest, By category, Category insights
- [ ] **Day 18 · Insights 2 (38–41)**
  - [ ] Patterns widgets, heatmap → Activity for a day, empty range
- [ ] **Day 19 · Today widgets (05, 10)**
  - [ ] This week, Next bill, Net worth, Top category, Goal (wide), Money in and out, Edit home
- [ ] **Day 20 · Accounts (55–59)**
  - [ ] Accounts list; bank, card, loan, deposit details; fix balance; Pay bill

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
