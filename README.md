# Burrow

A private, open-source expense tracker for Android. It records payments by itself from bank
messages, reminds you of bills and shows what is left. It never sends your data anywhere.

- Rules for contributors and AI agents: [`CLAUDE.md`](CLAUDE.md)
- Design: [`docs/DESIGN.md`](docs/DESIGN.md), screens in [`design/screens/`](design/screens/)
- Plan: [`TODO.md`](TODO.md)

## Build
```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testFossDebugUnitTest :app:assemblePlayDebug :app:assembleFossDebug
```
Use Android Studio's JDK: the system JDK 25 is too new for the Android Gradle Plugin.
