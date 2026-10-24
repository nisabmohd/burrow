# Burrow: designs still needed

UI that is built and working in the app but has **no design PNG yet**, or has **changed since its PNG**.
For each: where it lives, what is on it, the states to draw, and the existing PNGs to match for style.

**Everything follows the existing kit** (see `INDEX.md` for every current PNG): Rose 345 seed, Lexend 400/500,
six text styles, 4 dp grid, 16 dp screen margin, 24 dp surfaces, pills for everything small or tappable, round
checkboxes. Draw each at 412 dp wide, light. Where it matters, also at the **largest text (2×)**.

---

## A. New screens and sheets (no PNG at all)

### A1. Messages not understood (screen 116)
More › Auto-capture › "2 messages not understood".
- Cards like To review (44): sender + time, ✕ ignore, the message text, a **Teach** chip.
- Swipe right = Teach, swipe left = Ignore (Undo snackbar). *(See C2: this may move to buttons like To review.)*
- States: list of 1–3 messages · empty state (cookie shape, "Nothing to teach").
- Match: `44-review.png`, `52-teach-message.png`.

### A2. Built-in rule pack (India) (new)
More › Auto-capture › Message rules › tap "India".
- Centred header: globe icon, "India", the number of rules, line "Built in · read-only".
- One row: "Use this pack" with a switch.
- Section "Rules in this pack": rows exactly like "Yours" on 49: SMS icon circle · rule name · "HDFCBK · 12 payments" · chevron.
- Tap a row → Rule (50), read-only.
- Match: `49-message-rules.png`, `50-rule.png`.

### A3. Rename rule sheet (new)
Rule (50) for your own rules: a **pencil** icon button in the top bar opens it.
- Bottom sheet: title "Rename rule", one pill text field (pencil icon, hint "HDFC card, salary…", ✕ clear), Save (off while empty).
- Also draw: Rule (50) top bar with the pencil (your rule) and without it (built-in rule).
- Match: `50-rule.png`, `74-expense-note-sheet.png` (sheet with one field).

### A4. Paste a message sheet (from Add rule, 51)
Message rules › Add rule › "Paste a message".
- Sheet: title, one line, two pill fields (the message, who sent it), Continue (off until there is text).
- Match: `51-add-rule-sheet.png`.

### A5. Licences + licence text (from About, 72)
About › Licences.
- **Licences list**: grouped rows, each: name ("Burrow", "AndroidX (…)", "Phosphor Icons", "Lexend font", …) · licence
  ("GNU General Public License v3.0", "Apache License 2.0", "MIT License", "SIL Open Font License 1.1") · chevron.
- **Licence text**: top bar with back + licence name; the item's name as a small title; then the full licence as
  readable paragraphs (body style, muted), long scroll (GPL is ~35 KB).
- Match: `72-about.png`, `54-more-settings.png`.

### A6. Check for updates + Update sheet (GitHub build only)
About (72): a new first row "Check for updates" (download icon). Its line changes:
- "From GitHub Releases" → tap → "Checking…" → "You're on the latest version" **or** opens the Update sheet;
  "Couldn't check. Try again later" (error colour).
- F-Droid build: same row, line "Updates come from F-Droid", not tappable. Play build: opens the store.

**Update available** bottom sheet:
- Title "Update available", line "Version 0.3.0 · you have 0.2.1".
- A "What's new" block (surfaceContainerLow, 24 dp): up to five bullet lines with small primary dots.
- Footer: Later (tonal) + Update (filled).
- States: **ready** · **downloading** (wavy progress + "Downloading… 42%", Update looks off) · **downloaded, needs
  permission** (hint "Allow Burrow to install apps, then tap Install", button Install) · **downloaded** (Install) ·
  **failed** ("The download didn't finish" in error colour, button "Open release page").
- Also shown once a day on app start (same sheet).
- Match: `72-about.png`, `32-budget-edit-sheet.png` (sheet + footer), the wavy bar in `04-today.png`.

### A7. New goal (form)
Plan › Goals › Add goal.
- Top bar: ✕ · "New goal" · Save (off until name + target).
- Name field (pencil icon), Target field (currency icon, number keyboard), "By when" row (month/year or "No date").
- **Icon**: a card with 8 circles in **two rows of four** (flight, shield, laptop, home, school, gift, car, flag),
  each its own colour; the chosen one has a primary ring.
- Error line when Save is tapped too early.
- Match: `33-goal.png`, `106-edit-account.png` (icon grid card), `27-new-bill.png`.

### A8. Sheets that have no PNG
All are `BurrowSheet`s (title + one muted line + ✕, body, pinned footer):
- **Time** (New expense › Time extra): hours/minutes picker + Done.
- **Type an amount** (tap the amount on Split / Refund / Welcome budget): amount display + keypad + Done.
- **Text** (generic one-field sheet: payee, names): title, one pill field, Done.
- **Option** (Customise: e.g. swipe right / swipe left action): pick-one list + checks.
- **Add a currency** (Customise › Currency): search field + list of currencies.
- **Fix balance** (Account › "Fix balance"): current balance, − amount +, Save.
- **Pay in** (card / loan: "Pay card bill" / "Pay EMI"): from account, amount, date, Save.
- **Backup password** (Backup: set / enter password, wrong password state).
- Match: `32-budget-edit-sheet.png`, `68-month-start-sheet.png`, `84-exchange-rate-sheet.png`, `19-category-sheet.png`.

### A9. Wide screens (tablet / unfolded phone)
The bottom nav becomes a **side nav rail** (same five tabs, same icons and selected pill).
- Draw Today and Activity at 840 dp wide with the rail.
- Match: `04-today.png`, `11-activity.png`.

---

## B. Changed since their PNG (redraw these)

### B1. Auto-capture (48): real on/off and permission notes
- Switches show **on only when Android allows it** (SMS permission, notification access).
- **Capture off** (both switches off or blocked): a Note first, "Auto-capture is off · Turn on Bank SMS or Payment
  apps to record payments by themselves"; only the two switches and "How it works" show (no "When a payment is
  found", Message rules, Try it).
- **Blocked after installing from a file** (Android 13+ Restricted settings): warning Note "Android is blocking
  access · Burrow was installed from a file. In App info tap ⋮ › Allow restricted settings, then turn it on here
  again" with an "App info" button.
- Bank SMS line is always "Reads messages from banks" (no bank list).
- Match: `48-auto-capture.png`, `07-today-capture-paused.png` (warning note).

### B2. More (53): Auto-capture tile states
- **On** · "92% recorded by itself" / "12 this month".
- **Paused** · "Needs access" (turned on, but Android blocks it). New state.
- **Off** · "Not set up".
- Match: `53-more.png`.

### B3. To review (44): a Save button on each card, no swiping
- Each card: circle, payee, account · time, amount, ✕ (ignore); the message with parts highlighted; category chips + More;
  **a full-width "Save" button (check icon) at the bottom of the card**.
- Bottom "Save 3 payments" stays.
- This is the one place a card has a button (user's call).
- Match: `44-review.png`.

### B4. Teach (52): "Money in or out" + checks
- A **fourth row "Money in or out"**: you tap "credited" / "debited" in the message like the payee. The row then
  reads "debited · Money out".
- Instruction for that step: "Tap the word that says **money in or out** (credited, debited), or skip it."
- **Every row shows a round check** on the right once that part is picked (empty ring until then), like PickList (80).
- The row being picked now is tinted (as today).
- Match: `52-teach-message.png`, `80-customise-today.png` (round checks).

### B5. Tabs: Plan (22–24) and Customise (80–85)
- Plan's Coming up / Budgets / Goals: equal pills, **label centred, no check mark**; selected = primary.
- Customise's five tabs use **the same pills**, at their own width, scrolling sideways (were outlined chips).
- Match: `22-plan-bills.png`, `80-customise-today.png`.

### B6. Customise › Currency (83): decimal tiles
- Three tiles (Auto 1,240.5 · None 1,240 · Always 1,240.00): the check sits on the **title line only**; the value
  below gets the full width and shrinks to fit (never "1,240....").
- Match: `83-currencies.png`.

### B7. Activity chart card (110 / 12): "All days"
- With a day picked, the header shows **"All days"** as plain primary text (not a button), fully visible in the
  24 dp header row, next to the fold arrow. Small days draw as round dots, never flat blobs.
- Match: `110-activity-chart-open.png`, `12-activity-day.png`.

### B8. About (72): new rows
- Order: **Check for updates** (A6) · Source code · **"Your data stays yours · Burrow never sends your data
  anywhere"** (shield icon; replaces "No internet permission") · Rate (Play only) · Report a problem · Licences.
- Match: `72-about.png`.

### B9. Capture intro (45–47): privacy card wording
- The small "No internet" card now reads **"Nothing sent"** (shield icon); the page's line says "Burrow never sends
  your data anywhere. It keeps only the amount and who you paid."
- Match: `46-capture-intro-2.png`, `47-capture-intro-3.png`.

### B10. Notes with long titles (07, 06) at large text
- A note title that wraps ("These are sample / transactions", "Auto-capture is / paused") stays one tight block
  above its line; draw 06 and 07 at 1.3× and 2× text.
- Match: `06-today-sample-data.png`, `07-today-capture-paused.png`.

### B11. Rule (50): built-in vs yours
- Yours: pencil in the top bar (A3), "Use this rule" switch, Delete rule.
- Built-in: no pencil, no switch; footer line "Built-in · India".
- Match: `50-rule.png`.

---

## C. Decisions to make in the design

### C1. One look for "pick one from a list"
Today two looks exist: sheets (Type 111, Account 20, Option) mark the choice with a **plain check**; Customise lists
(80, 85) and Teach use a **round radio/check badge**. Pick one and draw both 111 and 80 with it.

### C2. One look for swipe
Coming up (22), Messages not understood (A1) and transaction rows (11) swipe with slightly different backgrounds.
Draw one swipe style (colour, icon, label each way), or decide which of them become buttons like To review (B3).

### C3. Large text and small phones
For the main screens (Today, New expense, Activity, Auto-capture, More) a version at **320 dp wide with 2× text**:
how rows, notes and tiles wrap.
