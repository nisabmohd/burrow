# Savings: final design items 1–8 (8 Nov 2026)

PNGs: `screens/` (light) and `screens/dark/`. `burrow-prototype.html` is clickable; the panel's Savings group has every state.
These build on SAVINGS.md (202–213). Same components only: BurrowSheet, ValueSheet, Calendar, connected group, round check, list rows.

## 1. New deposit sheet (214, 215)
- Add money on a **Once** account › row "Starts a new deposit · At 7.1% · matures 19 Oct 2028 · tap to change" opens **New deposit**.
- Sheet: title "New deposit", subtitle "Inside Fixed deposit · its own rate and maturity".
  - Row `%` "7.1% a year" / "Interest" → ValueSheet in **% mode** (title "Interest rate", sub "A year · as on the bank receipt", display "7.1%"). Starts at the account's rate.
  - "Matures in": connected group **1 year · 2 years · 3 years · 5 years** (default 2).
  - Row "Matures 19 Oct 2028" / "Pays into HDFC Savings" (date = today + years; tapping it can open the one calendar for an exact date).
  - **Done** returns to Add money with the typed amount kept. The row then shows the chosen rate and date (215).
- Saving creates `deposit(v, rate, start, maturesOn, maturityValue)`; the account's own rate is only the default.

## 2. Edit savings (216, 217)
- The edit button on any Savings account opens the **New savings form prefilled**, title "Edit savings", Save top-right.
- Below the form, in this order: **Colour** (12 hues, same as Edit account) · **Icon** (savings icons) · **Count in net worth** switch · **Archive** (red tonal, full width; snackbar "Archived · its history stays").
- Changing How money goes in / How it grows on an account with history only affects new money; past deposits and instalments keep their values.

## 3. Market value for Once and Any time (218, 219; 211 is Every month)
- Header is the same for all three: value · "+₹7,200 on ₹54,000 put in".
- **Once (218):** rows "Put in once · ₹54,000 on 3 Mar 2025" and "Value updated 5 Oct". Main button **Update value** (no Add money, no list).
- **Any time (219):** rows "₹54k put in · Add any time" and "Value updated". Then the **Update value** row, main button **Add money**, list **Money added**.
- Every month (211) unchanged: Next instalment row, Update value row, Pay instalment, Instalments.

## 4. Every month with no end date (220, 221)
- Account: header sub "₹5,000 every month · 13 paid" (no "of N"); rate row "6.8% a year · No end date · stop it any time".
- Accounts row: "Every month · 6.8% · no end date" and the **amount** as trailing (no ring, since there's no end to measure against).
- Data: `instalmentsTotal` nullable.

## 5. Empty lists on a new account (222, 223)
- Instalments: one muted row, calendar icon, "No instalments yet" / "The first one is due on 10 Nov".
- Money added: one row, plus icon, "Nothing added yet" / "Money you add shows here". Section header shows no total.
- Same rows on market-value accounts.

## 6. New savings default rate: empty (224, 225, 226)
- The rate is **empty** by default (a wrong default silently gives wrong maturity values).
- Row reads "Add the rate" / "Interest · needed to work out the value". Save stays off until name, amount and (for Fixed rate) rate are set.
- Tapping the disabled Save marks the row's second line red: "Interest · needed to work out the value" (225).
- Rate entry is the ValueSheet in % mode (226). Market value hides the rate row as before.

## 7. Sunday review with 2+ investments (227, 228)
- One investment: unchanged row "Update 1 investment · Index fund · value from 5 Oct" → that account.
- Two or more: **"Update 2 investments · Index fund, Gold · oldest from 1 Oct"** → sheet **Update values** ("Type what each is worth today"): one row per market account (its circle, name, "₹61,200 · from 5 Oct", chevron) → Update value sheet (212). After Save it returns to the list; updated rows drop off; the review row disappears when none are left.

## 8. Accounts › Loans (210)
- Already designed: row "Home loan · SBI · 8.5% · ends Aug 2038", trailing ring of EMIs paid (%). See 210; no new screen.
