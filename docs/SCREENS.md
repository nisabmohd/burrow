# Screens

Every screen, top to bottom, with its PNG (`design/screens/`). Build each to match its PNG at 412 dp
width, light and dark. "→" means what a tap opens.

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
| 10-edit-home-sheet | Edit home | switch per widget |

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
| 79-expense-with-extras | extras set | chips under the amount show their value: "Every month", "1 file", "Lent to Rahul"; a future date shows "Upcoming · added that day" |
| 87-payee-typing | Paid to | a **free-text field** right under the amount ("Paid to" / "Received from"): type anything. Up to 4 quiet outlined suggestion chips (frequent payees; payers for income) show below and filter as you type; tapping one fills the text and sets its usual category. Typing a known payee exactly also sets it. Clear with ✕ |
| 74-expense-note-sheet | Note | Note · tag chips (#Work, #Trip, #Family, #Reimbursable) · Done |
| 75-expense-repeat-sheet | Repeat | Never / Every week / Every month / Every year · "It is a subscription" · "Remind me a day before". A repeat creates a Bill in Plan |
| 76-expense-attach-sheet | Attach | Take a photo / Choose a photo / Choose a file · attached files with remove. Files stay in app storage, included in backups |
| 77-expense-lend-sheet | Lend or borrow | None / I lent / I borrowed · name · "Remind me to settle". Shows under Accounts › Lent and borrowed |
| 78-expense-date-sheet | Date | Yesterday / Today / Tomorrow / Next Monday · Pick a date and time. Future = **upcoming**: listed in Plan, added to Activity on the day |

## Plan
| PNG | Screen | Layout |
|---|---|---|
| 22-plan-bills | Bills | Bills/Budgets/Goals group · overdue note (Mark paid) · Next up card · Subscriptions + EMIs widgets · This week / Later rows (amount over "in 6 days") · FAB Add bill |
| 25 / 26 | Bill / overdue bill | centred header (amount, "Due today" / "Was due 17 Oct" in red) · rows Category / Paid from / Repeats / Ask me on the day (switch) · Paid before (account circle rows) · Skip / Mark paid |
| 27 / 28 | New bill / due-day sheet | Bill/Subscription · Name · Amount · rows Category / Paid from / "Every month on the 1st" → DayStepper · When it is due (Ask me / Record it for me / Only remind me) |
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
| 44-review | To review | cards: payee, account, time, amount, ✕ · SMS with amount/payee/account highlighted · category chips + More · bottom "Save 3 payments". Empty: "All caught up" |
| 48-auto-capture | Auto-capture | centred status (142 payments recorded) · Bank SMS / Payment apps switches · "2 messages not understood" → Teach · Message rules · Try it / How it works |
| 49 / 50 / 51 | Message rules / Rule / Add rule sheet | see `docs/CAPTURE.md` |
| 52-teach-message | Teach | "Tap the amount in the message" · SMS with tappable parts · Amount / Paid to / Account rows fill as you tap · Save |

## More and settings
| PNG | Screen | Layout |
|---|---|---|
| 53 / 54 | More | 4 equal status tiles · Money (Accounts, Categories) · Customise (Today screen, New expense form, Transactions, Language and region, Currency — all open the one Customise screen on that tab) · App (Font and text size, Notifications, Hide amounts, Export or import CSV) · About (About Burrow, Delete all data) |
| 55–59 | Accounts / bank / card / loan / deposit | centred headers; card: statement amount, due date, Since statement / Available rows, Pay bill; loan: left to pay, EMIs paid, Next EMIs, Prepay; deposit: maturity amount, reminder |
| 60 / 61 / 62 | Categories list / grid / edit | centred header (13 · spent this month) · list sorted by spend with % (or 3-column tiles via header toggle) · FAB "New category" · edit: name, colour circles, icon circles in a card, Delete |
| 63 / 64 / 65 | Appearance / Font / Language | Light/Dark group, colour swatches, Dynamic colour switch · font rows with preview card + Text size group · language rows with native names |
| 66 / 67 / 68 | Notifications / Main currency / Month start sheet | switches with a live notification preview · searchable currency rows · DayStepper |
| 80–85 | **Customise** (one screen; tabs as a scrollable chip row: Today · Expense · Transactions · Region · Currency) | Same four blocks on every tab: **choice tiles** (pick one, selected = primaryContainer + check), **toggle tiles** (pick many, 3 columns, check badge), **pick lists** (one-of-many rows with a round radio), **ordered rows** (icon · name · ↑ ↓ · switch) and switch/plain rows. Today: top card pick list (Budget left / Spent / Balance, value as subtitle) · widgets as ordered rows · recent 3/5/10 · review switch. Expense: extras toggle tiles (Note, Repeat, Attach, Lend, Time, Last category) · opens as Expense/Income/Transfer · default account. Transactions: "When a payment is found" as 3 wide choice tiles (Ask on the notification / Open it to edit first / Save it for me) · list toggles · swipe rows |
| 83 / 84 | Customise › Currency / rate sheet | primary currency row · Show amounts as: choice tiles Symbol ₹1,240 / Code INR 1,240 · Decimal places: Auto 1,240.5 / None 1,240 / Always 1,240.00 · Other currencies rows ("1 USD = ₹84.10 · set 12 Oct") + Add · rate stepper sheet |
| 85-language-region | Customise › Region | Language row · Numbers: pick list (example as title, region as subtitle, round radio right) · Dates: pick list (Written out / Day first / Month first / Year first) · Week: connected button group Monday / Sunday / Saturday · Month starts on row → DayStepper |
| 86-account-card-net-worth | Card detail | "Count in net worth" switch (off by default for cards and loans); Accounts header says what is excluded; "Lent and borrowed" section lists people |
| 69 / 70 / 71 / 72 | Backup / App lock / PIN / About | plain lists; PIN keypad with wavy lock shape; About with the logo, version, source, licences |

## Edge cases that must exist (each has a design)
no data · sample data · auto-capture paused · permission denied · can't save yet · foreign currency ·
split not balanced · search no results · filter shows nothing · after delete (Undo) · no bills · no
budget · over budget · overdue bill · not enough data for insights · empty range · backup failed ·
wrong backup password · replace-data confirm · wrong PIN · review all caught up · goal reached.

## Next round (burrow-next, 2026-10-22) — PNGs in `design/next/`, spec in `docs/NEXT.md`
| # | Screen | What |
|---|---|---|
| 01 | Today | hero line "left of ₹30,000 · ₹608 safe to spend today"; no Customise Today button |
| 02 | Home screen | widget 4×2 Safe to spend today (+ opens New expense); 2×2 Add expense, Next bill; long-press shortcuts New expense · New income · Review N (only when some wait) · Search |
| 03 | Quick settings | "Add expense · Burrow" tile |
| 04 | New expense | "★ Favourites · one tap saves" + Edit, chips; Favourite chip in the extras (Customise › Expense) |
| 05 | Favourites | centred header with the count, drag rows with ✕, how to add one; empty state |
| 06 | Long-press a row | actions sheet, Delete in its own group |
| 07 | Transaction | Split across categories · Add a refund rows; Recorded from SMS box; a refund links its expense |
| 08 | Split payment | parts with − amount + (tap to type), Add a part, "Put ₹X in …" chip, Save when they add up |
| 09 | Refund sheet | − amount +, All of it / Half, Back into account, Save refund |
| 10 | Import or export | Cashew, Walnut, Money Manager (letter circles), Any spreadsheet; Restore a backup; Export to CSV |
| 11 | Check before import | count + range, Columns (spreadsheet), Categories (unmatched in red), Accounts, Skip ones already here, Import N |
| 12 | Welcome 2 of 3 | How much a month? − amount +, chips, "₹968 a day", Skip for now / Continue |
| 13 | Sunday review notification | Sunday 6 PM: "Your week: ₹X spent" · "N to review · 2 bills due · ₹X left. Takes 30 seconds." |
| 14 | Sunday review | week header vs the week before, four rows, "Save N and finish" |
| 15 | Auto-capture | 112 dp ring "92% · Recorded by itself", "Teach it N messages it missed · From …"; More tile "92% recorded by itself" |
| 16 | Customise › Today | drag to reorder widgets |
| 17 | Notifications | Sunday review switch (row opens this week's review) |
