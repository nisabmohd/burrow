# Motion and transitions

Good Android motion = the system's own patterns, short and consistent. All specs live in
`ui/theme/Motion.kt`; screens never write their own `tween`.

## Navigation
| Change | Transition | Spec |
|---|---|---|
| Push a screen (Today → Accounts, row → detail) | **Shared axis X** | new slides in 30 dp from the end + fades in; old slides 30 dp to the start + fades out. 450 ms emphasized |
| Back / pop | Shared axis X reversed | same timings |
| Back gesture (Android 14+) | **Predictive back** | `android:enableOnBackInvokedCallback="true"`; the NavHost previews the previous screen while dragging; sheets shrink with the gesture |
| Switch bottom tab | **Fade through** | old fades out 90 ms; new fades + scales in from 92%, 210 ms after 90 ms delay. No slide |
| Plan's Bills / Budgets / Goals, filter results | Fade through (96%) | `fadeThrough()` in AnimatedContent |
| Row → its detail (optional, later) | Container transform | shared element on the row surface (`SharedTransitionLayout`), 450 ms |
| Open a sheet | Slide up | Material ModalBottomSheet; scrim fades to 42% |
| Snackbar | Slide up from the nav bar; FAB moves up with it (animate offset) | 300 ms standard |

## Inside a screen
| Thing | Motion |
|---|---|
| Hero amount, centred-header amount | count up once on first show, 600 ms standard. Not on every recomposition, not on scroll back |
| Pill bars | grow from 0 once per data set, 40 ms stagger. Switching selected bar: colour cross-fade 150 ms |
| Rings, progress bars | animate to the new value, 450 ms emphasized decelerate |
| Chips, switches, checkboxes | Material defaults (they already animate) |
| Mark paid | the row's amount fades to "Paid", check scales 0.8 → 1, haptic toggle; row moves to the end after 300 ms |
| Delete / ignore | `animateItem()` in LazyColumn (fade + place), Undo snackbar |
| Notes appearing / leaving | `AnimatedVisibility` expand vertically + fade, 300 ms |
| Capture notification → saved | the row appears at the top of Recent with `animateItem()` |

## Rules
- Nothing loops or bounces for attention. No skeleton shimmer: show nothing until data is ready
  (Room is fast), never a "₹0" flash.
- One motion per kind of change, everywhere (a sheet always slides up, a tab always fades through).
- Respect **Remove animations** (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`): skip count-ups and
  bar growth, jump to final values.
- Animate with `graphicsLayer`/`Modifier.offset { }` lambdas so animation reads state in the
  layout/draw phase, not composition (no recomposition per frame).
- 120 Hz friendly: never animate layout size of a whole list; animate items.
