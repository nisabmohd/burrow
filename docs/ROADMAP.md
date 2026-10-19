# Roadmap: day by day

Six weeks to a Play Store beta, about 4–5 focused hours a day. Each day ends with a build that runs,
a commit, and the day's screens checked against their PNGs in light and dark. `TODO.md` is the same
plan as tick boxes.

## Week 0 — setup (1 day)
**Day 0.** New Android Studio project (Empty Activity, Kotlin, Compose) as `app.burrow.money`. Copy
`starter/` over it (gradle, theme, components, capture, data, res, workflows). Copy `.claude/`, `docs/`,
`design/` (PNGs, tokens, logo), `tools/`, `CLAUDE.md`, `TODO.md`, `.gitignore`. Build `playDebug` and
`fossDebug`. Run `RuleEngineTest`. Check the merged manifest has no INTERNET. First commit, push to GitHub.

## Week 1 — the bones: money in and out
- **Day 1. Theme + shell.** `BurrowTheme` from DataStore settings, edge-to-edge, bottom nav (5 tabs,
  fade through), empty tab screens, top bars. Check fonts, colours, dark mode against 04/11/22/36/53.
- **Day 2. Data layer.** Room DAOs (accounts, categories, txns), repositories in `AppContainer`, default
  categories + sample data seeding, `Money.kt` formatting per locale. Unit tests for balances.
- **Day 3. Add expense.** Keypad, Expense/Income/Transfer, category + account sheets, date, payee, note,
  can't-save-yet state, Undo snackbar (17–20).
- **Day 4. Activity.** Day-grouped LazyColumn with keys, search, transaction detail, edit, delete with
  Undo, duplicate (11, 21, 73).
- **Day 5. Today v1.** Hero budget (count-up), Recent, sample-data note, empty state (04, 06, 09).
  Generate the first baseline profile. Measure cold start + APK size.

## Week 2 — the core: auto-capture
- **Day 6. Rules engine.** Finish `RuleEngine`, India pack (`assets/rules/in.json`, 10+ banks/UPI apps)
  with a test per sender. Rule entity + repository.
- **Day 7. SMS + notifications.** `SmsReceiver` (goAsync), `PaymentNotificationListener` (allow-list),
  dedupe, bill match. Capture notification with category buttons (43).
- **Day 8. Intro + Review.** 3-step intro with permissions (45–47), To review with chips, ✕, swipe,
  "Save N payments", All caught up (44).
- **Day 9. Message rules.** Rules screen, rule detail with Test it, Add-rule sheet, import/export file,
  packs on/off (49–51).
- **Day 10. Teach.** Tap-to-build rule from an unread message, save, re-run on waiting messages (52).
  Auto-capture settings screen (48), paused note on Today (07).

## Week 3 — Plan: bills and budgets
- **Day 11. Bills data.** Bill + occurrence generation for repeat rules, reminders (AlarmManager, boot
  receiver), Ask me / Record / Remind on the due day.
- **Day 12. Bills tab.** Overdue note, Next up card, This week / Later rows, swipe Paid/Skip, bill
  detail, new bill + DayStepper (22, 25–28).
- **Day 13. Subscriptions + EMIs.** Subscriptions screen + suggestion note, loan account with EMI
  schedule (`emi()`), prepay (29, 58).
- **Day 14. Budgets.** Monthly budgets (custom month start), category rows with rings, budget detail,
  edit sheet, 80%/100% notifications (23, 30–32, 68).
- **Day 15. Goals.** Goals tab, goal detail, Add money (transfer), goal reached note (24, 33–35).

## Week 4 — Insights and Today widgets
- **Day 16. Filters.** Filter sheet (chips), Dates sheet (calendar + presets), header buttons with badge,
  active chips, Activity chart card with tap-a-day (12–16).
- **Day 17. Insights 1.** Spend chart vs previous period, A day, Biggest, By category, Category insights
  with pill bars (36–37, 42).
- **Day 18. Insights 2.** Patterns widgets, heatmap → Activity for a day, empty range (38–41).
- **Day 19. Today widgets.** This week, Next bill, Net worth, Top category, Goal (wide), Money in and out,
  Edit home sheet (05, 10).
- **Day 20. Accounts.** Accounts list, bank/card/loan/deposit details, fix balance, card Pay bill (55–59).

## Week 5 — settings, backup, polish
- **Day 21. More.** Status tiles, Categories list/grid + edit, Main currency, Month start (53–54, 60–62, 67).
- **Day 22. Appearance + language.** Theme, accent, dynamic colour, font + text size, per-app language
  with Hindi strings (63–65).
- **Day 23. Backup.** Encrypted file backup/restore via SAF, schedule, keep 7, CSV export/import, wrong
  password + replace confirm (69).
- **Day 24. Lock + notifications.** App lock (PIN + biometric), hide amounts, notification settings with
  preview (66, 70–71).
- **Day 25. Motion pass.** Shared axis everywhere, predictive back on sheets, count-ups, bar growth,
  animateItem, Remove-animations check. Welcome + onboarding steps (01–03).

## Week 6 — quality and release
- **Day 26. Responsive.** Medium/expanded layouts (3-column widgets, list–detail Activity, nav rail),
  landscape keypad, 200% font scale pass, RTL check.
- **Day 27. Accessibility + edge cases.** TalkBack labels, chart descriptions, 48 dp audit
  (`tools/smoke.py`), every edge case from SCREENS.md.
- **Day 28. Performance.** Macrobenchmark journeys, regenerate baseline profile, 20k-transaction test DB,
  fix any jank, APK < 5 MB.
- **Day 29. Store.** Screenshots (`tools/store_shots.py`), feature graphic (`design/logo/`), listing text,
  privacy policy (no data collected), Play SMS declaration form, data safety form.
- **Day 30. Release.** CHANGELOG, tag `v0.1.0` → CI builds signed APKs, upload play AAB to closed
  testing, F-Droid metadata.

## Later (after 1.0)
Home-screen widgets (Glance), splits, refunds linking, tags, more country rule packs from the community,
multi-currency rates screen, more languages.
