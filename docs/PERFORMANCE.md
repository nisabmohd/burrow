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
- Query only the visible range (paging by month for Activity).
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
