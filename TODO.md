# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.

## Release and stores
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium

## Features
- [ ] **Google Drive sync** (`play` and `github` only; `foss` keeps the folder backup, no Google code): encrypted
      backup to the user's own Drive next to the folder backup, as Weft 1.5.0 does. Needs INTERNET + Google sign-in in
      play (github already has INTERNET). Then update PRIVACY.md, data safety, About
- [ ] **In-app review and rate** (`play` only): Play In-App Review prompt at a calm moment, and "Rate on Play Store"
      in About opens it, as Weft 1.5.0 does. Ask before adding the Play review library; check the merged manifests

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
