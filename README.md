# Nutrition

A calorie and macro tracker for Android. Scan a barcode, search a global food database,
or add your own foods — and keep the app itself up to date straight from this private
GitHub repository.

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
- **Self-update** — signs in to GitHub, watches this repository's releases, and installs newer
  builds in place.

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

## Setting up automatic releases

Every push to the default branch runs `.github/workflows/release.yml`, which tests, lints,
builds a signed release APK and publishes it as a GitHub release. The app polls those releases.
One script covers the setup:

```bash
./scripts/setup-release.sh
```

It creates the signing keystore, records it in `.env` and prompts for the OAuth client id.

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
| `APP_GITHUB_OAUTH_CLIENT_ID` | client id of the OAuth app the user signs in through |

The workflow fails fast with an explanatory message if any of these are missing, rather than
publishing a release that cannot be installed or cannot be authenticated against.

### The signing key

Android refuses to install an update whose signature differs from the installed app, so every
release must be signed with the **same** key. Keep `release.jks` and its password somewhere
safe — losing them means no existing install can ever be updated again, only uninstalled and
replaced. `.gitignore` excludes `*.jks`.

The script needs `keytool`, which ships with any JDK. With no JDK on `PATH`, point it at one:
`JAVA_HOME=/path/to/jdk ./scripts/setup-release.sh`.

### Signing in with GitHub

The repository is private, so the app needs a credential to read releases and download assets.
It gets one through the OAuth **device flow**: the app shows a short code, you approve it at
github.com/login/device, and the resulting token is encrypted with an Android Keystore key
before it touches disk and is excluded from device backups.

That needs a registration on GitHub, and there are two kinds. Both use the same device-flow
endpoints, so the app code is identical either way — only the blast radius of the token differs.

**A GitHub App (recommended).** Create one at
**Settings → Developer settings → GitHub Apps → New GitHub App**:

- Homepage URL: anything.
- Untick **Webhook → Active**.
- Repository permissions → **Contents: Read-only**. Nothing else.
- Untick **Expire user authorization tokens** — the app stores one long-lived token and has
  no refresh logic, so leaving this on would silently sign you out after eight hours.
- Under **Optional features**/settings for the app, enable **Device flow**.

Then **Install App** and give it access to this repository only. The resulting token can read
exactly one repository's contents.

**An OAuth App (simpler, much broader).** Create one at
**Settings → Developer settings → OAuth Apps**, tick **Enable Device Flow**. There is no
per-repository step, because OAuth App scopes are account-wide: the `repo` scope this needs
grants read *and write* to every repository your account can reach. Fine for a throwaway
account, worth avoiding for your main one.

Either way, copy the **Client ID** into `APP_GITHUB_OAUTH_CLIENT_ID`. It is not a secret — it is
compiled into the APK — but the app cannot reach a private repository without one, so a build
made without it says so plainly instead of offering a sign-in button that could not work.

### Installing the first build

The updater can only *update*, so the first copy has to be installed by hand. The release is
private, so the download needs to be authenticated:

- **Browser:** open the release on github.com while signed in and tap the `.apk` asset. On the
  phone, allow your browser or file manager to install unknown apps when prompted.
- **Desktop + USB:** download it the same way (or `gh release download --pattern '*.apk'`),
  then `adb install nutrition-<version>.apk`.

Every release after that one arrives through the app.

### Letting the app install updates

Android requires an explicit per-app grant to install packages. The first time an update is
ready the app offers an *Allow installs* button that opens the right settings screen.

### Release mechanics

- `versionCode` is the workflow run number, which only ever increases — Android will not
  install a lower code over a higher one.
- `versionName` is `1.0.<versionCode>`, and the tag is `v<versionName>`.
- The release body starts with a `versionCode: N` line. That marker is what the app compares
  against `BuildConfig.VERSION_CODE`; the tag is only a fallback for hand-made releases.
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
  prefs/   DataStore settings, Keystore-encrypted secrets
  repo/    FoodRepository, DiaryRepository, RecipeRepository
update/    GitHub API, device-flow auth, download, PackageInstaller, periodic check
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
