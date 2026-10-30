# Screens

Every screen, top to bottom, with its PNG (`design/screens/`). Build each to match its PNG at 412 dp
width, light (dark mode is checked in the app). "→" means what a tap opens.

## First open
| PNG | Screen | Layout |
|---|---|---|
| 01-welcome | Welcome | logo + "Burrow" · rose preview panel (saved-payment card, budget ring widget, due-bill widget, slightly tilted) · "Money that tracks itself" · one line · **Get started** · "Free and open source · Restore a backup" |
| 02-welcome-currency | Step 1 of 2 | main currency rows (region first, ✓ on selected) · Continue |
| 03-welcome-accounts | Step 2 of 2 | "Where is your money?" · account rows with remove · chips Bank / Card / Wallet / Loan · Skip / Done |
| 45–47 capture-intro | Turn on auto-capture (3 steps) | dots · rose panel illustrating the step · headline · one line · Not now / Next (Allow) → system permission dialogs |

## Today
| PNG | State | Layout |
|---|---|---|
| 04-today | normal | title "Today" + date · accounts + add buttons · hero (October budget · days left · ₹13,644 · "left of ₹30,000 · ₹1,050 a day" · wavy progress) · review note "3 payments to review" → Review · widgets |
| 05-today-widgets | scrolled | This week (bars) · Next bill (Mark paid) · Net worth (line) · Top category (donut) · Goal (wide: ring right) · Money in and out (3 figures + split bar) · Recent (See all) |
| 06 / 07 / 08 / 09 | sample data / capture paused / over budget / empty | note first; over budget turns the hero red-toned with "₹6,300 over"; empty = empty state with "Record payments for you" |

## Activity
| PNG | State | Layout |
|---|---|---|
| 11-activity | normal | header funnel + calendar · search · chart card (Last 14 days · avg a day · ₹ spent · "28 transactions" · pill bars) · day groups (Today −₹478 …) · FAB Add expense |
| 12-activity-day | a day tapped | big number switches to that day, list filters, "All days" |
| 13 / 14 | Filter sheet / Dates sheet | see COMPONENTS |
| 15-activity-filtered | filters on | funnel badge + tint; chips "Food & drinks ✕" · Clear all |
| 16-activity-no-results | search miss | empty state "No results for …" + Clear filters |

## Recording money
| PNG | Screen | Layout |
|---|---|---|
| 17 / 18 | New expense / transfer | close · title · Save · Expense/Income/Transfer group · ₹ amount (display) · **Paid to** pill (payee) · Category / Account / Date rows · Payee / Note · keypad. Save disabled until amount + category; tapping Save early marks the missing row red |
| 19 / 20 | Category / Account sheet | list rows with circle + name + one line (spent this month / balance), ✓ on selected, "New category" |
| 21-transaction | Transaction | centred: category circle, payee, −₹240 (display), date · rows Category / Paid from / Note · "Recorded from SMS" box · Delete / Edit · header Duplicate |
| 73-delete-dialog | Delete | dialog, Undo snackbar after |

### New expense extras (user, 2026-10-20: simple, but not missing features)
| PNG | Screen | Layout |
|---|---|---|
| 79-expense-with-extras | New expense filled | payee chosen sets the category; "Lent · …" pill; note and Attach a receipt rows |
| 87-payee-typing | Paid to | a **free-text field** right under the amount ("Paid to" / "Received from"): type anything. Up to 4 quiet outlined suggestion chips (frequent payees; payers for income) show below and filter as you type; tapping one fills the text and sets its usual category. Typing a known payee exactly also sets it. Clear with ✕ |
| 74-expense-note-sheet | Note | Note · tag chips (#Work, #Trip, #Family, #Reimbursable) · Done |
| 76-expense-attach-sheet | Attach | Take a photo / Choose a photo / Choose a file · attached files with remove. Files stay in app storage, included in backups |
| 78-expense-date-sheet / 176-date-sheet-upcoming | Date | "When you paid" · Yesterday · Today · Tomorrow chips (a chip picks, Done saves) over the month calendar (today ringed, chosen filled) · Time row → time wheel · Done (`DateSheet`). Future = **upcoming**: the line reads "Upcoming · added on Sat 24 Oct", the form "Sat 24 Oct · upcoming"; listed in Plan, added to Activity on the day |

## Plan
| PNG | Screen | Layout |
|---|---|---|
| 22-plan-bills | Plan › **Coming up** | tabs Coming up · Budgets · Goals; groups Overdue (red, "Late") · This week · This month · Later with totals; rows: circle, name, ↻/📅 + "in 5 days · ICICI", amount + date; swipe right Paid/Add now, left Skip/Delete |
| 25 / 26 | Bill / overdue bill | centred header (amount, "Due today" / "Was due 17 Oct" in red) · rows Category / Paid from / Repeats / Ask me on the day (switch) · Paid before (account circle rows) · Skip / Mark paid |
| 27 / 161 / 162 / 28 | New bill / due-day sheet | Bill/Subscription · Name · Amount · **How often** (Weekly · Monthly · Yearly button group) · rows **Due** first ("Every week on Monday" / "Every month on the 1st" / "Every year on 9 Jan"), Category, Paid from · When it is due (Ask me / Record it for me / Only remind me). Due opens the day value sheet (28), the weekday sheet (163) or the yearly calendar (164) |
| 163-weekday-sheet | Due every week on | one row of seven 44 dp day circles from the week-start day (`WeekdayRow`; chosen primary, others surfaceContainerLow) · "Every Monday · next on Mon, 26 Oct" (follows each tap; the sheet stays open) · Done |
| 164-yearly-date-sheet | Due every year on | the one calendar, month name only (no year), ‹ › move months; line "Next: 9 Jan 2027"; Done |
| 29-subscriptions | Subscriptions | suggestion note (Add) · centred "Every month ₹876 · ₹10,516 a year" · rows |
| 23-plan-budgets | Budgets | centred header (₹13,644 left · of ₹30,000 · a day · bar) · Categories rows (circle · name · "₹710 over"/"₹5,310 left" · ring %) |
| 30 / 31 / 32 | Budget / category budget / edit sheet | centred header (red when over) · rows or that category's transactions · edit button → sheet with − amount + and chips |
| 24-plan-goals | Goals | centred header (Saved for goals) · goal rows with rings · FAB Add goal |
| 33 / 34 / 35 | Goal / reached / Add money sheet | centred header + bar · Added rows · Add money (bottom) · "Goal reached" note first when done |

## Insights
| PNG | Screen | Layout |
|---|---|---|
| 36–39 | Insights | header funnel + calendar · chart card (Spent · range · ₹ · vs previous period · cumulative line + dashed previous) · A day + Biggest widgets · By category card (stack bar + rows → Category insights) · Patterns: Busiest day, No-spend days, You kept, Top payee · month heatmap (tap a day → Activity) |
| 40-insights-filter-sheet | Filter | Accounts + Categories chips (no Type) · "Show 9 payments" |
| 41-insights-no-spending | empty range | chips · empty state + Change dates |
| 42-category-insights | Category | centred header · By day pill chart · transactions |

## Auto-capture
| PNG | Screen | Layout |
|---|---|---|
| 43-capture-notification | notification | "₹240 to Third Wave Coffee · HDFC ••1234 · pick a category" · two category pills · Ignore · Edit (opens New expense filled in) |
| 44-review | To review | cards (no swipe): payee, account, time, amount, ✕ · SMS with amount/payee/account highlighted · category chips + More · **Edit · Save**, two equal tonal buttons (stacked at large text; Edit opens New expense prefilled (165), saving there clears it) · bottom "Save 3 payments" (the one filled button). Top row "2 messages not understood · Teach once, read from then on" → Not understood. Empty (170): "All caught up" · Back to Today · link "Teach 2 messages it missed" |
| 48-auto-capture | Auto-capture | centred status (142 payments recorded) · Bank SMS ("Reads messages from banks", always) / Payment apps switches · "2 messages not understood" → Teach · Message rules · Try it / How it works |
| 49 / 50 / 51 | Message rules / Rule / Add rule sheet | see `docs/CAPTURE.md`. A built-in pack row opens its rules listed like yours (name · sender · payments read) → the same Rule screen, read-only. Your rules have a pencil in the top bar: Rename rule sheet |
| 52-teach-message / 149-teach-done | Teach | "Tap the amount in the message" ("Tap the **name**: who you paid or who paid you") · SMS with tappable parts · Amount / **From / to** / Account / Money in or out rows fill as you tap (tap "credited" or "debited" like the payee; skip to guess) · picked rows show a check · Save. Done (149): "Use for messages like this" switch, then "Payments it reads go to To review with the payee's usual category; you pick it there." (no category row) |

## More and settings
| PNG | Screen | Layout |
|---|---|---|
| 53 / 54 | More | 4 equal status tiles · Money (Accounts, Categories) · Customise (Today screen, New expense form, Transactions, Language and region, Currency — all open the one Customise screen on that tab) · App (Font and text size, Notifications, Hide amounts, Export or import CSV) · About (About Burrow, Delete all data) |
| 55–59 | Accounts / bank / card / loan / deposit | centred headers; card (57): statement amount, due date, Since statement / Available rows ("₹1.38L available · 8% of ₹1.5L used · shared with HDFC Regalia" on a shared line), Pay bill (141: chips "Statement ₹8,120" · "Since statement ₹10k", no minimum due); Edit card (177): "Credit limit" row when another card exists → sheet (174) Its own limit / Same limit as HDFC Regalia, round check; sharing hides the Limit field; loan: left to pay, EMIs paid, Next EMIs, Prepay; deposit: maturity amount, reminder ("Remind me a week before": one notification 7 days before maturity, at the reminder time in Notifications, under the Bills switch; 166: "Fixed deposit matures in a week · ₹1,12,000 goes to HDFC Savings on Mon, 26 Oct" → Accounts) |
| 60 / 61 / 62 | Categories list / grid / edit | centred header (13 · spent this month) · list sorted by spend with % (or 3-column tiles via header toggle) · FAB "New category" · edit: name, colour circles, icon circles in a card, Delete |
| 63 / 64 / 65 | Appearance / Font / Language | Light/Dark group, colour swatches, Dynamic colour switch · font rows with preview card + Text size group · language rows with native names |
| 66 / 67 / 68 | Notifications / Main currency / Month start sheet | switches with a live notification preview · searchable currency rows · day value sheet (68) |
| 80–85 | **Customise** (one screen; tabs as scrolling `PillTabs` (40 dp, own widths): Today · Expense · Transactions · Region · Currency) | Same four blocks on every tab: **choice tiles** (pick one, selected = primaryContainer + check), **toggle tiles** (pick many, 3 columns, check badge), **pick lists** (one-of-many rows with the round check), **ordered rows** (icon · name · ↑ ↓ · switch) and switch/plain rows. Today: top card pick list (Budget left / Spent / Balance, value as subtitle) · widgets as ordered rows · recent 3/5/10 · review switch. Expense (81): extras toggle tiles (Note, Repeat, Attach, Lend, Time, Last category, Recent with the history icon) · opens as Expense/Income/Transfer · default account. Transactions: "When a payment is found" as 3 wide choice tiles (Ask on the notification / Open it to edit first / Save it for me) · list toggles · swipe rows |
| 83 / 84 | Customise › Currency / rate sheet | primary currency row · Show amounts as: choice tiles Symbol ₹1,240 / Code INR 1,240 · Decimal places: Auto 1,240.5 / None 1,240 / Always 1,240.00 · Other currencies rows ("1 USD = ₹84.10 · set 12 Oct") + Add · rate stepper sheet |
| 85-language-region | Customise › Region | Language row · Numbers: pick list (example as title, region as subtitle, round radio right) · Dates: pick list (Written out / Day first / Month first / Year first) · Week: connected button group Monday / Sunday / Saturday · Month starts on row → day value sheet (68) |
| 86-account-card-net-worth | Card detail | "Count in net worth" switch (off by default for cards and loans); Accounts header says what is excluded; "Lent and borrowed" section lists people |
| 69 / 70 / 71 / 72 | Backup / App lock / PIN / About | plain lists; PIN keypad with wavy lock shape; About with the logo, version, source, licences |

## Retention features (88–105)
| PNG | Screen | Notes |
|---|---|---|
| 04-today (was 88) | Today | hero line ends "₹X safe to spend today"; no Customise button on Today (Customise stays optional, in More) |
| 89-home-screen-widget | Launcher | Glance widget 4×2: mark + "Safe to spend today", 44 sp amount, "₹13.6k left in October · 13 days", thin bar, 56 dp + (New expense); 2×2 Add expense and Next bill widgets; icon long-press shortcuts: New expense, New income, Review N payments, Search |
| 90-quick-settings-tile | Quick settings | "Add expense · Burrow" tile opens New expense; Sunday review + capture notifications below |
| 17-new-expense (was 91) | New expense | final layout (burrow-new-expense, 24 Oct): amount · **favourites as filled quick-add pills right under it** (surfaceContainerHigh, "₹20 Chai" amount first in bold, no icon; empty form only; one tap saves with Undo) then up to 4 **Recent** pills (175: outlined with the category circle, "₹240 Third Wave Coffee"; one tap fills the form (178) to check and Save; no label) · Paid to (payee matches under it while typing) · category chips · "HDFC Savings · Today" · extras Note · **Time ("9:30 AM")** · Receipt · Repeat or lend · docked keypad |
| 92-favourites | Favourites | centred header (count), rows: ⠿ drag handle, category circle, "Chai · ₹20", category · account, remove |
| 93-long-press-sheet | Transaction long-press | 450 ms on any transaction row: Change category, Duplicate to today, Split, Add a refund, Save as favourite; Delete in its own group |
| 94-transaction-split-refund | Transaction | rows "Split across categories" and "Add a refund" / "Refunded ₹X · counts as ₹Y spent"; split parts listed when split |
| 95-split-payment | Split payment | header "₹X still to put in a category"; part rows (category, − amount +), Add a part, chip "Put ₹X in …"; Save only when balanced with 2+ parts |
| 96-refund-sheet | Refund | stepper, All of it / Half, "Back into <account>" — lowers the category, not income |
| 97-import | Import or export | Cashew, Walnut, Money Manager (letter circles, no brand logos), Any spreadsheet (CSV); Restore a backup; Export to CSV |
| 98-import-check | Check before import | count + range, category matches (unmatched in red), account matches, "Skip ones already here", one button |
| 99-welcome-budget-step | Welcome step 2 of 3 | "How much a month?" stepper, 20k/30k/50k/75k chips, "₹X a day" row, Skip for now / Continue |
| 100-sunday-review-notification | Notification | Sunday 6 PM: "Your week: ₹X spent" · "N to review · 2 bills due · ₹X left. Takes 30 seconds." |
| 101-sunday-review | Sunday review | header week total vs last week; rows: to review, bills this week, budget left + safe today, top category; "Save N and finish" |
| 102-auto-capture-trust | Auto-capture | 112 dp ring "92%", "Recorded by itself", "131 of 142 payments this month · you typed 11"; "Teach it N messages it missed" |
| 37-insights-widgets | Insights widgets | A day with a sparkline, Biggest; Top payee lists the next two payees with amounts instead of three identical icons |
| 103-customise-drag-reorder | Customise › Today | widgets: ⠿ handle (drag to reorder, row lifts) · icon · name · switch. No arrows anywhere |
| 104-notifications-settings | Notifications | adds "Sunday review" switch (on) |
| 106-edit-account | Edit account | header preview (icon + colour), Name, Last digits (helps match SMS), Colour (12 hues), Icon (12), Count in net worth, Opening balance, Archive |
| 107-edit-account-colour | Edit account (scrolled) | colour and icon pickers, same blocks as Edit category |
| 110-activity-chart-open | Activity, chart open | the summary row expanded into the 14-day pill chart; ⌃ in its header folds it |
| 111-type-sheet | Type sheet | from the title "New expense ▾": Expense (money out) · Income (money in) · Transfer |
| 112-repeat-or-lend-sheet | Repeat or lend | rows Once · Repeat · Subscription · Lent · Borrowed; How often (Weekly/Monthly/Yearly) or a name field; Done |
| 113-coming-up-later | Coming up (scrolled) | Later group, swipe hint, "Every month": Subscriptions and EMIs rows |
| 114-upcoming-one-off | Upcoming payment | centred header "Upcoming · added on Saturday, 24 Oct"; Category, Account, Change date; Delete row; bottom "Add now" |
| 115-upcoming-added-notification | Notification | "Rent ₹18,000 added today · It was due today · open Burrow to undo" |

## 2026-10-23 design update (kit screens 105–109)
| PNG | Screen | Layout |
|---|---|---|
| 105-brand-logos | Brand | Burrow icon (full, round, dark, themed, notification) and the developer mark "mid apps" |
| 106-edit-account | Edit account | header preview (icon + colour), Name, Last digits (helps match SMS), Colour (12 hues), Icon (12), Count in net worth, Opening balance, Archive |
| 107-edit-account-colour | Edit account (scrolled) | colour and icon pickers, same blocks as Edit category |
| 108-new-expense-keypad-open | New expense, typing | keypad open, caret after the amount, list fades above it |

## Calm pass (2026-10-23) — PNGs from burrow-ui-changes
| PNG | Screen | Layout |
|---|---|---|
| 110-activity-chart-open | Activity, chart open | the summary row expanded into the 14-day pill chart; ⌃ in its header folds it |
| 111-type-sheet | Type sheet | from the title "New expense ▾": Expense (money out) · Income (money in) · Transfer |
| 112-repeat-or-lend-sheet | Repeat or lend | rows Once · Repeat · Subscription · Lent · Borrowed; How often (Weekly/Monthly/Yearly) or a name field; Done |
| 113-coming-up-later | Coming up (scrolled) | Later group, swipe hint, "Every month": Subscriptions and EMIs rows |
| 114-upcoming-one-off | Upcoming payment | centred header "Upcoming · added on Saturday, 24 Oct"; Category, Account, Change date; Delete row; bottom "Add now" |
| 115-upcoming-added-notification | Notification | "Rent ₹18,000 added today · It was due today · open Burrow to undo" |

## Design requests (24 Oct) — 116–159 (light, in screens/)
| PNG | Screen | Notes |
|---|---|---|
| 116 / 117 | Messages not understood | cards like To review: sender + time, ✕ ignore, message, full-width tonal **Teach** button (no swipe, C2); empty "Nothing to teach" |
| 118-builtin-pack | Built-in pack (India) | centred header (globe, count, "rules · built in · read-only"), "Use this pack" switch, "Rules in this pack" rows (sender · N payments · chevron) |
| 50-rule / 120-rule-builtin | Rule yours / built-in | yours: pencil in the bar (→ 119), "Use this rule", Delete rule; built-in: no pencil, no switch, footer "Built-in · India" |
| 119-rename-rule-sheet | Rename rule | one pill field (pencil, ✕ clear), Save off while empty |
| 121-paste-message-sheet | Paste a message | message field (tall pill) + "Who sent it" field, Continue off until text |
| 122 / 123 | Licences / licence text | grouped rows App · Libraries · Icons and fonts (name · licence · chevron); text screen: name as small title, paragraphs body muted |
| 72, 124–127 | About + update row | order: Check for updates · Source code · Your data stays yours (shield) · Rate (Play) · Report a problem · Licences; row line: From GitHub Releases / Checking… / You're on the latest version / Couldn't check (error) / Updates come from F-Droid |
| 128–132 | Update sheet | ready · downloading (wavy + "Downloading… 42%", Update off) · needs permission (Install) · downloaded (Install) · failed (error line, "Open release page"); What's new block with primary dots; Later + main button |
| 133 / 134 | New goal | Name, Target, By when (sheet), Icon card 2×4 coloured circles, chosen = primary ring; error "Add a name and a target to save" |
| 135 / 160 | Time wheel | every time is the **TimeWheelSheet**: hour · minute (5s) · AM/PM columns on a pill band. New expense time (135); More › Notifications › Bill reminders → "Bill reminder time · A day before it is due" (160); Sunday review → "Sunday review time · Every Sunday". Rows read "9:00 AM, a day before" / "Sundays at 6:00 PM" (104); the switch still turns each on or off |
| 136–144 | Sheets | all value sheets are one **ValueSheet** (value + caret, chips, same 3×4 keypad): Type an amount · Text (one field) · Option (pick one, round checks) · Add a currency (search + list) · Fix balance (− amount +, difference line) · Pay card bill / Pay EMI (amount, chips, From, Date) · Backup password set / wrong |
| 48, 145, 146 | Auto-capture on / off / blocked | switches on only when Android allows; off: info note + the two switches + How it works only; blocked: warning note "Android is blocking access …" + App info |
| 53, 147, 148 | More tile | On "92% by itself" · Needs access "Tap to fix" · Not set up "Tap to turn on" |
| 44-review | To review | **Edit · Save** on each card (see Auto-capture above) + bottom "Save 3 payments" |
| 52 / 149 | Teach | 4 rows incl. **Money in or out** ("debited · Money out"); "or skip it"; round check on each picked row |
| 22, 23, 80, 83 | Tabs | **pill tabs**: Plan equal width, centred, no check, selected = primary; Customise same pills, own width, scroll |
| 83-currencies | Decimal tiles | check on the title line only; value full width, never "1,240…." |
| 12 / 110 | Activity day | "All days" plain primary text in the header next to ⌃; small days are round dots |
| 46 | Capture intro 2 | "Nothing sent" (shield); "Burrow never sends your data anywhere…" |
| 06, 07, 150–152 | Large text (1.3× / 2×) | wrapped note titles stay one tight block above their line; nav shows only the selected label; widgets and tiles go one per row; rows wrap; the FAB goes icon-only; display amounts shrink to fit |
| 158 / 159 | Wide 840 dp | side **nav rail** (same five tabs, same pill), widgets three across |

**C1 decision:** every pick-one list (sheets and Customise) uses the **round check badge** on the right (empty ring → filled primary with ✓); no plain ticks.
**C2 decision:** swipe only on Coming up rows (right = Paid / Add now on primaryContainer, left = Skip / Delete); To review and Not understood use buttons.

## Follow-ups (30 Oct) — 161–178 and redrawn 02 · 17 · 22 · 24 · 27 · 44 · 50 · 52 · 57 · 74 · 78 · 81 · 84 · 91 · 139 · 141 · 145 · 149
| PNG | Screen | Decision |
|---|---|---|
| 24 · 02 · 74 · 84 · 139 · 141 | Fixes | Goals tab is a pill with no check; Welcome currency uses the round check; tags are plain words ("Work", no #); wording "never sends your data anywhere"; Pay card bill chips are "Statement" and "Since statement" (no minimum due) |
| 167-category-sheet-grouped | Category sheet | when both kinds are listed (To review, Split, Bills, Txn): **Expense** group, then **Income** group; every category sheet ends with **New category** ("Name, colour and icon") |
| 168-today-not-understood-note | Today | "2 messages not understood · Teach once and messages like it are read", only when To review is empty. **One Today note is enough** |
| 145-auto-capture-off | Auto-capture (off) | "Auto-capture is off" note, the two switches, "2 messages not understood · Teach it the ones it missed" (shown even when off), How it works |
| 171 · 172 · 173 | Coming up swipe | below 96 dp the background stays surfaceContainer with no label; armed it fills (right primaryContainer "Mark paid", left surfaceContainerHighest "Skip") and the label appears |

## Edge cases that must exist (each has a design)
no data · sample data · auto-capture paused · permission denied · can't save yet · foreign currency ·
split not balanced · refund larger than payment · nothing to import · search no results · filter shows nothing · after delete (Undo) · no bills · no
budget · over budget · overdue bill · not enough data for insights · empty range · backup failed ·
wrong backup password · replace-data confirm · wrong PIN · review all caught up · goal reached.
