# Icons

**Solar**, Linear (outline) style, by 480 Design (CC BY 4.0, Figma community file, npm `@iconify-json/solar`).
Chosen over Hugeicons; outline only, no duotone, so every icon looks the same (user, 2026-10-31). CC BY needs credit: the licence text with the credit line is
`res/raw/licence_solar_cc_by.txt`, shown in About › Licences as "Solar icons by 480 Design". Keep that row.

**Source of truth since 12 Nov: the design's own paths, `design/icons/solar-icons.json`** (from `burrow-design-final-1112.zip`).
`python3 tools/solar_icon.py --kit design/icons/solar-icons.json` rewrites every drawable it covers; the bare ✕ + ✓ − are the
inner glyphs of Solar's circle icons, as the design draws them. Icons only the app uses (category icons) still come from the
npm package below.

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
| `account_balance` | buildings-2 |
| `account_balance_wallet` | wallet |
| `add` | (glyph from add-circle) |
| `alarm` | alarm |
| `apartment` | buildings |
| `arrow_back` | arrow-left |
| `arrow_downward` | arrow-down |
| `arrow_drop_down` | alt-arrow-down |
| `arrow_forward` | arrow-right |
| `arrow_upward` | arrow-up |
| `arrow_upward_alt` | arrow-right-up |
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
| `call_split` | routing-2 |
| `category` | widget |
| `celebration` | confetti |
| `check` | (glyph from check-circle) |
| `check_circle` | check-circle |
| `checkroom` | t-shirt |
| `chevron_left` | alt-arrow-left |
| `chevron_right` | alt-arrow-right |
| `child_care` | smile-circle |
| `close` | (glyph from close-circle) |
| `cloud` | cloud |
| `cloud_done` | cloud-check |
| `cloud_upload` | cloud-upload |
| `cloud_download` | cloud-download |
| `account_switch` | user-rounded |
| `logout` | logout-2 |
| `code` | code |
| `coffee` | cup-hot |
| `content_copy` | copy |
| `content_paste` | clipboard |
| `cooking_gas` | fire |
| `credit_card` | card |
| `currency_exchange` | transfer-horizontal |
| `currency_pound` | dollar |
| `currency_rupee` | dollar |
| `dark_mode` | moon |
| `dashboard_customize` | widget-add |
| `date` | calendar-date |
| `date_range` | calendar-mark |
| `delete` | trash-bin-minimalistic |
| `delete_forever` | trash-bin-trash |
| `delivery` | delivery |
| `description` | document-text |
| `directions_car` | bus |
| `doctor` | stethoscope |
| `donut_large` | pie-chart-2 |
| `download` | download-minimalistic |
| `drag_indicator` | hamburger-menu |
| `edit` | pen |
| `edit_note` | pen-new-square |
| `envelope` | letter |
| `error` | danger-circle |
| `euro` | euro |
| `event` | calendar |
| `event_available` | calendar-add |
| `event_busy` | calendar-minimalistic |
| `event_repeat` | repeat |
| `event_upcoming` | calendar-search |
| `expand_less` | alt-arrow-up |
| `expand_more` | alt-arrow-down |
| `export` | export |
| `fastfood` | donut-bitten |
| `favorite` | heart |
| `filter_alt_off` | filter |
| `filter_list` | sort |
| `fingerprint` | shield-keyhole |
| `fitness_center` | dumbbell-large |
| `flag` | flag |
| `flight` | suitcase-tag |
| `folder` | folder |
| `format_list_bulleted` | list |
| `gadgets` | headphones-round |
| `games` | gamepad |
| `gold` | medal-ribbons-star |
| `grid_view` | widget-2 |
| `group` | users-group-rounded |
| `hand_tap` | cursor |
| `handshake` | hand-money |
| `help` | question-circle |
| `history` | history |
| `home` | home-2 |
| `hospital` | medical-kit |
| `hotel` | bed |
| `house_help` | broom |
| `image` | gallery |
| `import` | import |
| `info` | info-circle |
| `insights` | graph-up |
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
| `local_mall` | bag-4 |
| `lock` | lock-keyhole |
| `medicine` | pill |
| `more_horiz` | menu-dots |
| `movie` | clapperboard-play |
| `notes` | notes |
| `notifications` | bell |
| `notifications_active` | bell-bing |
| `notifications_off` | bell-off |
| `notifications_paused` | bell-off |
| `palette` | palette |
| `pause` | pause |
| `pause_circle` | pause-circle |
| `pause_circle_filled` | pause-circle |
| `payments` | banknote-2 |
| `percent` | sale |
| `person` | user-rounded |
| `pets` | paw |
| `photo_camera` | camera |
| `pie` | pie-chart-2 |
| `pie_chart` | pie-chart-2 |
| `pin` | pin |
| `plants` | leaf |
| `play_arrow` | play |
| `public` | global |
| `real_estate_agent` | home-smile |
| `receipt_long` | bill-list |
| `redeem` | gift |
| `redeem2` | gift |
| `redo` | restart |
| `refund` | card-recive |
| `remove` | (glyph from minus-circle) |
| `repairs` | sledgehammer |
| `restaurant` | chef-hat |
| `rule` | checklist-minimalistic |
| `salon` | scissors |
| `savings` | safe-2 |
| `scales` | scale |
| `schedule` | clock-circle |
| `school` | square-academic-cap |
| `science` | test-tube |
| `search` | magnifer |
| `search_off` | magnifer-bug |
| `settings_backup_restore` | restart |
| `shield` | shield |
| `shield_check` | shield-check |
| `shoes` | running-2 |
| `shopping_cart` | cart-large-2 |
| `signal_cellular_4_bar` | chart |
| `smartphone` | smartphone |
| `sms` | chat-round-dots |
| `spa` | leaf |
| `star` | star |
| `storefront` | shop |
| `summarize` | clipboard-list |
| `swap_horiz` | transfer-horizontal |
| `swap_vert` | transfer-vertical |
| `table` | document-text |
| `table_view` | document-text |
| `tag` | hashtag |
| `task_alt` | check-circle |
| `tax` | calculator |
| `taxi` | streets-navigation |
| `text_fields` | text |
| `tickets` | ticket |
| `timer` | stopwatch |
| `today` | calendar-mark |
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
| `upload_file` | file-send |
| `vegetables` | cart-large-4 |
| `visibility_off` | eye-closed |
| `volunteer_activism` | hand-heart |
| `wallet` | wallet |
| `wallpaper` | gallery-wide |
| `water` | waterdrop |
| `wifi` | wi-fi-router |
| `wifi_off` | wi-fi-router |

`ic_shortcut_*`: layer-list, a white glyph on the rose circle (app shortcuts; launchers draw them as they are).
`ic_notification`, `ic_launcher_*`, `ic_mid_apps`: the Burrow and mid apps marks, not icons from the set.

The Edit category picker (62) lists 60 icons grouped by kind of spending; order lives in `CATEGORY_ICONS`
(`ui/components/AppIcons.kt`).
