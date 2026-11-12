# Contributing to Burrow

Thanks for helping. Bug reports, fixes, message rules for your bank, design work and translations are all welcome.

Before you start, read [`CLAUDE.md`](CLAUDE.md). It lists the rules every change follows, whoever writes it. The design rules are in [`docs/DESIGN.md`](docs/DESIGN.md), the shared pieces every screen is built from in [`docs/COMPONENTS.md`](docs/COMPONENTS.md), and open work in [`TODO.md`](TODO.md).

## Set up

You need JDK 17 or later (Android Studio's bundled JDK works; the system JDK 25 is too new) and the Android SDK with API 36.

```sh
git clone https://github.com/nisabmohd/burrow.git
cd burrow
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assemblePlayDebug
```

In Android Studio the active build variant is **playDebug** (Build → Select Build Variant). Each flavour adds its own folder: `src/play`, `src/foss` and `src/github` (the update check). Never run `./gradlew clean` on its own; use `:app:clean`.

## Test

Unit tests cover the money maths, budgets and safe to spend, bills, message rules for every bank pack, the backup format and the shared components:

```sh
./gradlew :app:testPlayDebugUnitTest
```

The smoke suite checks every feature on a connected emulator or phone (Android 15 or older; API 36.1 can't run Espresso). The whole run takes about two hours, so run the part you changed:

```sh
./gradlew :app:assemblePlayDebug
python3 tools/smoke.py                 # all checks
python3 tools/smoke.py review capture  # only the matching checks
```

For UI changes, also look at the screen in dark mode and at the largest font size:

```sh
adb shell cmd uimode night yes
adb shell settings put system font_scale 2.0
```

## Adding a feature

1. Open an issue first if the change is large or adds a dependency. Burrow never adds a library that needs the network.
2. Put logic in `domain/` with unit tests, and UI in the matching `ui/` package, built only from the components in `docs/COMPONENTS.md`.
3. Add a check to `tools/smoke.py`, and follow the `add-feature` skill in `.claude/skills/`.
4. Add a line to `CHANGELOG.md` if users will notice the change.

## Message rules

A payment message no rule reads can be taught in the app (Not understood → Teach). To add a rule pack for your bank or country, see [`docs/CAPTURE.md`](docs/CAPTURE.md) and the `capture-rules` skill; packs written by the community live in [`community-rules/`](community-rules/). Every rule gets a test with a real message (with your own numbers changed).

## Translations

Burrow ships in English, Hindi, Spanish, German, French, Portuguese (Brazil) and Japanese. All UI text is in `app/src/main/res/values/strings.xml`; each language has its own `values-xx/strings.xml` with the same keys.

- **Improve a language:** edit its `values-xx/strings.xml` and open a pull request.
- **Add a language:** copy `values/strings.xml` to `values-xx/`, translate every string, then add the tag to `localeFilters` in `app/build.gradle.kts`.
- Keep every placeholder (`%1$s`, `%2$d`, `%%`) and keep plurals as `<plurals>`. Use the app's words (see [`docs/COPY.md`](docs/COPY.md)) and keep it short and plain.

## Pull requests

Keep each pull request to one change, explain what it does and why, and include a screenshot for anything visual. CI runs the unit tests and builds the flavours on every pull request.

## Releases

See the `release` skill in `.claude/skills/`.
