# Auto-capture and message rules

The core feature: payments record themselves. All parsing is rules, on the phone, no AI; messages never leave the phone.
Code: `capture/` (pure Kotlin, unit-tested), `SmsReceiver`, `PaymentNotificationListener`.

## Pipeline
1. **Receive**: bank SMS (`SMS_RECEIVED`, foss/github flavors; play after the Play SMS declaration is
   approved) or a payment-app notification (`NotificationListenerService`, package allow-list: GPay,
   PhonePe, Paytm, bank apps). Anything else is ignored and never stored.
2. **Drop**: OTPs, offers, failed/declined/reversed payments (`RuleEngine.skip`).
3. **Match a rule**: user rules first (newest first), then enabled built-in packs. A rule = sender regex +
   pattern with named groups `amount` (required), `payee`, `account`, `currency`, `balance`.
4. **Dedupe**: same amount + same account or reference within 10 minutes from SMS and app = one entry.
   A payment-app notification often names neither, so a pair from different sources where one has no
   account also counts (`CaptureLogic.findDuplicate`). The second one is stored as MERGED, never asked.
5. **Bill match**: a payee + amount (±10%) matching a bill due within 3 days marks that bill **Paid**.
6. **What happens** follows More › Customise › Transactions › "When a payment is found": **Ask on the notification** (default), **Open it to edit first** (the notification opens New expense pre-filled with amount, payee, account, note = sender), or **Save it for me** (uses the payee's usual category; editable later).
   A payee's **usual category** is the one picked most often for them (ties: the latest). It counts the
   name as saved and the message's own spelling, so "thirdwave@okicici" saved as "Third Wave Coffee" is still known.
   Account digits: banks show 2 to 6 last digits behind X, *, • or dots; rules read any of them and
   matching compares the digits both sides have (at least 2), the longest overlap winning.
   **Category words** (`capture/CategoryWords.kt`): plain words ("petrol", "medicines", "groceries") and brands
   ("Swiggy", "Indian Oil", "Apollo") in the merchant, then the message, point at a default category by its icon
   (so a renamed category is still found). The merchant's usual category comes first (what you chose wins), then
   the words' category, then your most used. Whole words only.
   **Merchant and expense name**: the merchant the message named is kept on the payment, hidden (`TxnEntity.merchant`);
   the expense is named by its plain word when there is one ("Medicines"), else by the merchant. The notification,
   To review and the Transaction screen (a Merchant row) show both; with no merchant the title is "₹500 on Petrol".
   Usual categories and bill matching read the hidden merchant too.
   **Ask**: post the capture notification ("₹240 to Third Wave Coffee · HDFC ••1234 · pick a category")
   with two category pills (the payee's usual category first, then the words' category, then your most used), then Ignore and Edit (opens New expense
   pre-filled, for when neither category fits). One tap saves. The pop-up is shorter: pills + Edit; Ignore is in the shade.
7. **Review**: anything not acted on waits in To review. Messages no rule could read go to
   "messages not understood" → Teach.
8. **Keep**: the message text is kept 30 days for review/teach, then deleted; the transaction stays.

## Message rules screen (49-message-rules.png)
Centred header (count · on · plus built-in pack) · **Yours** (name, sender, language, times read,
switch) · **Built-in packs** per country (India on by default; UAE, UK … off) · Import a rules file ·
Share your rules · FAB **Add rule** → sheet: Paste a message / From messages not understood / Import.

## Rule detail (50-rule.png)
Centred header (sender) · Example message with Amount, Paid to, Account highlighted · What it reads
(Amount, Paid to, Account, Direction Money out / in) · Language and numbers (message language,
number style) · **Test it** (paste any message; parts highlight live) · Use this rule switch · Delete.

## Teach (52-teach-message.png)
"Tap the amount in the message" → tap the amount → payee → account. The app builds the regex from the
taps (literal text around the tapped spans, digits generalised to `[\d,.]+`, the sender as the sender
regex), shows a test on the original message, and saves a user rule.

**Type (12 Nov, 235/236).** The user taps only Amount, Paid to and Account. Burrow fills in the type from the
words in the message (`capture/TypeWords.kt`, tried in this order): Not a payment ("requested", "is due",
"will be debited") · Refund ("refund", "refunded", "reversal", "cashback") · Transfer ("self transfer", "to your
own") · Income ("credited", "received", "deposited") · Expense ("debited", "spent", "paid", "sent", "withdrawn",
"purchase"). Whole words, any case. The Type row is there from the start and can be tapped at any time; Save needs
only the amount (Paid to and Account are optional, any order; 0.4.1). The Type sheet changes it and adds words (`Rule.typeWords`). When the words
agree with the type, the rule decides by words each time (a "credited" message from the same bank is income); when
they don't, the rule keeps the picked type (`Rule.type`). A capture stores its type in `direction`: OUT, IN,
REFUND or TRANSFER. Not a payment is never stored. A refund links to the latest expense with the same payee in the
60 days before, at least as large, and takes its category; with no match it keeps the category picked in Review.
Never income. A transfer has no category and counts as neither spending nor income.

## Number styles
| Style | Example | Notes |
|---|---|---|
| `COMMA_DOT` | 1,234.56 · 1,23,456.78 | India, US, UK, UAE (grouping ignored) |
| `DOT_COMMA` | 1.234,56 | Germany, Spain, Italy, Brazil, Indonesia |
| `SPACE_COMMA` | 1 234,56 | France, Russia, Poland, Sweden |
| `PLAIN` | 1234.56 | some wallets |

## Rules file format (`.burrow-rules.json`)
Shared by people for their bank or country (GitHub, chat). **Never contains message text from a phone.**
```json
{
  "version": 1,
  "name": "Germany – Sparkassen",
  "country": "DE",
  "rules": [
    {
      "id": "de.sparkasse.card",
      "name": "Sparkasse",
      "sender": "(?i)sparkasse",
      "pattern": "Kartenzahlung (?<amount>[\\d.]+,\\d{2}) (?<currency>EUR) bei (?<payee>[^,]+), Konto \\.\\.\\.(?<account>\\d{4})",
      "language": "de",
      "numberStyle": "DOT_COMMA",
      "direction": "OUT"
    }
  ]
}
```
Built-in packs ship as `assets/rules/<country>.json` in the same format; the India pack covers the top
banks and UPI apps (30 rules: money out and money in for HDFC, ICICI, SBI, Axis, Kotak, IDFC FIRST, YES, Bank of
Baroda, PNB, Canara; Google Pay, PhonePe, Paytm; last, `in.any.credit` reads "credited to your A/c" from any bank's
SMS header). On only when the phone is in India. Each rule carries an anonymised `example`, and `IndiaPackTest` checks every one. Every pack has tests in `src/test/…/capture/` with real (anonymised) examples.
The **UAE pack** (`ae.json`: Emirates NBD, ADCB, FAB, Mashreq, any bank's credit SMS) and the **UK pack** (`gb.json`:
Monzo, Starling, Revolut app notifications, then any UK bank app's "£X spent at Y") are on only in their country;
`WorldPacksTest` checks them. Their examples follow the banks' published shapes and still need real messages to confirm.
A pack's name is its country in the phone's language (`ui/rules/PackName.kt`).
**Community packs** live in `community-rules/` in the repository: rules files anyone can send as a pull request
and anyone can open with Import. The app never fetches them (it never goes online).
Import validates every regex (compile + a 50 ms timeout guard against catastrophic patterns) before saving.

## Privacy promises (say them in the intro, keep them in code)
Messages are never sent anywhere. Only financial senders/apps are read. OTPs and offers are skipped.
The app keeps the amount and who you paid; message text is deleted after 30 days.
