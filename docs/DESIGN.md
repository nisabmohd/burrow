# Burrow design guidelines

Burrow is a small, private expense tracker for Android. It records payments by itself from
bank messages, reminds you of bills, and shows what is left. These are the rules every screen follows.
The live reference is the prototype (`design/prototype/burrow-prototype.html`, not in git) and the
PNGs in `design/screens/` (light) and `design/screens/dark/`.

## The feel
Clean, calm, Material You Expressive, minimal, as if Google made it. Not a clone of any other app.
Rose accent, Lexend, soft tonal surfaces, round shapes, no clutter. A screen should read in three seconds.

## The ten rules
1. **One hero in the whole app.** Only Today's budget block is a filled colour block (primaryContainer).
2. **Every other screen starts with the centred header**: 64 dp icon circle, title, big plain amount,
   one line, optional thin progress bar. Same for lists (Accounts, Budgets, Goals, Subscriptions,
   Categories, Message rules) and details (account, bill, goal, budget, category, transaction).
3. **One signal per number.** A ring *or* a bar, plus one short line ("₹710 over", "₹5,310 left").
   Never ring + bar + % + red text together.
4. **No buttons inside cards.** Tap the row to open it. One main action per screen: the FAB, one
   bottom button, or the single "Mark paid" on the Next-up card.
5. **Notes go first.** Any notice (overdue, paused, suggestion, goal reached) is the first thing on
   the page, in the one `Note` component. Colour by kind only (info rose, warning amber, error red).
6. **One shape rule.** 24 dp surfaces; circles/pills for everything small or tappable.
7. **Filters are one component.** Funnel + calendar round buttons in the header → one Filter sheet
   (Type, Accounts, Categories as wrapping chips, fits one screen) and one Dates sheet. Active
   filters become removable chips under the search bar with "Clear all".
8. **Every picker is a bottom sheet.** Category, account, dates, day (stepper), widgets, budgets.
   Dialogs only confirm delete, replace data or a wrong password.
9. **One chart style.** Pill bars with a dashed average; rings with a gap; the circle heatmap.
   Charts are Compose Canvas, no library.
10. **Short words.** A title and one line. Explanations live in the first-time intros, not on settings
    pages. No exclamation marks, no "Oops", no "Awesome".

## Screens, by tab
- **Today:** hero budget → note (review / sample / paused) → widget grid (This week, Next bill,
  Net worth, Top category, Goal, Money in and out) → Recent. "Edit home" toggles widgets.
- **Activity:** header funnel + calendar → search → active-filter chips → 14-day pill chart card
  (tap a day) → day groups.
- **Plan:** Bills | Budgets | Goals (connected button group).
  Bills: overdue note → Next up card → Subscriptions + EMIs widgets → This week / Later rows
  (amount over "in 6 days", no tick buttons). Budgets: centred header + category rows with rings.
  Goals: centred header + goal rows with rings, FAB "Add goal".
- **Insights:** header funnel + calendar → spend chart vs previous period → A day + Biggest →
  By category → Patterns (Busiest day, No-spend days, You kept, Top payee) → month heatmap.
  Everything stays on this one page.
- **More:** four equal status tiles (Auto-capture, Backup, App lock, Appearance) → Money (Accounts,
  Categories) → Customise (Today screen, New expense form, Transactions, Language and region, Currency) →
  App (Font and text size, Notifications, Hide amounts, Export) → About.

`docs/SCREENS.md` has every screen and nested screen in detail.

## Simple, not bare (user, 2026-10-20)
Power lives one level down, never on the main screens:
- **New expense** keeps three rows (Category, Account, Date) and the keypad. Extras are chips under the
  amount (Note, Repeat, Attach, Lend or borrow); each opens a sheet and shows its value once set.
  Which chips appear is chosen in More › Customise › New expense form.
- **Customise** (More) is ONE screen with tabs Today · Expense · Transactions · Region · Currency, built from the same four blocks:
  choice tiles (pick one), toggle tiles (pick many), ordered rows (show/hide + move up/down), switch rows.
  Any future setting that customises a screen goes here, using these blocks.
- **Region** and **Currency** are tabs of Customise: language, number and date format, week and month start;
  primary currency, symbol/code, decimal places, typed-in exchange rates.
- Every account has "Count in net worth" (cards and loans off by default).


## Reasons to come back (user, 2026-10-23 — final)
Every feature must **save taps or give a reason to open the app**, never add a screen people must learn.
- **The daily number:** "Safe to spend today" = (left this month + spent today) ÷ days left incl. today − spent today, never below 0.
  Same number on Today's hero line, the home-screen widget and Sunday review.
- **Outside the app:** home-screen widget, app-icon shortcuts, quick-settings tile. Widgets use the system font.
- **Favourites:** one tap saves (with Undo). Added by long-press › Save as favourite.
- **Long-press any transaction** opens the action sheet; tap still opens the detail.
- **Reorder is drag only:** ⠿ handle on the left, the row lifts; no up/down arrows.
- **Sunday review** is the only weekly notification; one button finishes it.
- **Customise stays optional:** defaults must already feel right; nothing on Today points to Customise.
- **USP, say it everywhere:** It records itself · Private by design · Know what's left today · Works with any bank.
- **Don't add:** cloud sync or accounts · AI chat · investments · shared ledgers · receipt OCR · streaks and badges · more charts.

## Calm pass (user, 2026-10-23)
Reference screens for the calm style: More, Appearance, Customise › Region, settings lists — flat lists, one idea per group, nothing repeated.
- **New expense:** title "New expense ▾" opens the type sheet (Expense / Income / Transfer); amount alone with a blinking caret; **keypad always visible**;
  Paid to; favourites chips (empty form only); **category chips** (4 most-used + More); one line "HDFC Savings · Today" (each part
  tappable); one chip row: Note · Receipt · **Repeat or lend** (one chip → sheet: Once, Repeat, Subscription, Lent, Borrowed + how often / person).
  Amount, payee and category stay visible with the keypad open, no scrolling.
- **Plan › Coming up** (was Bills): one list grouped Overdue (red) · This week · This month · Later, each with its total. Bills and
  one-off future payments together; a small icon tells repeating (↻) from one-off (📅). Swipe right = Paid / Add now, left = Skip / Delete.
  No Next up card; Subscriptions and EMIs are two rows under "Every month" at the bottom.
- **Insights** keeps its widgets with small graphs (user: "like old graph only", no list view): spent chart → A day (sparkline) ·
  Biggest → By category → Patterns: Busiest day (bars) · No-spend days (ring) · You kept (ring) · Top payee (next two payees with
  amounts, no repeated icons) → month heatmap (top day only, no repeated month total).
- **Activity** opens on the list: the chart is one summary row ("₹13,738 spent · 28 transactions · last 14 days") that expands.
  A folded "3 coming up" row sits on top; upcoming items never mix into the history.
- **Today** shows 2 widgets by default (This week, Top category) and a "₹X coming up this week" row. No buttons in widgets.
- **Transaction detail:** Duplicate and Edit in the app bar; Delete is a red row at the bottom.
- **Kept from earlier the same day (user):** tags are plain words (never "#Work"); the date sheet is Yesterday · Today · Tomorrow chips over a month calendar (not the old list of rows).

## Upcoming (user, 2026-10-23)
- Anything with a future date is **upcoming**: saved to Coming up (snackbar "Saved · Upcoming on Sat, 24 Oct"), added on its day
  with a quiet notification ("Rent ₹18,000 added today", Undo in the app). Upcoming income shows "+", never coloured.
- **Safe to spend counts what is coming up** (option, on by default — Customise › Transactions › "Coming up lowers safe to spend"):
  safe today = (left this month − unpaid upcoming expenses due by month end, excluding transfers like card bills and EMI principal
  + spent today) ÷ days left − spent today.
- Empty: "Nothing coming up. Bills and future-dated payments show here."

## Widgets (Today and Insights)
Half-width widgets are exactly 184 dp tall so rows are even. A widget alone on a row switches to
the wide layout (text left, 104 dp ring right, 16 dp padding) and wraps its content. Widgets show
an icon + label, a 22 sp value, one line and a 60 dp visual at the bottom.

## Accessibility
- Contrast 4.5:1 for text (3:1 for 24 sp+). Every colour pair in `TOKENS.md` passes in both themes.
- Touch targets ≥ 48 dp. Icon-only buttons have a content description.
- Never colour alone: "over" and "overdue" are also in words.
- Font scale: everything reflows; only text inside fixed shapes (rings, nav) is capped at 1.3×
  (`CappedFontScale`).
- TalkBack: rows read as one item ("Netflix, ₹649, due today, button"). Charts have a summary
  description ("Last 14 days, ₹13,738 spent, highest Friday ₹2,980").
- Respect "Remove animations".

## Internationalisation
- Every string in `strings.xml`; plurals for counts. English and हिन्दी first, more by translators.
- Per-app language (Android 13+) via `localeConfig`; applies instantly.
- Numbers and currency follow the locale (1,00,000 in India, 100,000 elsewhere), changeable in More.
- Copy never assumes a country. Sample data follows the user's region currency.
- RTL works: use start/end, never left/right; mirror chevrons and back arrows.

## Dark theme
Same structure; tones from `TOKENS.md`. Category circles get darker containers with light icons.
Check every new screen in both themes before calling it done (`design/screens/dark/` for reference).
