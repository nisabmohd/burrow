# Auto-capture and message rules

The core feature: payments record themselves. All parsing is rules, on the phone, no AI, no internet.
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
6. **Ask**: post the capture notification ("₹240 to Third Wave Coffee · HDFC ••1234 · pick a category")
   with two category buttons (remembered payee → category first) and Ignore. One tap saves.
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
banks and UPI apps (19 rules: HDFC, ICICI, SBI, Axis, Kotak, IDFC FIRST, YES, Bank of Baroda, PNB, Canara;
Google Pay, PhonePe, Paytm). Each rule carries an anonymised `example`, and `IndiaPackTest` checks every one. Every pack has tests in `src/test/…/capture/` with real (anonymised) examples.
Import validates every regex (compile + a 50 ms timeout guard against catastrophic patterns) before saving.

## Privacy promises (say them in the intro, keep them in code)
No internet permission at all. Only financial senders/apps are read. OTPs and offers are skipped.
The app keeps the amount and who you paid; message text is deleted after 30 days.
