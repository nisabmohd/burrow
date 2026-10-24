---
name: verify-app
description: Build the app and check every feature on an emulator with the smoke suite. Use after a change, before committing a release, or when asked to verify, test or check the app.
---

# Verify the app

1. Build with Android Studio's bundled JDK if the system JDK is too new for the Android Gradle Plugin:
   ```sh
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testFossDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug
   ```
2. Make sure an emulator or phone is connected (`adb devices`). If none: `~/Library/Android/sdk/emulator/emulator -avd <AVD name>` in the background, then `adb wait-for-device`.
3. During development, check only the part you changed by hand. Before a release, run the whole smoke suite (installs a fresh debug build with sample data, one check per feature, fails on touch targets under 48 dp):
   ```sh
   python3 tools/smoke.py            # everything
   python3 tools/smoke.py capture    # only checks whose name contains "capture"
   ```
4. For a failure, look at the screen before guessing: `adb exec-out screencap -p > /tmp/s.png`, or list labels with `python3 -c "import sys; sys.path.insert(0,'tools'); import device as d; print([n.label for n in d.dump() if n.label])"`.
5. For UI changes also check dark mode (`adb shell cmd uimode night yes`) and the largest text (`adb shell settings put system font_scale 2.0`). Reset both afterwards.

## Pitfalls
- Snackbars last about 4 s and a UI dump takes 1–2 s: read the snackbar and tap Undo from one dump.
- Tap by label, never coordinates; layouts shift.
- For alarms and reminders, schedule them for the next minute instead of waiting.
- Emulator images without root: set the date with `adb shell cmd time_detector set_auto_detection_enabled false` then `adb shell cmd alarm set-time <epoch ms>`.
- Screenshots: status bar demo mode (`adb shell settings put global sysui_demo_allowed 1`, then `am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930`, and `battery`, `network -e wifi show -e level 4 -e fully true`, `notifications -e visible false`).
6. Check the play and foss merged manifests have no INTERNET (github has it for the update check only): `grep -c INTERNET app/build/intermediates/merged_manifests/playDebug/*/AndroidManifest.xml` must print 0 (or only the `tools:node="remove"` line before merging).
7. Compare the changed screens with their PNGs in `design/screens/` (light) and `design/screens/dark/`.
8. Test SMS capture on an emulator: `adb emu sms send HDFCBK "Rs.240.00 debited from A/c XX1234 to VPA thirdwave@okicici. UPI Ref 4821."`
