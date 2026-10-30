# Icons

**Hugeicons** free set, stroke rounded (MIT, hugeicons.com, npm `@hugeicons/core-free-icons`). Chosen over
Phosphor for its look (user, 2026-10-30). Licence text: `res/raw/licence_hugeicons_mit.txt` (shown in About › Licences).

Every icon is a vector drawable in `app/src/main/res/drawable/`, named by meaning: `ic_<meaning>_<weight>.xml`
with weights `regular`, `fill`, `duotone`. The free set has **one style**, so the three files are the same
stroke icon (1.5 stroke, round caps, viewport 24). The weight names stay so screens pick a weight by role; the
selected or tinted state comes from the container (the nav pill, the tonal circle), never from the glyph.
`isShrinkResources` drops every file the app doesn't reference, so unused weights cost nothing in the APK.

## Which weight where (kept by role)
| Where | Weight | Size |
|---|---|---|
| Inside a tonal circle (rows, widgets, notes, headers), widget labels | **duotone** | 22 dp in a 40 dp circle, 36 dp in a 64 dp circle |
| Actions: app-bar buttons, back, close, add, search, filter, calendar, chevrons, chips | **regular** | 24 dp (20 in buttons, 18 in chips) |
| Selected bottom-nav tab, status icons, the check in a selected chip | **fill** | 24 dp |

Tint every icon (`tint =`): the files are white so one drawable serves every colour.
Never mix icon sets. Never draw an icon with emoji. Never use the Pro styles (solid, duotone, bulk): they are
not MIT and can't ship in a GPL repo.

## Adding or changing an icon
The map meaning → Hugeicons name lives in `ICONS` in `tools/hugeicons_icon.py` (single source; the table below
is a copy). Add a line there, then:
```sh
npm pack @hugeicons/core-free-icons && tar xzf hugeicons-core-free-icons-*.tgz   # anywhere outside the repo
python3 tools/hugeicons_icon.py <path>/package <meaning>        # one icon, all three weights
python3 tools/hugeicons_icon.py <path>/package                  # regenerate every icon
```
Browse names at hugeicons.com (free filter) or `ls <path>/package/dist/esm | grep -i <word>`.
The free set has no keyboard-backspace glyph: the keypad uses ArrowLeft04.

## Names (meaning → Hugeicons)
| Meaning | Hugeicons |
|---|---|
| `account_balance` | Bank |
| `account_balance_wallet` | Wallet01 |
| `add` | Add01 |
| `alarm` | AlarmClock |
| `apartment` | Building03 |
| `arrow_back` | ArrowLeft02 |
| `arrow_downward` | ArrowDown02 |
| `arrow_drop_down` | ArrowDown01 |
| `arrow_forward` | ArrowRight02 |
| `arrow_upward` | ArrowUp02 |
| `arrow_upward_alt` | ArrowUp02 |
| `attach_file` | Attachment01 |
| `attach_money` | DollarCircle |
| `autorenew` | Refresh |
| `backspace` | ArrowLeft04 |
| `backup` | CloudUpload |
| `balance` | BalanceScale |
| `bar_chart` | ChartColumn |
| `battery_full` | BatteryFull |
| `block` | Unavailable |
| `bolt` | Flash |
| `books` | Books01 |
| `bug_report` | Bug01 |
| `bus` | Bus01 |
| `calendar_month` | Calendar03 |
| `calendar_view_day` | Calendar01 |
| `calendar_view_month` | GridView |
| `call_split` | Split |
| `category` | Shapes |
| `celebration` | Party |
| `check` | Tick02 |
| `check_circle` | CheckmarkCircle02 |
| `checkroom` | TShirt |
| `chevron_left` | ArrowLeft01 |
| `chevron_right` | ArrowRight01 |
| `child_care` | Baby01 |
| `close` | Cancel01 |
| `cloud` | Cloud |
| `cloud_done` | CloudSavingDone01 |
| `cloud_upload` | CloudUpload |
| `code` | SourceCode |
| `coffee` | Coffee02 |
| `content_copy` | Copy01 |
| `content_paste` | Clipboard |
| `cooking_gas` | Fire |
| `credit_card` | CreditCard |
| `currency_exchange` | Exchange01 |
| `currency_pound` | Pound |
| `currency_rupee` | Rupee |
| `dark_mode` | Moon02 |
| `dashboard_customize` | DashboardSquare02 |
| `date` | Calendar01 |
| `date_range` | Calendar02 |
| `delete` | Delete02 |
| `delete_forever` | Delete01 |
| `delivery` | Scooter02 |
| `description` | File02 |
| `directions_car` | Car01 |
| `doctor` | Stethoscope |
| `donut_large` | ChartRing |
| `download` | Download04 |
| `drag_indicator` | DragDropVertical |
| `edit` | PencilEdit02 |
| `edit_note` | NoteEdit |
| `envelope` | Mail01 |
| `error` | AlertCircle |
| `euro` | Euro |
| `event` | CalendarCheckIn01 |
| `event_available` | CalendarCheckIn01 |
| `event_busy` | CalendarRemove01 |
| `event_repeat` | Repeat |
| `event_upcoming` | CalendarClock |
| `expand_less` | ArrowUp01 |
| `expand_more` | ArrowDown01 |
| `export` | Share05 |
| `fastfood` | Hamburger01 |
| `favorite` | Favourite |
| `filter_alt_off` | FilterRemove |
| `filter_list` | FilterHorizontal |
| `fingerprint` | FingerPrint |
| `fitness_center` | Dumbbell01 |
| `flag` | Flag02 |
| `flight` | Airplane01 |
| `folder` | Folder01 |
| `format_list_bulleted` | LeftToRightListBullet |
| `gadgets` | Headphones |
| `games` | GameController03 |
| `gold` | GoldIngots |
| `grid_view` | GridView |
| `group` | UserGroup |
| `hand_tap` | Touch01 |
| `handshake` | Agreement02 |
| `help` | HelpCircle |
| `history` | History |
| `home` | Home01 |
| `hospital` | FirstAidKit |
| `hotel` | BedDouble |
| `house_help` | Clean |
| `image` | Image01 |
| `import` | InboxDownload |
| `info` | InformationCircle |
| `insights` | Sparkles |
| `insurance` | Umbrella |
| `investment` | ChartIncrease |
| `ios_share` | Share05 |
| `jewellery` | Diamond01 |
| `key` | Key01 |
| `laptop_mac` | Laptop |
| `light_mode` | Sun03 |
| `lightbulb` | Idea01 |
| `loan_emi` | HandCoins |
| `local_bar` | Beer |
| `local_gas_station` | FuelStation |
| `local_mall` | ShoppingBag02 |
| `lock` | SquareLock02 |
| `medicine` | Medicine02 |
| `more_horiz` | MoreHorizontal |
| `movie` | Film02 |
| `notes` | Note01 |
| `notifications` | Notification03 |
| `notifications_active` | Notification01 |
| `notifications_off` | NotificationOff03 |
| `notifications_paused` | NotificationSnooze03 |
| `palette` | PaintBoard |
| `pause` | Pause |
| `pause_circle` | PauseCircle |
| `pause_circle_filled` | PauseCircle |
| `payments` | Money03 |
| `percent` | Percent |
| `person` | User |
| `pets` | PawPrint |
| `photo_camera` | Camera01 |
| `pie` | PieChart |
| `pie_chart` | PieChart |
| `pin` | PasswordValidation |
| `plants` | Plant02 |
| `play_arrow` | Play |
| `public` | Globe02 |
| `real_estate_agent` | RealEstate02 |
| `receipt_long` | Invoice01 |
| `redeem` | Gift |
| `redeem2` | GiftCard |
| `redo` | Redo03 |
| `refund` | ArrowTurnBackward |
| `remove` | MinusSign |
| `repairs` | Wrench01 |
| `restaurant` | Restaurant01 |
| `rule` | CheckList |
| `salon` | Scissors |
| `savings` | PiggyBank |
| `scales` | BalanceScale |
| `schedule` | Clock01 |
| `school` | Mortarboard02 |
| `science` | TestTube01 |
| `search` | Search01 |
| `search_off` | SearchRemove |
| `settings_backup_restore` | History |
| `shield` | SecurityCheck |
| `shield_check` | SecurityCheck |
| `shoes` | RunningShoes |
| `shopping_cart` | ShoppingCart01 |
| `signal_cellular_4_bar` | SignalFull02 |
| `smartphone` | Smartphone |
| `sms` | Message01 |
| `spa` | Flower |
| `star` | Star |
| `storefront` | Store01 |
| `summarize` | News01 |
| `swap_horiz` | ArrowDataTransferHorizontal |
| `swap_vert` | ArrowDataTransferVertical |
| `table` | LayoutTable01 |
| `table_view` | LayoutTable01 |
| `tag` | Tag01 |
| `task_alt` | CheckmarkCircle02 |
| `tax` | Taxes |
| `taxi` | Taxi |
| `text_fields` | TextFont |
| `tickets` | Ticket01 |
| `timer` | Timer02 |
| `today` | Calendar04 |
| `toys` | Puzzle |
| `train` | Train01 |
| `translate` | Translate |
| `travel` | Luggage01 |
| `trending_down` | TrendingDown |
| `trending_up` | TrendingUp |
| `tune` | SlidersHorizontal |
| `tv` | Tv01 |
| `two_wheeler` | Motorbike02 |
| `undo` | Undo03 |
| `upload_file` | Upload04 |
| `vegetables` | Carrot |
| `visibility_off` | ViewOff |
| `volunteer_activism` | HandHeart |
| `wallet` | Wallet01 |
| `wallpaper` | PaintBrush02 |
| `water` | Droplet |
| `wifi` | Wifi01 |
| `wifi_off` | WifiOff01 |

`ic_shortcut_*`: layer-list, a white glyph on the rose circle (app shortcuts; launchers draw them as they are).
`ic_notification`, `ic_launcher_*`, `ic_mid_apps`: the Burrow and mid apps marks, not icons from the set.

The Edit category picker (62) lists 60 icons grouped by kind of spending; order lives in `CATEGORY_ICONS`
(`ui/components/AppIcons.kt`).
