---
name: capture-rules
description: Add or fix message rules (bank SMS / payment notification parsing) for a bank, country or language, with tests. Use when a payment message isn't read, when adding a country pack, or when changing RuleEngine.
---

# Message rules

Read `docs/CAPTURE.md` first. Code: `app/src/main/java/app/burrow/money/capture/`; packs: `app/src/main/assets/rules/<country>.json`; tests: `app/src/test/java/app/burrow/money/capture/`.

1. Collect 3+ real messages for the sender (anonymise: change account digits, amounts and names, keep the exact shape and punctuation).
2. Write the failing test first in `RuleEngineTest` (or `<Country>PackTest`): sender + body → expected amount (minor units), currency, payee, account, direction.
3. Write the rule: `sender` regex on the sender id; `pattern` with named groups `amount` (required), `payee`, `account`, `currency`. Pick the `numberStyle`. Keep patterns anchored on stable words ("debited from", "spent on"), never on dates or reference numbers.
4. Guard against slow regexes: no nested quantifiers like `(.+)+`; prefer `[^,]+` over `.*`.
5. Add it to the pack JSON (id `<cc>.<bank>.<kind>`), run `./gradlew :app:testFossDebugUnitTest`.
6. Check OTP / offer / failed messages from the same sender are still dropped.
7. Try it on an emulator: `adb emu sms send <SENDER> "<message>"` → the capture notification appears with the right amount and payee.
