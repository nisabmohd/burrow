# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.

## Release and stores
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium

## Features
- [ ] **Google Drive backup**: encrypted backup to the user's own Drive next to the folder backup. Decide the approach
      first (INTERNET + Google sign-in in play; foss can't use Google code). Then update PRIVACY.md, data safety, About
- [ ] Payee names for UPI ids ("thirdwave@okicici" → "Third Wave Coffee") and payee aliases
- [ ] More country rule packs (UAE, UK first; only India is built in), and community packs


## Tests
- [ ] Fix the 10 component tests that fail (first run 2026-10-30, API 35 emulator; API 36.1 can't run Espresso:
      `InputManager.getInstance` is gone): ButtonGroup "Überweisungen" at 1.3×/2×, PillTabs "Coming up" at 2× and
      scrolling to "Region", ChoiceTiles row heights at 1.3×/2×, Chips (48 dp target, "Food & drinks" overflow),
      ValueSheet (two "₹4,000" nodes). Some may be the Material Button false positive (see FollowUpsTest); then
      `python3 tools/smoke.py`
- [ ] Smoke (0.3.0 run: 50/61 with animations on for the first 19 checks): `split_needs_parts_that_add_up` looks for a
      clickable node with "₹" text, but the amount pill's clickable node has no text (the pill works by hand). The suite
      is slow (3 min a check) where the blinking caret keeps `uiautomator dump` waiting; turning animations off made
      it fast but broke scrolling in More (swipes fling to the end, `scroll_to` only goes down)
- [ ] Baseline profile: the play variant fails in under a second (foss and github pass), so 0.3.0 shipped the 25 Oct profile
- [ ] Screen-level tests (Today, New expense, Auto-capture with fake state) once those screens take state instead of view models

## Check on a real phone
- [ ] Backup → restore round trip, CSV export / import (system pickers)
- [ ] Budget alerts (80% / 100%) and bill match from a real bank SMS
- [ ] Restricted settings note after installing the APK from a file (Android 13+)
- [ ] Haptics on a phone without vibration primitives (Vivo / Oppo / Realme)
- [ ] RTL with an RTL language (the emulator falls back to English)
- [ ] 20k-transaction performance test: Activity scrolling and Insights

## Every change
- [ ] `playDebug` and `fossDebug` build; unit tests pass
- [ ] Screen matches its PNG, light and dark, at 412 dp and on a small phone, and at 1.3× / 2× text
- [ ] Shared components only; no raw colour, size or string in screens
- [ ] play and foss merged manifests have no INTERNET (github: only the update check)
- [ ] Committed with a clear message
