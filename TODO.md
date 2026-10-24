# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.

## Release and stores
- [ ] Push `main` and tag `v0.2.0` (a pre-push hook on the work laptop blocks this repo); create the GitHub release
      with `burrow-0.2.0-github.apk`, `burrow-0.2.0-foss.apk` and `SHA256SUMS.txt` from `build/dist/v0.2.0/`
- [ ] Add the `BURROW_KEYSTORE_BASE64` / password / alias secrets so the Release workflow can sign and publish by itself
- [ ] Google Play: upload `burrow-0.2.0-play.aab` to closed testing; data safety ("no data collected or shared");
      SMS permissions declaration (`release/sms-declaration.md`)
- [ ] F-Droid: merge request for the `foss` build
- [ ] **Rate on Play Store** (play): set `PLAY_LISTED = true` once the listing is live; the About row is wired.
      Maybe a one-time gentle ask after a few weeks (one sheet, never again after "No thanks")
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium

## Features
- [ ] **Google Drive backup**: encrypted backup to the user's own Drive next to the folder backup. Decide the approach
      first (INTERNET + Google sign-in in play; foss can't use Google code). Then update PRIVACY.md, data safety, About
- [ ] New bill: weekly and yearly repeat when creating (monthly only today; editing keeps yearly)
- [ ] Deposits: "Remind me a week before" is saved but no maturity reminder is scheduled
- [ ] Payee names for UPI ids ("thirdwave@okicici" → "Third Wave Coffee") and payee aliases
- [ ] More country rule packs (UAE, UK first; only India is built in), and community packs
- [ ] More languages

## Tests
- [ ] Run the new tests (incl. ValueTyping, PillTabs, ValueSheet, PickAndCards): `./gradlew :app:testFossDebugUnitTest :app:connectedFossDebugAndroidTest`, then `python3 tools/smoke.py`
- [ ] Screen-level tests (Today, New expense, Auto-capture with fake state) once those screens take state instead of view models

## Check on a real phone
- [ ] Backup → restore round trip, CSV export / import (system pickers)
- [ ] Budget alerts (80% / 100%) and bill match from a real bank SMS
- [ ] Restricted settings note after installing the APK from a file (Android 13+)
- [ ] Haptics on a phone without vibration primitives (Vivo / Oppo / Realme)
- [ ] RTL with an RTL language (the emulator falls back to English)
- [ ] 20k-transaction performance test: Activity scrolling and Insights

## Design follow-ups (kit 2026-10-24)
- [ ] Ask the designer to fix PNGs that break agreed rules: 24 (check on the Goals tab), 02 (plain tick, not the round
      check), 74 and New expense 13 ("#Work" tags; tags are plain words), New expense 11 (list date sheet; the rule is
      Yesterday · Today · Tomorrow chips over a calendar), 84 / 139 ("never goes online"; the wording is "never sends
      your data anywhere")
- [ ] Card minimum due (141 "Minimum ₹410" chip): the app doesn't store it yet

## Clean-up
- [ ] Swipe: Coming up builds its own `SwipeToDismissBox`; move it onto the shared `SwipeRow` (or a dismissing variant)
- [ ] Replace the raw dp left inside the starter components (Blocks, Charts, Controls, Lists, ~34) with `Dimens` tokens

## Every change
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone, and at 1.3× / 2× text
- [ ] Shared components only; no raw colour, size or string in screens
- [ ] play and foss merged manifests have no INTERNET (github: only the update check)
- [ ] Committed with a clear message
