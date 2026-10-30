# Icons

**Solar**, Linear (outline) style, by 480 Design (CC BY 4.0, Figma community file, npm `@iconify-json/solar`).
Chosen over Hugeicons; outline only, no duotone, so every icon looks the same (user, 2026-10-31). CC BY needs credit: the licence text with the credit line is
`res/raw/licence_solar_cc_by.txt`, shown in About › Licences as "Solar icons by 480 Design". Keep that row.

Every icon is a vector drawable in `app/src/main/res/drawable/`, named by meaning: `ic_<meaning>_<weight>.xml`
with weights `regular`, `fill`, `duotone`. The app uses **one style**, Linear (1.5 strokes, round caps, viewport 24),
so the three files are the same outline icon. The weight names stay so screens pick a weight by role; the selected
or tinted state comes from the container (the nav pill, the tonal circle), never from the glyph. Never use Solar's
Bold, Duotone or Broken styles. `isShrinkResources` drops every file the app doesn't reference.

Glyphs that sit inside a circle or badge (check, add, close, remove) use Solar's bare shape, not the `-circle` one.
Pick the most specific glyph for the meaning (a safe for a bank account, a calculator for tax), never a generic shape.

## Which weight where (kept by role)
| Where | Weight | Size |
|---|---|---|
| Inside a tonal circle (rows, widgets, notes, headers), widget labels | **duotone** | 22 dp in a 40 dp circle, 36 dp in a 64 dp circle |
| Actions: app-bar buttons, back, close, add, search, filter, calendar, chevrons, chips | **regular** | 24 dp (20 in buttons, 18 in chips) |
| Selected bottom-nav tab, status icons, the check in a selected chip | **fill** | 24 dp |

Tint every icon (`tint =`): the files are white so one drawable serves every colour.
Never mix icon sets. Never draw an icon with emoji.

## Adding or changing an icon
The map meaning → Solar name lives in `ICONS` in `tools/solar_icon.py` (single source; the table below
is a copy). Add a line there, then:
```sh
npm pack @iconify-json/solar && tar xzf iconify-json-solar-*.tgz   # anywhere outside the repo
python3 tools/solar_icon.py <path>/package <meaning>        # one icon, all three weights
python3 tools/solar_icon.py <path>/package                  # regenerate every icon
```
Browse names at icon-sets.iconify.design/solar; give the name without the style suffix. The script says when
a name is missing in Linear or uses SVG features it can't convert.
Solar has no rupee or pound glyph: those use banknotes.

## Names (meaning → Solar)
| Meaning | Solar |
|---|---|
| `account_balance` | safe-square |
| `account_balance_wallet` | wallet |
| `add` | add |
| `alarm` | alarm |
| `apartment` | buildings |
| `arrow_back` | arrow-left |
| `arrow_downward` | arrow-down |
| `arrow_drop_down` | alt-arrow-down |
| `arrow_forward` | arrow-right |
| `arrow_upward` | arrow-up |
| `arrow_upward_alt` | arrow-up |
| `attach_file` | paperclip |
| `attach_money` | dollar |
| `autorenew` | refresh |
| `backspace` | backspace |
| `backup` | cloud-upload |
| `balance` | scale |
| `bar_chart` | chart-2 |
| `battery_full` | battery-full |
| `block` | forbidden-circle |
| `bolt` | bolt |
| `books` | book-2 |
| `bug_report` | bug |
| `bus` | bus |
| `calendar_month` | calendar |
| `calendar_view_day` | calendar-minimalistic |
| `calendar_view_month` | calendar-date |
| `call_split` | branching-paths-down |
| `category` | widget |
| `celebration` | confetti |
| `check` | check |
| `check_circle` | check-circle |
| `checkroom` | t-shirt |
| `chevron_left` | alt-arrow-left |
| `chevron_right` | alt-arrow-right |
| `child_care` | balloon |
| `close` | close |
| `cloud` | cloud |
| `cloud_done` | cloud-check |
| `cloud_upload` | cloud-upload |
| `code` | code |
| `coffee` | cup-hot |
| `content_copy` | copy |
| `content_paste` | clipboard-text |
| `cooking_gas` | fire |
| `credit_card` | card |
| `currency_exchange` | round-transfer-horizontal |
| `currency_pound` | banknote-2 |
| `currency_rupee` | banknote |
| `dark_mode` | moon |
| `dashboard_customize` | widget-add |
| `date` | calendar-minimalistic |
| `date_range` | calendar-date |
| `delete` | trash-bin-minimalistic |
| `delete_forever` | trash-bin-trash |
| `delivery` | delivery |
| `description` | document-text |
| `directions_car` | car |
| `doctor` | stethoscope |
| `donut_large` | pie-chart-3 |
| `download` | download-minimalistic |
| `drag_indicator` | reorder |
| `edit` | pen-2 |
| `edit_note` | pen-new-square |
| `envelope` | letter |
| `error` | danger-circle |
| `euro` | euro |
| `event` | calendar-mark |
| `event_available` | calendar-mark |
| `event_busy` | calendar |
| `event_repeat` | repeat |
| `event_upcoming` | calendar-date |
| `expand_less` | alt-arrow-up |
| `expand_more` | alt-arrow-down |
| `export` | export |
| `fastfood` | donut-bitten |
| `favorite` | heart |
| `filter_alt_off` | filter |
| `filter_list` | filter |
| `fingerprint` | shield-keyhole |
| `fitness_center` | dumbbell-large-minimalistic |
| `flag` | flag |
| `flight` | plane |
| `folder` | folder |
| `format_list_bulleted` | list |
| `gadgets` | headphones-round |
| `games` | gamepad |
| `gold` | medal-ribbons-star |
| `grid_view` | widget |
| `group` | users-group-rounded |
| `hand_tap` | cursor |
| `handshake` | hand-shake |
| `help` | question-circle |
| `history` | history |
| `home` | home-2 |
| `hospital` | medical-kit |
| `hotel` | bed |
| `house_help` | broom |
| `image` | gallery |
| `import` | import |
| `info` | info-circle |
| `insights` | chart-square |
| `insurance` | umbrella |
| `investment` | graph-new-up |
| `ios_share` | share |
| `jewellery` | crown-star |
| `key` | key |
| `laptop_mac` | laptop |
| `light_mode` | sun |
| `lightbulb` | lightbulb |
| `loan_emi` | hand-money |
| `local_bar` | wineglass |
| `local_gas_station` | gas-station |
| `local_mall` | bag |
| `lock` | lock-keyhole |
| `medicine` | pill |
| `more_horiz` | menu-dots |
| `movie` | clapperboard-play |
| `notes` | notes |
| `notifications` | bell |
| `notifications_active` | bell-ring |
| `notifications_off` | bell-off |
| `notifications_paused` | alarm-sleep |
| `palette` | palette |
| `pause` | pause |
| `pause_circle` | pause-circle |
| `pause_circle_filled` | pause-circle |
| `payments` | wallet-money |
| `percent` | sale |
| `person` | user |
| `pets` | paw |
| `photo_camera` | camera |
| `pie` | pie-chart-2 |
| `pie_chart` | pie-chart-2 |
| `pin` | password |
| `plants` | leaf |
| `play_arrow` | play |
| `public` | global |
| `real_estate_agent` | home-angle |
| `receipt_long` | bill-list |
| `redeem` | gift |
| `redeem2` | ticket-sale |
| `redo` | undo-right |
| `refund` | undo-left-round |
| `remove` | minus |
| `repairs` | sledgehammer |
| `restaurant` | chef-hat-minimalistic |
| `rule` | checklist |
| `salon` | scissors |
| `savings` | money-bag |
| `scales` | scale |
| `schedule` | clock-circle |
| `school` | square-academic-cap |
| `science` | test-tube |
| `search` | magnifier |
| `search_off` | magnifier-slash |
| `settings_backup_restore` | restart |
| `shield` | shield |
| `shield_check` | shield-check |
| `shoes` | running-2 |
| `shopping_cart` | cart-large-2 |
| `signal_cellular_4_bar` | sim-card |
| `smartphone` | smartphone |
| `sms` | chat-round-dots |
| `spa` | meditation-round |
| `star` | star |
| `storefront` | shop |
| `summarize` | clipboard-list |
| `swap_horiz` | transfer-horizontal |
| `swap_vert` | transfer-vertical |
| `table` | table |
| `table_view` | table |
| `tag` | tag-horizontal |
| `task_alt` | check-circle |
| `tax` | calculator |
| `taxi` | streets-navigation |
| `text_fields` | text-field |
| `tickets` | ticket |
| `timer` | stopwatch |
| `today` | calendar |
| `toys` | balls |
| `train` | tram |
| `translate` | translation |
| `travel` | suitcase |
| `trending_down` | graph-down |
| `trending_up` | graph-up |
| `tune` | tuning-2 |
| `tv` | tv |
| `two_wheeler` | scooter |
| `undo` | undo-left |
| `upload_file` | upload |
| `vegetables` | cart-large-4 |
| `visibility_off` | eye-closed |
| `volunteer_activism` | hand-heart |
| `wallet` | wallet |
| `wallpaper` | wallpaper |
| `water` | waterdrop |
| `wifi` | wi-fi-router-round |
| `wifi_off` | wi-fi-router-minimalistic |

`ic_shortcut_*`: layer-list, a white glyph on the rose circle (app shortcuts; launchers draw them as they are).
`ic_notification`, `ic_launcher_*`, `ic_mid_apps`: the Burrow and mid apps marks, not icons from the set.

The Edit category picker (62) lists 60 icons grouped by kind of spending; order lives in `CATEGORY_ICONS`
(`ui/components/AppIcons.kt`).
