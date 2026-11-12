# TODO

Only what is left. Finished work lives in git history and `CHANGELOG.md`; tick a box, then delete it.
Screen numbers point to `design/screens/`.


## Needs you
- [ ] Play Protect / sideload blocking: register in Android developer verification; send the github/foss APKs to the
      Play Protect appeals form; README install steps point to Play, F-Droid or Obtainium
- [ ] Google Cloud: OAuth clients of type Android for `app.burrow.money` with the SHA-1 of the Play app-signing key,
      the release key and the debug key; turn on the Google Drive API
- [ ] When Drive goes on: Google Cloud setup (`~/Downloads/burrow-google-drive-setup.md`), set `DRIVE_BACKUP` true in app/build.gradle.kts, Play Console Data safety (`release/data-safety.md`)

## On your phone (USB debugging; user will connect it)
- [ ] Pending Google Cloud / Play Console setup — Drive with a Google account: connect, back up, clear data, restore, compare counts; 11 backups keep 10; switch
      account; disconnect; delete backups; `adb shell cmd jobscheduler run -f app.burrow.money 3101` while charging


## Notes
- Smoke takes ~1.5 h on the API 35 emulator with animations off; run a part with `python3 tools/smoke.py <names>`.
  Instrumented tests need API 35 (36.1 can't run Espresso). Use `ANDROID_SERIAL=emulator-5558`.
- Every change: playDebug and fossDebug build, unit tests pass, screen matches its PNG, shared components only, no
  INTERNET in play/foss manifests beyond Drive, committed with a clear message.
