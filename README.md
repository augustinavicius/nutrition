# Nutrition

A calorie and macro tracker for Android. Scan a barcode, search a global food database,
or add your own foods — and keep the app itself up to date straight from this GitHub
repository.

- **Scan** — point the camera at a product barcode. Known products resolve instantly from
  the on-device cache; unknown ones are looked up in Open Food Facts and cached for next time.
- **Search** — searches your own saved foods and Open Food Facts side by side, debounced so
  typing doesn't hammer the network.
- **Add** — create a food by hand with per-100 g figures, an optional barcode, and a sanity
  check against the energy its macros imply.
- **Recipes** — build a dish from raw ingredients, weigh it once cooked, and log portions by
  weight. Cooking changes what food weighs but not how much energy is in it, so spreading the
  raw total over the cooked weight is what makes a serving come out right.
- **Diary** — a day at a time, split by meal, with a calorie ring, macro bars against your
  goals, and a seven-day trend.
- **Local only** — everything lives in one SQLite database on the device. There is no server,
  no account and no network storage; the only thing the app fetches is Open Food Facts lookups
  and its own releases.
- **Self-update** — watches this repository's releases and installs newer builds in place. No
  account, no sign-in: the repository is public, so the app reads releases anonymously. Pick
  the **stable** channel for releases cut from `master`, or **development** for every build.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 21 and the Android SDK (compileSdk 37, build-tools 36+) to build

## Configuration

Everything configurable is an environment variable, and `.env` at the repository root is where
they live locally. Real environment variables always win over the file, which is how CI feeds
the same settings in from repository secrets with no `.env` present.

```bash
cp .env.example .env      # then fill it in
```

`.env` is gitignored and holds the keystore password, so it is written mode 600.
`.env.example` documents every variable. If `.env` sets `ANDROID_HOME` and there is no
`local.properties`, the build generates one on first run — nothing else to point at the SDK.

## Building

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:assembleRelease        # release APK (debug-signed unless .env names a keystore)
```

## Branches and update channels

| Branch | Channel | Published as | Tag |
| --- | --- | --- | --- |
| `master` | Stable | full release, marked latest | `v1.0.<code>` |
| `development` | Development | pre-release | `v1.0.<code>-dev` |

Day-to-day work lands on `development`, which ships a build to anyone who has chosen that
channel in **Settings → App updates**. Merging into `master` cuts a stable release, which
everyone gets.

The development channel is a superset: it offers whichever release is newest, stable or not.
Marking development builds as GitHub pre-releases keeps the repository's "Latest release"
pointing at the newest stable one, which is what someone installing the first copy by hand
should land on.

`versionCode` is the workflow run number and is shared by both branches, so it only ever
increases no matter which branch built last. One consequence is worth knowing: moving from
development back to stable leaves the device on a higher build than stable offers, so it will
report itself up to date until stable overtakes it. Android will not install a lower
`versionCode` over a higher one, so that is the only honest answer.

## Setting up automatic releases

Every push to `master` or `development` runs `.github/workflows/release.yml`, which tests,
lints, builds a signed release APK and publishes it as a GitHub release. The app polls those
releases. One script covers the setup:

```bash
./scripts/setup-release.sh
```

It creates the signing keystore and records it in `.env`.

If the [GitHub CLI](https://cli.github.com) is installed and signed in (`gh auth login`) it
uploads the secrets too. **It is not required** — without it the script prints the exact values
to paste into **Settings → Secrets and variables → Actions → New repository secret**, and
nothing else in this project needs `gh` on your machine. (The release workflow calls it, but
that runs on a GitHub runner where it is preinstalled.)

| Secret | Purpose |
| --- | --- |
| `APP_KEYSTORE_BASE64` | the keystore, base64-encoded |
| `APP_KEYSTORE_PASSWORD` | keystore password |
| `APP_KEY_ALIAS` | key alias |
| `APP_KEY_PASSWORD` | key password |

The workflow fails fast with an explanatory message if any of these are missing, rather than
publishing a release that cannot be installed.

### The signing key

Android refuses to install an update whose signature differs from the installed app, so every
release must be signed with the **same** key. Keep `release.jks` and its password somewhere
safe — losing them means no existing install can ever be updated again, only uninstalled and
replaced. `.gitignore` excludes `*.jks`.

The script needs `keytool`, which ships with any JDK. With no JDK on `PATH`, point it at one:
`JAVA_HOME=/path/to/jdk ./scripts/setup-release.sh`.

### Installing the first build

The updater can only *update*, so the first copy has to be installed by hand. The repository
is public, so the download needs no credentials:

- **Browser:** open the latest release on github.com and tap the `.apk` asset. On the phone,
  allow your browser or file manager to install unknown apps when prompted.
- **Desktop + USB:** download it the same way (or `gh release download --pattern '*.apk'`),
  then `adb install nutrition-<version>.apk`.

Every release after that one arrives through the app.

### Letting the app install updates

Android requires an explicit per-app grant to install packages. The first time an update is
ready the app offers an *Allow installs* button that opens the right settings screen.

## Release mechanics

- `versionCode` is the workflow run number, which only ever increases — Android will not
  install a lower code over a higher one.
- `versionName` is `1.0.<versionCode>` on `master` and `1.0.<versionCode>-dev` on
  `development`; the tag is `v<versionName>`.
- The release body starts with `versionCode: N` and `channel: <stable|development>` lines.
  Those markers are what the app compares against `BuildConfig.VERSION_CODE` and against the
  channel the user follows; the tag is only a fallback for hand-made releases, and so is
  GitHub's pre-release flag.
- The APK and the R8 `mapping.txt` are attached to every release. The job also prints the
  signing certificate's SHA-256 so you can confirm it never changes.
- Put `[skip release]` in a commit message to push without cutting a release.

## Architecture

Single Gradle module, Kotlin and Jetpack Compose, MVVM with `StateFlow`. Dependencies are wired
by hand in [`AppContainer`](app/src/main/java/io/github/augustinavicius/nutrition/AppContainer.kt) —
one module and a handful of singletons don't justify a DI framework.

```
core/      domain model: Nutrients, Food, DiaryEntry, Recipe, Goals, formatting
data/
  db/      Room entities, DAOs, the starter pantry
  off/     Open Food Facts client and mapping
  prefs/   DataStore settings
  repo/    FoodRepository, DiaryRepository, RecipeRepository
update/    GitHub API, release channels, download, PackageInstaller, periodic check
ui/        Compose screens, one package per screen, plus shared components
```

Two decisions worth knowing about:

**Everything is grams.** Nutrients are stored per 100 g, the basis packaging and food databases
already use, and a logged portion is a weight in grams scaled from that. There are no servings:
one unit throughout means no conversion to get wrong and nothing to choose between when logging.

**A saved recipe maintains an ordinary food.** Rather than teaching search, logging and the
diary about dishes, a recipe writes its finished per-100 g figures into a `Food` row tagged
`RECIPE`. Everything downstream treats a cooked dish exactly like anything else.

**Diary entries snapshot the food they were logged from.** They hold the name and per-100 g
nutrients rather than a foreign key, so correcting or deleting a food never rewrites what a
past day says you ate.

## Attribution

Food data comes from [Open Food Facts](https://world.openfoodfacts.org), made available under
the [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/). Seed values are
rounded from USDA FoodData Central reference entries.
