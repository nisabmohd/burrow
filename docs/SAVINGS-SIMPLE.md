# Savings, simplified to the bank-page pattern (9 Nov 2026)

This replaces the layout in SAVINGS.md and SAVINGS-FINAL.md. The behaviour and data are unchanged. All savings PNGs (202–228) were re-captured, light and dark.

## Account screen = the bank page (56)
1. **Centred header:** circle, name, amount, one short line.
2. **One list group** (like Fix balance / Transfer): the action rows, at most one info row, then **Count in net worth** (a switch).
3. **One history section:** rows built like transaction rows (account circle, "What · date", "From HDFC Savings", amount on the right).

There are **no filled buttons**, no reminder switch, no check badges and no "Next" tags. The actions are rows.

| Kind | Header line | Rows | History |
|---|---|---|---|
| Once · fixed (202) | worth today · ₹2,28,430 at maturity | Add money / Starts a new deposit · 7.1% a year / Pays into HDFC Savings on … | Deposits: "Deposit · 12 Mar 2025" / "7.1% · matures 12 Mar 2027" / ₹2,00,000 |
| Every month · fixed (203, 220) | ₹5,000 a month · 13 of 36 paid (or "no end date") | Pay instalment / ₹5,000 due 12 Nov · 6.8% a year / ₹1,99,800 when it ends (or "Stop it any time") | Instalments: "Instalment 13 · 12 Oct" / From HDFC Savings / ₹5,000 |
| Any time · fixed (204) | 7.1% a year · no end date | Add money / Any amount, any time · ₹13,100 a year / Interest at this rate (hidden at ₹0) | Money added: "Added · 5 Oct" |
| Market · every month (211) | +₹7,200 on ₹54,000 put in | Update value / Last updated 5 Oct · Pay instalment | Instalments |
| Market · once (218) | same | Update value · Put in ₹54,000 / On 3 Mar 2025 | none |
| Market · any time (219) | same | Update value · Add money | Money added |

**Empty history (222, 223):** the illustrated empty state (COMPONENTS.md › Empty state) replaces the list: two ghost rows with the account's own icon, then "No instalments yet" / "Each ₹3,000 you pay shows up here. The first one is due on 12 Nov.", then a tonal **Pay first instalment** button (or **Add money**). The matching action row is hidden while it's empty.

## New savings (208, 209, 213, 224, 225)
- Name · **Money goes in** (Once / Every month / Any time) · **Grows by** (Fixed rate / Market value) · one list: amount, rate, ends, the account it's from.
- No helper paragraphs under the groups. An empty rate reads "Add the rate / Needed to work out the value", and that second line turns red after a tried Save.

## Edit savings (216, 217) = Edit account (106)
- Preview header (icon in its colour, name, kind) · Name · Colour · Icon (same 12 as Edit account) · amount/rate/ends/from rows · Count in net worth · **Archive account**.
- How money goes in and how it grows can't be changed after creation, so those groups are not shown.

## Add money and New deposit (205, 214, 215)
- The Add money row is "New deposit · 7.1% · matures 19 Oct 2028 ›". It opens New deposit (subtitle is just the account name): Interest row, Matures in 1/2/3/5 years, maturity row, Done.

## Empty states app-wide (09, 16, 41, 185, 222, 223)
Every empty or no-results state now uses the same illustrated component: Today with nothing, Activity search with no results, Insights with no spending, category search with no results, and empty savings. Done-type screens (Nothing to teach, deleted) keep the cookie shape with a check.
