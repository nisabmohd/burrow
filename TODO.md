# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.

## Release and stores
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium

## Google Drive backup (built 2026-11-08; needs the owner's setup before it works)
- [ ] Google Cloud: OAuth clients of type Android for `app.burrow.money` with the SHA-1 of the Play app-signing key,
      the release key and the debug key; turn on the Google Drive API (`drive.appdata` and `email` aren't restricted)
- [ ] On a phone with a Google account: connect, back up, clear data, restore, compare counts; back up 11 times and
      check 10 are listed; switch account; disconnect; delete backups; run the job while charging with
      `adb shell cmd jobscheduler run -f app.burrow.money 3101`
- [ ] Play Console Data safety for Drive backup (`release/data-safety.md`), as Weft's `docs/PLAY-DATA-SAFETY.md`
- [ ] Smoke checks: Drive rows (not connected / connected / failed), account sheet, foss without Drive; category search

## Two cards, one bill (designs 186–201 built and checked on the emulator 2026-11-08)
- [ ] Smoke check in `tools/smoke.py` for two cards on one bill (Add account › Credit card › Same bill, SMS from each
      card's digits, "Paid with"), and restoring a v3 backup (cards come back from the accounts' digits)

## Tests
- [ ] Smoke takes ~2 h on the API 35 emulator (each `uiautomator dump` ~2.5 s); run a part with
      `python3 tools/smoke.py <check names>`. Instrumented tests need API 35: API 36.1 can't run Espresso
      (`InputManager.getInstance` is gone)

## Check on a real phone
Done on the emulator with `tools/phone_checks.py` (2026-11-02, 8/8): backup ↔ restore, CSV, budget alerts, bill match,
category words, restricted settings note, RTL (ar-XB pseudolocale), 20k transactions. Still needs real hardware:
- [ ] Budget alerts and bill match from a real bank SMS (the emulator's SMS follow the banks' published shapes)
- [ ] Haptics on a phone without vibration primitives (Vivo / Oppo / Realme)
- [ ] Activity scroll jank with 20k transactions (the emulator draws in software: its frame times mean nothing)

## Every change
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone, and at 1.3× / 2× text
- [ ] Shared components only; no raw colour, size or string in screens
- [ ] play and foss merged manifests have no INTERNET (github: only the update check)
- [ ] Committed with a clear message
