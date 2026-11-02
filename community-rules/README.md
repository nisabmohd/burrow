# Community rules

Rules files for banks and payment apps that Burrow's built-in packs don't read yet. Burrow never goes
online, so the app doesn't fetch these: download a file and open it with
**More › Auto-capture › Message rules › Import a rules file**.

## Add one

1. In Burrow, teach the app your bank's messages (Not understood › Teach), then **Share your rules**.
   The shared file holds only the patterns, never your messages.
2. Name it `<country>-<bank>.burrow-rules.json` (for example `de-sparkasse.burrow-rules.json`) and put it here.
3. Add one anonymised `example` per rule: change the amount, the account digits and the names, and keep the
   exact wording and punctuation.
4. Open a pull request. Rules that read well for several people move into a built-in pack.

The format is in `docs/CAPTURE.md` § Rules file format. Patterns must not use nested quantifiers like `(.+)+`;
Import refuses a pattern that takes too long.
