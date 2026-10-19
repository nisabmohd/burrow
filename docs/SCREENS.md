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
| 17 / 18 | New expense / transfer | close · title · Save · Expense/Income/Transfer group · ₹ amount (display) · Category / Account / Date rows · Payee / Note · keypad. Save disabled until amount + category; tapping Save early marks the missing row red |
| 19 / 20 | Category / Account sheet | list rows with circle + name + one line (spent this month / balance), ✓ on selected, "New category" |
| 21-transaction | Transaction | centred: category circle, payee, −₹240 (display), date · rows Category / Paid from / Note · "Recorded from SMS" box · Delete / Edit · header Duplicate |
| 73-delete-dialog | Delete | dialog, Undo snackbar after |

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
| 43-capture-notification | notification | "₹240 to Third Wave Coffee · HDFC ••1234 · pick a category" · two category buttons · Ignore |
| 44-review | To review | cards: payee, account, time, amount, ✕ · SMS with amount/payee/account highlighted · category chips + More · bottom "Save 3 payments". Empty: "All caught up" |
| 48-auto-capture | Auto-capture | centred status (142 payments recorded) · Bank SMS / Payment apps switches · "2 messages not understood" → Teach · Message rules · Try it / How it works |
| 49 / 50 / 51 | Message rules / Rule / Add rule sheet | see `docs/CAPTURE.md` |
| 52-teach-message | Teach | "Tap the amount in the message" · SMS with tappable parts · Amount / Paid to / Account rows fill as you tap · Save |

## More and settings
| PNG | Screen | Layout |
|---|---|---|
| 53 / 54 | More | 4 equal status tiles · Money (Categories, Accounts, Main currency, Month starts on) · App (Language, Font and text size, Notifications, Hide amounts, Export or import CSV) · About (About Burrow, Delete all data) |
| 55–59 | Accounts / bank / card / loan / deposit | centred headers; card: statement amount, due date, Since statement / Available rows, Pay bill; loan: left to pay, EMIs paid, Next EMIs, Prepay; deposit: maturity amount, reminder |
| 60 / 61 / 62 | Categories list / grid / edit | centred header (13 · spent this month) · list sorted by spend with % (or 3-column tiles via header toggle) · edit: name, colour circles, icon circles in a card, Delete |
| 63 / 64 / 65 | Appearance / Font / Language | Light/Dark group, colour swatches, Dynamic colour switch · font rows with preview card + Text size group · language rows with native names |
| 66 / 67 / 68 | Notifications / Main currency / Month start sheet | switches with a live notification preview · searchable currency rows · DayStepper |
| 69 / 70 / 71 / 72 | Backup / App lock / PIN / About | plain lists; PIN keypad with wavy lock shape; About with the logo, version, source, licences |

## Edge cases that must exist (each has a design)
no data · sample data · auto-capture paused · permission denied · can't save yet · foreign currency ·
split not balanced · search no results · filter shows nothing · after delete (Undo) · no bills · no
budget · over budget · overdue bill · not enough data for insights · empty range · backup failed ·
wrong backup password · replace-data confirm · wrong PIN · review all caught up · goal reached.
