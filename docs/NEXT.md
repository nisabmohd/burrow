# Burrow: next ideas (designed, not merged)

Goal: people keep using Burrow after week one. Every idea saves taps or gives a reason to open the app.
Never a new screen people have to learn. `burrow-next-prototype.html` is clickable (side panel › New ideas).
PNGs in `screens/`. Same design rules as `docs/DESIGN.md`: 24 dp surfaces, pills for everything tappable,
one hero (Today), notes first, no buttons inside cards.

## The daily number
**Safe to spend today** = (left this month + spent today) ÷ days left including today − spent today.
Today's hero line, the home-screen widget and the Sunday review all show this same number. Never below ₹0.

## Add
| # | Screen | What it is |
|---|---|---|
| 02 | Home-screen widget (Glance) | 4×2: Burrow mark + "Safe to spend today", 44 sp amount, "₹13.6k left in October · 13 days", thin progress bar, 56 dp + button (opens New expense). Small 2×2 widgets: Add expense; Next bill (name, amount, "Due today"). primaryContainer surface, system font. Tap anywhere else opens Today. |
| 02 | App shortcuts | Long-press the icon: New expense, New income, Review N payments, Search. Static + one dynamic (Review only when there are payments). |
| 03 | Quick settings tile | "Add expense · Burrow" tile with the Burrow mark; opens New expense directly. |
| 04–05 | Favourites | On New expense (Expense type, empty amount): label "Favourites · one tap saves" + chips (24 dp category circle, name, amount) + "Edit". **One tap saves** the expense to today with an Undo snackbar. Add one with the Favourite chip in the extras row, or from the long-press sheet. Favourites screen: centred header (count), rows with drag handle + remove. |
| 10–11 | Import | More › Import or export: Cashew, Walnut, Money Manager (letter circles, no brand logos), Any spreadsheet (CSV); Restore a backup; Export to CSV. Check screen: count + date range, category mapping rows (unmatched in red "Choose a category"), account mapping, "Skip ones already here" switch (duplicates = same day, amount, payee), one button "Import N transactions". Runs on the phone. |
| 07–09 | Split and refund | Transaction detail gets two rows: "Split across categories" and "Add a refund". Split screen: centred header shows "₹X still to put in a category"; parts are rows (category circle, − amount +); "Add a part"; chip "Put ₹X in <last>"; Save only when parts add up and 2+ parts > 0. Refund sheet: stepper, All of it / Half, "Back into <account>", lowers the category's spending, not income. |

## Improve what exists
| # | Screen | Change |
|---|---|---|
| 12 | Welcome | New step 2 of 3 "How much a month?": stepper, chips 20k/30k/50k/75k, "₹X a day" row, Skip for now / Continue. Steps: currency → budget → accounts. |
| 13–14 | Sunday review | Notification Sunday 6 PM (switch in Notifications, on by default): "Your week: ₹X spent" · "N to review · 2 bills due · ₹X left. Takes 30 seconds." Opens Sunday review: centred header (week total, vs last week), rows: payments to review, bills due this week, budget left + safe today, most-spent category. Bottom button "Save N and finish" saves all reviews with suggested categories. |
| 15 | Trust in capture | Auto-capture opens on a 112 dp ring "92% · Recorded by itself · 131 of 142 payments this month · you typed 11". Teach row: "Teach it N messages it missed". More tile: "92% recorded by itself". |
| 06 | Long-press a row | Any transaction row, 450 ms: sheet with payee + amount, Change category, Duplicate to today, Split, Add a refund, Save as favourite, Delete (red, own group). |
| 16 | Reorder | Customise › Today widgets and Favourites reorder by drag: ⠿ handle on the left, the row lifts (surfaceContainerHigh + shadow), others make room; switch on the right shows/hides. No up/down arrows anywhere. |
| 01 | Customise stays optional | "Customise Today" button removed from Today; only in More. |

## Data
- `favourite`: id, name, amount, categoryId, accountId, sortOrder.
- `txn.refundOf` (refund rows link to the expense), `split` rows already in the schema.
- `import_map`: source app, their category → categoryId, their account → accountId (remembered for next import).
- DataStore: `weeklyReview` (true), `monthlyBudget` set in Welcome.

## Don't add
Cloud sync or accounts · AI chat or "insights" · investments and stocks · shared or household ledgers ·
receipt OCR · streaks and badges · more charts.
