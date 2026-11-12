# Data

Room (SQLite) + DataStore Preferences. Schema v1 is in `starter/…/data/BurrowDatabase.kt`.

## Money
- Amounts are `Long` minor units (paise, cents). Never Float/Double. Interest maths in `BigDecimal`,
  rounded once at the end, HALF_EVEN (`Money.kt`, `emi()`).
- Each transaction stores its currency, the rate it used and the base amount, so history never changes.
- An account's balance = opening balance + its transactions (cached, always recomputable).
- **Transfers** are two linked rows and never count as spending or income: card bill payment, ATM
  withdrawal, EMI principal, savings instalment, "Add money" to a goal.
- **Refunds** link to the original expense and reduce that category.
- **Split** expenses: parts must add up before Save is enabled.
- EMI interest is an expense in "Fees & interest"; principal is a transfer to the loan account.

## Customisation that changes data
- **Upcoming**: a transaction dated in the future is stored with `status = UPCOMING`, shown in Plan, and
  becomes POSTED on its day (alarm at midnight + on app open). Balances ignore it unless "Count upcoming in balances".
- **Repeat** on a new expense creates a `bill` (kind BILL or SUBSCRIPTION) and links the first occurrence to the txn.
- **Lend / borrow**: `txn.personId` + `txn.lend` (LENT / BORROWED); a `person` table keeps names; the person's
  balance = lent − borrowed − settlements. Settle up records a linked transaction.
- **Attachments**: files copied into app storage (`files/attachments/<txnId>/`), `attachment` rows; compressed
  to ≤ 1600 px JPEG; included in backups; deleted with the transaction.
- **Tags**: `tag` + `txn_tag` (#Work, #Trip…), filterable in Activity.
- **Net worth**: `account.includeInNetWorth` (cards and loans default false).
- **Shared credit line** (v6): `account.sharesLimitWith` = the card whose `creditLimit` this card uses (its own is
  cleared). Available = that limit − what every card on the line owes. One step only (`creditLine()`), so no loops.
- **Cards on one bill** (v8): `card` (accountId, name, number, sortOrder). A card account holds one or more cards;
  its `number` is kept as the first card's digits. Capture matches any card's digits to its account
  (`withCards`), and `txn.cardId` records the card only when the account has two or more (`matchCard`); with one
  card left, every `cardId` of that account is cleared. The v7 → v8 migration and restoring an older backup give
  each card account with digits one card (empty name, shown as "Card"). Backups v4 carry `cards`.
- **Merchant and expense name** (v7): `txn.merchant` = the merchant the bank message named, kept out of sight (the
  payee is the expense's own name, "Medicines"); usual categories, aliases and bill matching read it. `capture.name` =
  the expense name the message's words gave ("Petrol"), when they gave one. See CAPTURE.md.
- **Exchange rates**: `rate` (currency, perPrimary as BigDecimal text, setAt); each txn keeps its own `fxRate`.

## Entities
`account`, `category` (parentId for sub-categories), `txn`, `bill` (repeat rule, onDue ASK/RECORD/REMIND),
`bill_occurrence` (one row per due date: UPCOMING/PAID/SKIPPED, never auto-skipped), `budget`
(month + optional category, carry-over), `goal`, `capture_rule` (rule JSON + enabled + uses),
`capture` (message, status, 30-day text retention). Later: `card` (limit, statement day, due day),
`loan` + `instalment`, `savings`, `payee` (aliases + default category), `split`, `tag`.

## DataStore keys
homeTopCard, homeWidgetsOrder, homeRecentCount, homeShowReview, formExtras (note,repeat,attach,lend), formDefaultType, formDefaultAccount, formLastCategory, showTime, onPaymentFound (ASK / EDIT_FIRST / SAVE), countUpcoming, confirmDelete, swipeRight, swipeLeft, numberFormat, dateFormat, weekStart, currencyDisplay (SYMBOL / CODE), decimals (AUTO / 0 / 2), theme, dynamicColor (false), accentHue (345), font ("lexend"), textSize, languageTag, mainCurrency,
monthStartDay (1), numberStyle, smsCapture, notificationCapture, enabledPacks, appLock, lockAfter,
hideAmounts, backupFolderUri, backupSchedule, lastBackupAt, driveAccount, driveWifiOnly (true), lastDriveBackupAt,
driveBackupError, driveBackupCount, reviewAskedAt, firstOpenAt, txnCount, sampleBannerHidden,
homeWidgets, supportPromptShownAt, supportPromptDismissCount.

## Backup (folder; Drive below)
- One encrypted file (`burrow-2026-10-19.burrow`): JSON of all tables, zipped, AES-GCM with a key from
  the user's password (PBKDF2-HMAC-SHA256, 200k iterations, random salt + IV in the header). Uses only
  `javax.crypto`, no library.
- Saved by hand or on a schedule to a folder the user picks (Storage Access Framework). Pointing it at a
  cloud app's folder keeps a copy off the phone without Burrow going online. Keep last 7.
- Restore replaces everything after a confirm dialog; wrong password = dialog, nothing changes.
- A backup carries every table (rules included; message text never) and every setting the user chose:
  theme, currency, month start, Today widgets, rule packs, hide amounts, notifications and their times, and
  every Customise tab (v5). Left out, as they belong to the phone: PIN / app lock, backup folder and password,
  Drive account, the capture switches (permissions are granted again). Older backups keep today's values for
  what they don't carry.
- CSV export/import of transactions (date, payee, amount, currency, category, account, note).
- Android Auto Backup is OFF (`allowBackup=false`, data extraction rules exclude everything).

## Drive backup (play and github, 2026-11-08, ported from Weft 1.5.0)
- Backup › Google Drive: Connect (Google's account picker; needs the backup password first, then runs a
  first backup), Back up to Drive now, Restore from Drive (sheet of the last 10, then the same password +
  "Replace?" flow as a file), Wi-Fi only (on), Google account sheet: Switch account / Disconnect (backups
  stay) / Delete backups from Drive (confirm dialog). Built only from Backup's own rows and sheets.
- Each backup is the **same encrypted `.burrow` bytes** as a folder backup (sealed with the backup password),
  named `burrow-backup-<ISO time>.burrow` in `drive.appdata` (hidden; only Burrow sees it), with
  `appProperties` device + txns. Newest 10 kept. Google can't read them.
- Scope `drive.appdata` + `email`; `AuthorizationClient` (play-services-auth) for consent, Drive v3 over plain
  `HttpURLConnection` (no Google API client). Code: `src/drive` (play, github), stand-in `src/noDrive` (foss).
- Schedule: Backup's Automatic backup (Daily / Weekly) drives both; Drive uses one persisted JobScheduler job
  (`DriveBackupJob`, id 3101): next 1 AM (weekly: Monday 1 AM), charging, unmetered when Wi-Fi only. Skipped
  within 6 h of a manual backup; a dropped connection retries. No WorkManager.
- Errors (snackbar): offline, Wi-Fi only on mobile data, Drive full, Google needs you to allow Burrow again,
  couldn't reach Drive, Drive unticked on Google's screen. 401 → token cleared and asked again once; 429/5xx
  → two retries. One at a time (a mutex).
- Owner setup: Google Cloud OAuth clients (Android) for `app.burrow.money` with the SHA-1 of the Play app-signing
  key, the release key and the debug key; Google Drive API on. See TODO.md.

## In-app review (play only)
About › Rate opens Play's own rating card (`src/play` `PlayReview`; the store page if Play can't show it).
Play's card also shows once when To review empties, after 30 real transactions and at most every 120 days
(`domain/ReviewAsk.kt`). Burrow draws nothing of its own for it.

## Sample data
First open seeds a realistic month (flagged `isSample`), with the "These are sample transactions"
note. Removed on the first real transaction or by "Remove". The Electricity bill's last due date before today
stays unpaid (no BESCOM payment that month), so Coming up always has an Overdue example, early in the month too.

## v5 (2026-10-22, burrow-next)
- `TxnEntity.refundOf` + type `REFUND`: money back for an expense. Raises its account, lowers that
  category's spending (SQL `spent` / `spentByCategory` subtract it), never counts as income.
- `SplitEntity(txnId, categoryId, amount)`: a split payment. Parts always add up to the payment and
  there are 2+ of them; the payment stays one row in Activity, its own category becomes the biggest
  part. Category totals in SQL use the parts; `expandForStats` unfolds them for Insights, Today's
  widgets and Category insights. Changing the amount or type in Edit drops the split.
- `FavouriteEntity(name, amount, categoryId, accountId, sortOrder)`: one-tap expenses (04, 05).
- `ImportMapEntity(source, kind, theirName, targetId)`: another app's category / account names →
  Burrow's, remembered for the next import (11).
- Backups are version 3 and carry splits, favourites and import mappings.
- DataStore: `weeklyReview` (Sunday review, on by default), `formFavouriteOff`. The Welcome budget is
  a normal overall `BudgetEntity` from this month on (not a DataStore key), so Budgets shows it.
- The daily number: safe to spend today = (left this month + spent today) ÷ days left including
  today − spent today, never below 0 (`domain/Next.kt`). Today, the widget and the Sunday review.

## Calm pass (2026-10-23)
- Upcoming one-off = `txn` with `at` in the future and `status = UPCOMING`. `runBillReminders` (app open + the
  bill alarm, which also wakes at the next upcoming time) posts the due ones and sends "Rent ₹18,000 added today" (115).
  Repeating ones are `bill`s. Upcoming items never show in Activity's history or any total.
- DataStore: `upcomingLowersSafe` (true): Safe to spend takes off unpaid expenses due before the period ends
  (bills and upcoming one-offs; card bills, EMIs and transfers excluded). `homeWidgets` defaults to week + topcat.

## Review sorted (Room v11, 12 Nov)
- `CaptureEntity.categoryPicked` (default false): set when the user picks the category in Review (the Review sheet's chips,
  More, or What was it for? 241). To review's **Ready to save** = a transfer, a refund, a picked category, or the category
  Burrow *knows* (`CaptureRepository.knownCategory`: the merchant's usual category, else the message's words). Anything else
  waits under **Pick a category** ("Burrow isn't sure"); "Save N ready" never saves those.

## Index on transfers in (Room v10, 0.4.1)
- `TxnEntity` gains an index on `toAccountId` (auto-migration 9 → 10): an account page reads its own rows,
  paid from it or moved into it, with one indexed query. No column changes; backups are unchanged.

## Savings (Room v9, backup v6)
- `AccountEntity.savingsMode` ONCE / MONTHLY / ANY (null = before v9: ONCE with a maturity date, else ANY); `marketValue`,
  `marketGain` (typed value minus balance at the time) and `valueUpdatedAt`. Worth = balance + marketGain.
- ONCE: the first deposit is the account's principal / interestRate / startDate / maturityDate; each later Add money is a
  transfer in with a `DepositEntity(txnId, accountId, interestRate, maturityDate)`. A transfer in without one uses the account's rate and term.
- MONTHLY: emi = each month, payoutAccountId = from; one `BillEntity(kind = SAVINGS, toAccountId = account)`; paying is a transfer.
  Savings instalments never lower safe to spend.
- All money in is a transfer, never spending. Opening a Once or Any time account moves no money (its amount is the opening balance).
