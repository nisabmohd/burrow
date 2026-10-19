# Icons

**Phosphor Icons** (MIT, phosphoricons.com). Chosen over Material Symbols for a warmer, rounder look (user, 2026-10-19).
All 151 icons the design uses are already converted to vector drawables in `starter/app/src/main/res/drawable/`,
three weights each: `ic_<name>_regular.xml`, `ic_<name>_fill.xml`, `ic_<name>_duotone.xml`.
`isShrinkResources` drops every one the app doesn't reference, so unused files cost nothing in the APK.

## Which weight where
| Where | Weight | Size |
|---|---|---|
| Inside a tonal circle (rows, widgets, notes, headers), widget labels | **duotone** | 22 dp in a 40 dp circle, 36 dp in a 64 dp circle |
| Actions: app-bar buttons, back, close, add, search, filter, calendar, chevrons, chips | **regular** | 24 dp (20 in buttons, 18 in chips) |
| Selected bottom-nav tab, status icons, the check in a selected chip | **fill** | 24 dp |

Tint every icon (`tint =`): the files are white so one drawable serves every colour.
Never mix icon sets. Never draw an icon with emoji. A new icon: take it from Phosphor, convert the same way
(paths only, viewport 256, `fillAlpha` for duotone opacity), name it by meaning (`ic_<meaning>_<weight>`).

## Names (design name → Phosphor icon)
| Name in the design | Phosphor |
|---|---|
| `account_balance` | bank |
| `account_balance_wallet` | wallet |
| `add` | plus |
| `alarm` | alarm |
| `arrow_back` | arrow-left |
| `arrow_drop_down` | caret-down |
| `arrow_forward` | arrow-right |
| `arrow_upward` | arrow-up |
| `arrow_upward_alt` | arrow-up |
| `attach_money` | currency-dollar |
| `autorenew` | arrows-clockwise |
| `backspace` | backspace |
| `backup` | cloud-arrow-up |
| `balance` | scales |
| `bar_chart` | chart-bar |
| `battery_full` | battery-full |
| `bolt` | lightning |
| `bug_report` | bug |
| `calendar_month` | calendar-blank |
| `calendar_view_day` | calendar |
| `calendar_view_month` | squares-four |
| `category` | shapes |
| `celebration` | confetti |
| `check` | check |
| `check_circle` | check-circle |
| `checkroom` | t-shirt |
| `chevron_left` | caret-left |
| `chevron_right` | caret-right |
| `child_care` | baby |
| `close` | x |
| `cloud` | cloud |
| `cloud_done` | cloud-check |
| `cloud_upload` | cloud-arrow-up |
| `code` | code |
| `coffee` | coffee |
| `content_copy` | copy |
| `content_paste` | clipboard-text |
| `credit_card` | credit-card |
| `currency_exchange` | currency-circle-dollar |
| `currency_pound` | currency-gbp |
| `currency_rupee` | currency-inr |
| `dark_mode` | moon |
| `dashboard_customize` | squares-four |
| `date` | calendar |
| `date_range` | calendar-dots |
| `delete` | trash |
| `delete_forever` | trash-simple |
| `description` | file-text |
| `directions_car` | car-profile |
| `donut_large` | chart-donut |
| `drag_indicator` | dots-six-vertical |
| `edit` | pencil-simple |
| `edit_note` | note-pencil |
| `error` | warning-circle |
| `euro` | currency-eur |
| `event` | calendar-check |
| `event_available` | calendar-check |
| `event_busy` | calendar-x |
| `event_repeat` | calendar-dots |
| `expand_more` | caret-down |
| `favorite` | heartbeat |
| `filter_alt_off` | funnel-x |
| `filter_list` | funnel |
| `fingerprint` | fingerprint |
| `fitness_center` | barbell |
| `flag` | flag-banner |
| `flight` | airplane-tilt |
| `folder` | folder |
| `format_list_bulleted` | list-bullets |
| `grid_view` | squares-four |
| `group` | users |
| `handshake` | handshake |
| `help` | question |
| `history` | clock-counter-clockwise |
| `home` | house |
| `info` | info |
| `insights` | sparkle |
| `ios_share` | export |
| `key` | key |
| `laptop_mac` | laptop |
| `light_mode` | sun |
| `lightbulb` | lightbulb |
| `local_gas_station` | gas-pump |
| `local_mall` | shopping-bag |
| `lock` | lock |
| `more_horiz` | dots-three |
| `movie` | film-slate |
| `notes` | note |
| `notifications` | bell |
| `notifications_active` | bell-ringing |
| `notifications_off` | bell-slash |
| `notifications_paused` | bell-z |
| `palette` | palette |
| `pause` | pause |
| `pause_circle` | pause-circle |
| `pause_circle_filled` | pause-circle |
| `payments` | money |
| `percent` | percent |
| `pets` | paw-print |
| `pie` | chart-pie |
| `pie_chart` | chart-pie-slice |
| `pin` | password |
| `play_arrow` | play |
| `public` | globe-hemisphere-east |
| `real_estate_agent` | house-line |
| `receipt_long` | receipt |
| `redeem` | gift |
| `redeem2` | gift |
| `redo` | arrow-u-up-right |
| `remove` | minus |
| `restaurant` | fork-knife |
| `rule` | list-checks |
| `savings` | piggy-bank |
| `schedule` | clock |
| `school` | graduation-cap |
| `science` | flask |
| `search` | magnifying-glass |
| `search_off` | magnifying-glass-minus |
| `settings_backup_restore` | clock-counter-clockwise |
| `shield` | shield-check |
| `shield_check` | shield-check |
| `shopping_cart` | shopping-cart |
| `signal_cellular_4_bar` | cell-signal-full |
| `smartphone` | device-mobile |
| `sms` | chat-text |
| `spa` | flower-lotus |
| `star` | star |
| `storefront` | storefront |
| `summarize` | article |
| `swap_horiz` | arrows-left-right |
| `swap_vert` | arrows-down-up |
| `table` | table |
| `table_view` | table |
| `tag` | hash |
| `task_alt` | check-circle |
| `text_fields` | text-aa |
| `timer` | timer |
| `today` | calendar-dot |
| `translate` | translate |
| `travel` | airplane-tilt |
| `trending_down` | trend-down |
| `trending_up` | trend-up |
| `tune` | funnel-simple |
| `undo` | arrow-counter-clockwise |
| `upload_file` | upload-simple |
| `visibility_off` | eye-slash |
| `volunteer_activism` | hand-heart |
| `wallet` | wallet |
| `wallpaper` | paint-brush |
| `wifi` | wifi-high |
| `wifi_off` | wifi-slash |
