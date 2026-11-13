# Size, speed and responsiveness

Burrow must feel instant on a ₹8,000 phone and stay tiny.

## Budgets (fail the release if missed)
| Metric | Budget | How to check |
|---|---|---|
| APK (play, release, universal) | **< 5 MB** (aim 3.5) | `ls -la app/build/outputs/apk/play/release/` |
| Cold start to first frame | < 500 ms on a mid phone, < 900 ms on a low-end one | `adb shell am start -W -n app.burrow.money/.MainActivity` (TotalTime) |
| Scroll jank | 0 frames over 16 ms in Activity with 2,000 transactions | Macrobenchmark `FrameTimingMetric`, or Profile GPU rendering |
| Room query for a month | < 30 ms with 20,000 transactions | Log in debug |
| Memory | < 120 MB PSS on Today | Android Studio profiler |

## What keeps it small
- R8 full mode, `isMinifyEnabled`, `isShrinkResources` (unused icons drop out), locale filters (en, hi),
  packaging excludes, no dependency-info block. Settings already in `starter/app/build.gradle.kts`.
- **No libraries** beyond AndroidX + kotlinx-serialization: no DI framework, no chart library, no image
  library, no networking, no Firebase/analytics, no `material-icons-extended`. Ask before adding any.
- Fonts: static TTFs, two weights each, plus one variable Space Grotesk TTF (≈ 540 KB for all four families). Drop a family if size gets tight.
- Vector drawables only; no PNGs in the app.

## What keeps it fast
- **Baseline profile** (`:baselineprofile` module, Macrobenchmark journey: open → Today → Activity
  scroll → Insights → add expense). Regenerate before each release and commit it. Startup profile too.
- Nothing heavy in `Application.onCreate`; `AppContainer` is all `lazy`.
- Room on `Dispatchers.IO`; totals computed in SQL (`SUM`, `GROUP BY`), not in Kotlin over lists.
  Index `at`, `accountId`, `categoryId`, `dueDate` (already in the schema).
- Query only the visible range. No screen reads every row (only the CSV export does; `ProjectRulesTest` checks):
  - **Activity** browsing reads 3 months, then 3 more when the list is 30 rows from its end (`ACTIVITY_MONTHS`,
    `ACTIVITY_LOAD_AHEAD`). Picked dates read just those days; a search or filter reads all history so nothing is
    missed. The Filter sheet's count runs over `postedKeys` (5 columns per row), not full rows.
  - **Account pages** read their own rows (`forAccount`, paid from it or moved into it, ≤ 1,000) and show the last
    30 days with payments; a card's "since statement" is a SQL `SUM` (`expenseSince`). Before 0.4.1 they filtered the
    newest 400 rows of every account, so a quiet card lost its history.
  - **Today** reads from 7 weeks back (net-worth line) or the budget month's start; the **Sunday review** 21 days;
    **Insights** the picked range, the one before and the heatmap month.
- Every `Flow<List<TxnView>>` query has a `WHERE` or `LIMIT`; `TxnEntity` is indexed on `at`, `accountId`,
  `toAccountId` (Room v10), `categoryId`, `refundOf`.
- Lists that grow with use are lazy: Activity, Today, and the account, budget and category-insights pages
  (`LazyDetailFrame`, one item per day header and per day group). `TxnRow` outside a lazy item fails `ProjectRulesTest`.
- `LongHistoryTest` (androidTest, in-memory Room): 20,000 rows over 3 years, 6 accounts, a quiet card. Budgets: month
  query < 30 ms, account page < 60 ms, filter keys < 150 ms (emulator, 12 Nov: 1 / 6 / 10 ms).
- Compose: stable keys in every `LazyColumn` (`key = { it.id }`), `contentType` for headers vs rows,
  immutable UI state (`@Immutable` data classes, `ImmutableList`-like `List` from `toList()`), lambdas
  that read state late (`Modifier.offset { }`), `derivedStateOf` for "scrolled" flags, no `remember`
  of whole lists in rows.
- Charts draw in one `Canvas`, no per-bar composables in big lists.
- Strong skipping is on by default (Kotlin 2.x): keep parameters stable so rows skip.
- Formatting (`NumberFormat`, `DateTimeFormatter`) cached per locale, not created per row.

## Responsive layouts
- Phone portrait is the design (412 dp). Must work from 320 dp to tablets/foldables.
- `WindowSizeClass`: compact = as designed; medium (≥ 600 dp) = widget grid 3 columns, sheets max 640 dp
  wide centred, detail screens max 640 dp content width; expanded (≥ 840 dp) = list–detail
  (Activity list + transaction detail side by side) with `ListDetailPaneScaffold`, nav rail instead of bar.
- Landscape phone: same as medium, keypad moves beside the amount.
- Edge-to-edge always (`enableEdgeToEdge()`), insets via `WindowInsets.safeDrawing`; IME padding on forms.
- Font scale up to 200%: rows grow in height, never clip; widgets keep 184 dp but their text may wrap
  to two lines then ellipsize.

## Battery
- No polling, no background services except the NotificationListener the user enables.
- SMS handled in a `BroadcastReceiver` with `goAsync()`, done in < 1 s.
- Reminders: `AlarmManager.setAndAllowWhileIdle` (inexact window is fine for 9 AM); re-scheduled on boot,
  time change and edit. No exact-alarm permission needed.
- Scheduled backup: WorkManager is NOT used (dependency); use one inexact alarm per day that writes the
  file via the saved SAF folder URI.
