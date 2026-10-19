---
name: release
description: Cut a Burrow release — version bump, checks, baseline profile, changelog, tag, store assets. Use when asked to release, publish or prepare a version.
---

# Release

1. All boxes for the version ticked in `TODO.md`; `python3 tools/smoke.py` passes on an emulator.
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Regenerate the baseline profile: `./gradlew :app:generateBaselineProfile`, commit it.
4. Build release: `./gradlew :app:assemblePlayRelease :app:assembleFossRelease :app:bundlePlayRelease`. Check APK < 5 MB and the merged manifest has no INTERNET.
5. Copy outputs to `build/dist/v<version>/` (keep older versions).
6. Add a `## <version>` section to `CHANGELOG.md` ("- " lines become What's new).
7. Commit, tag `v<version>`, push the tag: CI builds signed APKs, SHA-256 sums and the GitHub release.
8. Play: upload the AAB to the track; update screenshots (`python3 tools/store_shots.py`), feature graphic (`design/logo/play-feature-graphic-1024x500.png`), data safety ("no data collected or shared"), and the SMS permission declaration if SMS capture is enabled for play.
