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

## Entities
`account`, `category` (parentId for sub-categories), `txn`, `bill` (repeat rule, onDue ASK/RECORD/REMIND),
`bill_occurrence` (one row per due date: UPCOMING/PAID/SKIPPED, never auto-skipped), `budget`
(month + optional category, carry-over), `goal`, `capture_rule` (rule JSON + enabled + uses),
`capture` (message, status, 30-day text retention). Later: `card` (limit, statement day, due day),
`loan` + `instalment`, `savings`, `payee` (aliases + default category), `split`, `tag`.

## DataStore keys
theme, dynamicColor (false), accentHue (345), font ("lexend"), textSize, languageTag, mainCurrency,
monthStartDay (1), numberStyle, smsCapture, notificationCapture, enabledPacks, appLock, lockAfter,
hideAmounts, backupFolderUri, backupSchedule, lastBackupAt, firstOpenAt, txnCount, sampleBannerHidden,
homeWidgets, supportPromptShownAt, supportPromptDismissCount.

## Backup (no cloud, no sign-in)
- One encrypted file (`burrow-2026-10-19.burrow`): JSON of all tables, zipped, AES-GCM with a key from
  the user's password (PBKDF2-HMAC-SHA256, 200k iterations, random salt + IV in the header). Uses only
  `javax.crypto`, no library.
- Saved by hand or on a schedule to a folder the user picks (Storage Access Framework). Pointing it at a
  cloud app's folder keeps a copy off the phone without Burrow going online. Keep last 7.
- Restore replaces everything after a confirm dialog; wrong password = dialog, nothing changes.
- CSV export/import of transactions (date, payee, amount, currency, category, account, note).
- Android Auto Backup is OFF (`allowBackup=false`, data extraction rules exclude everything).

## Sample data
First open seeds a realistic month (flagged `isSample`), with the "These are sample transactions"
note. Removed on the first real transaction or by "Remove".
