# Savings: once, every month, any time (8 Nov 2026)

PNGs in `screens/` (light) and `screens/dark/`. Prototype: side panel › "Savings".

One Savings account kind with one choice, **How money goes in**. No bank-product names in the UI (no FD / RD / PPF).
| PNG | Screen | Design |
|---|---|---|
| 202 | Savings · **Once** | header: worth today, "₹2,28,430 at maturity"; rows: put in · rate, pays into, remind a week before; **Add money**; **Deposits** group (one row per deposit: amount at rate, from → matures, maturity value) |
| 203 | Savings · **Every month** | header: saved so far, "₹5,000 every month · 13 of 36 paid"; rows: next date + source, rate + value when it ends, remind the day before; **Pay instalment**; **Instalments** (next = "Next", paid = round check) |
| 204 | Savings · **Any time** | header: balance, "grows at 7.1% a year · add any time"; rows: added this year + yearly interest at this rate, no end date; **Add money**; **Money added** list |
| 205 | Add money (Once) | ValueSheet: amount + keypad; From account (transfer, not spending); Date; row **"Starts a new deposit · at 7.1%, matures in 2 years"** — a once-kind account can't take more, so adding makes another deposit inside it |
| 206 | Pay instalment | same ValueSheet prefilled with the monthly amount; Pay |
| 207 | Add money (Any time) | same ValueSheet, any amount; Add |
| 208 / 209 | New savings | Name ("Deposit, emergency fund, child's future…"); **How money goes in**: Once · Every month · Any time (connected group) + one helper line; Amount / Each month / Starting amount; Interest % a year; Ends (Matures … or No end date for Any time); From account |
| 210 | Accounts › Savings | rows "Once · 7.1% · matures Mar 2027" / "Every month · 6.8% · ends Jun 2028" (ring = instalments paid) / "Any time · 7.1% · no end date" |
| 190 | Add account | Savings: "Once, every month or any time" |

Data (later): `savings.mode` ONCE / MONTHLY / ANY; `deposit` rows (amount, rate, start, maturity) for ONCE; instalments as bill occurrences for MONTHLY; additions are transfers into the account for ANY. All money in is a **transfer**, never spending.

# Savings · How it grows (8 Nov) — 211–213
A second choice on Savings, next to How money goes in: **How it grows** — Fixed rate · Market value. Covers SIPs, funds, shares, gold, crypto and pensions worldwide without naming them, and without going online.
| PNG | Screen | Design |
|---|---|---|
| 211 | Savings · Market value | header: worth now, "+₹7,200 on ₹54,000 put in" (gain or loss in words, never coloured); rows: next instalment, "Value updated 5 Oct · Market value · Burrow never looks it up"; **Update value** row; Pay instalment; Instalments |
| 212 | Update value | ValueSheet: today's value + keypad, line "+₹9,500 on ₹54,000 put in", Cancel · Save |
| 213 / 208 / 209 | New savings | **How it grows**: Fixed rate · Market value (connected group + helper line). Market value hides Interest and defaults Ends to "No end date" |
| 101 | Sunday review | row "Update 1 investment · Index fund · value from 5 Oct" when a market-value account was last updated 30+ days ago |
| 210 | Accounts › Savings | "Every month · market value · updated 5 Oct", worth on the right |

Not added on purpose: live prices, holdings, buy/sell, XIRR, charts.

Data (later): `savings.grows` FIXED / MARKET; MARKET stores `value` and `valueUpdatedAt` (typed by the user); "put in" = the sum of transfers in.
