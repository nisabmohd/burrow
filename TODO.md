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
- [ ] Re-take store screenshots 01–07 with today's UI (`tools/store_shots.py`; 08 is done)
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
- [ ] Rename a message rule (the pencil on 50)
- [ ] More country rule packs (UAE, UK first; only India is built in), and community packs
- [ ] More languages

## Tests
- [ ] Run the new tests: `./gradlew :app:testFossDebugUnitTest :app:connectedFossDebugAndroidTest`, then `python3 tools/smoke.py`
- [ ] Screen-level tests (Today, New expense, Auto-capture with fake state) once those screens take state instead of view models

## Check on a real phone
- [ ] Backup → restore round trip, CSV export / import (system pickers)
- [ ] Budget alerts (80% / 100%) and bill match from a real bank SMS
- [ ] Restricted settings note after installing the APK from a file (Android 13+)
- [ ] Haptics on a phone without vibration primitives (Vivo / Oppo / Realme)
- [ ] RTL with an RTL language (the emulator falls back to English)
- [ ] 20k-transaction performance test: Activity scrolling and Insights

## Clean-up
- [ ] Swipe: Coming up, To review and Messages not understood build their own `SwipeToDismissBox` with their own
      background; move them onto the shared `SwipeRow` (or a dismissing variant of it) so every swipe looks and feels the same
- [ ] Pick one from a list: sheets (Type, …) mark the choice with a plain check, Customise lists with a round radio
      (`PickList`); pick one look for both
- [ ] Replace the raw dp left inside the starter components (Blocks, Charts, Controls, Lists, ~34) with `Dimens` tokens

## Every change
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone, and at 1.3× / 2× text
- [ ] Shared components only; no raw colour, size or string in screens
- [ ] play and foss merged manifests have no INTERNET (github: only the update check)
- [ ] Committed with a clear message
