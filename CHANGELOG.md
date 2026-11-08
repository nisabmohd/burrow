# Changelog

## 0.4.0
- A new logo: three arches, a burrow, the same and centred everywhere (app icon, notifications, About, widget, store).
- Google Drive backup (Google Play and GitHub builds): back up to your own Drive, encrypted with your backup password, by hand or on your backup schedule while charging; restore any of the last 10.
- Rate in About opens Google Play's rating card (Google Play build).
- Search in the category sheet.
- Two cards, one bill: a credit card account can hold several cards (ICICI RuPay + Visa). Messages from any of them land on the one account, with one statement, one due and one Pay bill; each payment says which card paid it. Cards with their own bills on one credit line stay separate cards that share the limit.
- New credit card asks for the bank first: with a card from that bank already, choose Same bill or Its own bill. The Bank sheet has search, Your banks and All banks.
- Add account opens a sheet of kinds (Bank account, Credit card, Cash or wallet, Loan, Savings).
- Cards from before can be named (Edit account › Tap to name it).
- Backup and restore shows the folder's name instead of a number.
- Switch account in Google Drive uses a one-person icon; category rows say "Nothing this month".
- Scrolling a list in a sheet back to its top no longer drags the sheet closed.
- A payment you saved or ignored in To review no longer leaves its notification behind; Save all counts only what saved.
- Play Console fixes (as Weft): edge-to-edge without the deprecated status and navigation bar colour calls, R8 shrinks resources with the code, Android Gradle plugin 9.1. The APK is a little smaller (3.2 MB).
- New store screenshots.

## 0.3.2
- Payee names: a UPI id you renamed ("thirdwave@okicici" → "Third Wave Coffee") keeps that name on its next payments.
- Words in a message pick the category ("petrol", "medicines", "Swiggy"), and the expense is named by them; the merchant shows on the transaction.
- Message rules for the UAE and the UK, on only in their country, and community packs anyone can import.
- Today points to payments waiting in To review even before your first transaction.
- ✕ in Paid to clears it while you type.
- Large text: tabs and settings buttons wrap or stack instead of cutting words; choice tiles in a row stay the same height.
- Insights loads only the dates it shows: fast with 20,000 transactions.

## 0.3.1
- New icons: Burrow now uses Solar outline icons (by 480 Design) everywhere, one clean line style. Credited in About › Licences.
- Swipe the Undo bar sideways to dismiss it. The change stays.
- Swiping a row shows what it does from the first touch: green with a check for Mark paid and Add now, grey for Skip, red for Delete, with the icon and the word side by side.
- Typing an amount shows exactly what you typed: "12." keeps its dot and "12.50" its zero, in New expense and every amount sheet.
- A bill's amount, an account's balance, a credit limit, an EMI and a goal's target open the number keypad instead of the phone keyboard.
- Picking a category for a bill or a budget no longer says "No spending this month" for every category.
- Home-screen widgets update as soon as anything changes: bills, budgets, accounts and Hide amounts, not only new payments.
- Clearer words on cards: "Electricity overdue", "spent in the last 7 days", "38% of this month's spending", "Average a day", "You saved 84% of the ₹1.2L you earned", "₹ main currency", and plainer lines under Customise.

## 0.3.0
- New expense: after your favourites, up to four recent payments sit under the amount ("₹240 Third Wave Coffee"). One tap fills the form so you can check and save. Turn them off in Customise › Expense › Recent.
- The date sheet keeps Yesterday, Today and Tomorrow over the calendar, and now has a Time row. Pick a day ahead and it says when the payment will be added.
- Bills can repeat every week, month or year. Weekly bills pick their day from a row of day circles; yearly bills use the same calendar, with the next date shown.
- To review: each payment has Edit and Save. Edit opens it in New expense to change the name, amount or account.
- Messages it couldn't read are easier to find: a note on Today when nothing is waiting in To review, a row at the top of To review, and a link when you're all caught up. Auto-capture lists them even when it's off.
- Teach: the name part is "From / to", and the category is picked in To review, not while teaching.
- Category sheets that list both kinds show Expense, then Income, and every category sheet ends with New category.
- Cards on one credit line can share a limit: Available counts what every card on the line owes.
- Pay card bill: choose the statement amount or everything since the statement.
- Fixed deposits remind you a week before they mature, with the amount, the account it goes to and the date.
- Swipes on Coming up stay quiet until you've dragged far enough, then show what letting go will do.
- Buttons grow with large text instead of cutting their label.
- "Rate on Play Store" in About, now that Burrow is on Play.
- New icons: Burrow now uses Hugeicons, a lighter rounded line set, everywhere in the app.
- Small phones: pick-one pills ("Opens as") go two to a row when a word and its check don't fit, and centre their label. Settings rows wrap a long name to a second line, and titles shrink a step instead of being cut off.

## 0.2.1
- Auto-capture shows what it really does: More says "Paused · Needs access" when Android blocks it, the same as Today and Auto-capture.
- Text never runs into the next line at large font sizes, and wrapped note titles sit closer together.
- Tapping a chip, an amount or a link shows the press inside its shape, not a square around it.
- Filter chips under the search are easier to tap.
- Licences open inside the app with their full text, and list Burrow's own licence (GPL-3.0).
- Built-in message rules are listed like your own; tap one to see what it reads. Your rules can be renamed.
- To review: each payment has its own Save button instead of swiping.
- Teach: tap the word that says money in or out ("credited", "debited"), like the payee; picked parts show a check.
- Auto-capture: Bank SMS always says "Reads messages from banks".
- One way to change any number: a sheet with the value, quick choices and a keypad (budgets, refunds, split parts, fixing a balance, paying a card bill, month start, time). No more − / + buttons.
- Every list where you pick one shows a round check; tabs look the same on Plan and Customise.
- Not understood: each message has a Teach button. Teach can set the payment's category and whether to keep reading messages like it.
- Large text: the nav shows only the open tab's name, widgets and tiles stack, the add button turns into a round +.
- A wrong backup password shows right in the password sheet.
- Times are picked on a scroll wheel (hour, minute, AM/PM) everywhere, and you can choose when bill reminders and the Sunday review arrive (More › Notifications).
- New expense: favourites sit right under the amount as quick-add pills ("₹20 Chai", one tap saves), and the time has its own chip next to Note.
- Big font sizes no longer squash charts and cards: rings stay round, widgets put their chart under the words, Insights' legend and section titles are never cut, Customise tiles go two to a row, and Welcome's buttons stack.
- About shows who makes Burrow: mid apps, with a contact address (tap to write an email).

## 0.2.0
- Check for updates: the GitHub download now finds, downloads and installs new versions itself (Play and F-Droid update it for you).
- Auto-capture tells you when it's really off, and shows how to allow access when Android blocks it after installing from a file.
- Haptics on every phone: taps, toggles and swipes now vibrate on phones that missed them before.
- Same tab pills everywhere (Plan, Customise), with the label always centred.
- Fixes: decimal tiles no longer cut off, goal icons fit every screen, "All days" on the chart is fully visible, small days show as dots.
- A slightly bigger app icon.
- Clearer privacy wording: Burrow never sends your data anywhere.

## 0.1.1
- A calmer New expense: the keypad stays put, category chips, one "Account · Today" line, and Note, Receipt and Repeat or lend chips. Everything fits on one screen.
- Plan › Coming up: bills and future-dated payments in one list. Swipe right to mark paid or add now, left to skip.
- Payments dated ahead are added on their day with a quiet notification, and Safe to spend now counts what is still due this month.
- Today and Activity show what is coming up this week; Activity opens on the list with the chart folded.
- Simpler date picker: Yesterday, Today, Tomorrow and a calendar that fits every phone.
- Insights: Top payee shows the next two payees; the transaction screen has Edit up top and Delete at the bottom.
- German, Spanish, French, Portuguese (Brazil) and Japanese.
- Detects India from your time zone, so the rupee is picked first; the UPI pack only turns on in India.
- Even spacing and alignment across every screen.
- Money in from every big Indian bank: credit messages from ICICI, SBI, Axis, Kotak, IDFC, YES, Bank of Baroda, PNB and Canara, plus a catch-all for other banks.
- Messages not understood: a list like To review, where each one can be taught or ignored; Teach has Ignore too.

## 0.1.0
- First beta. Add expenses, income and transfers with a keypad; Paid to suggestions that set the usual category.
- Auto-capture from bank SMS and payment apps: one tap saves a payment; message rules for any bank and language.
- Bills, subscriptions and EMIs with reminders; budgets with alerts; goals; accounts, cards, loans and deposits.
- Insights: spending by category, patterns and a month heatmap.
- Customise Today, the New expense form, what happens when a payment is found, region and currency.
- Notes, tags, repeating payments, receipts, lend or borrow, and future-dated (upcoming) payments.
- Encrypted backups, CSV export and import, app lock with PIN or fingerprint, hide amounts.
- English and Hindi. No internet permission: nothing leaves your phone.
- Payment notification: pick one of two categories in one tap, or Edit to open it filled in.
- Reads account numbers with any number of digits; learns each payee's usual category.
- 60 category icons for where money really goes: rent, recharge, fuel, EMI, insurance, gold and more.
