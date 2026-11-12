# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.

## 0.4.0: left for me (emulator)
- [ ] Package: release APKs + AAB + source zip in `build/dist/v0.4.0/` (building now)
- [ ] Compare with the PNGs (light; dark in the app): 17, 36, 44, 52, 63, 91, 116, 117, 149, 170, 175, 178, 234–238;
      savings 202–205, 208, 209, 211, 213–226 and empty states 09, 16, 41, 185, 222, 223
- [ ] Smoke: savings (New savings Once / Every month / Any time / market value, Add money → new deposit, Pay instalment,
      Update value, Edit savings, Sunday review Update values); Teach Type sheet; refund capture with a taught rule
- [ ] Store screenshots (`python3 tools/capture.py`) and the feature graphic, then a last commit (not pushed)

## Needs you
- [ ] Push `burrow-0.4.0`, tag `v0.4.0` (CI makes the GitHub release), upload `burrow-0.4.0-play.aab` to Play
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium
- [ ] Google Cloud: OAuth clients of type Android for `app.burrow.money` with the SHA-1 of the Play app-signing key,
      the release key and the debug key; turn on the Google Drive API
- [ ] Play Console Data safety for Drive backup (`release/data-safety.md`)

## Needs a real phone
- [ ] Drive with a Google account: connect, back up, clear data, restore, compare counts; 11 backups keep 10; switch
      account; disconnect; delete backups; `adb shell cmd jobscheduler run -f app.burrow.money 3101` while charging
- [ ] Budget alerts and bill match from a real bank SMS
- [ ] Haptics on a phone without vibration primitives (Vivo / Oppo / Realme)
- [ ] Activity scroll with 20k transactions (emulator frame times mean nothing)
- [ ] 229–233 in dark mode against `design/screens/dark/`


## Notes
- Smoke takes ~1.5 h on the API 35 emulator with animations off; run a part with `python3 tools/smoke.py <names>`.
  Instrumented tests need API 35 (36.1 can't run Espresso). Use `ANDROID_SERIAL=emulator-5558`.
- Every change: playDebug and fossDebug build, unit tests pass, screen matches its PNG, shared components only, no
  INTERNET in play/foss manifests beyond Drive, committed with a clear message.
