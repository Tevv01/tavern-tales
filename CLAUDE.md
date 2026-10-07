# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tavern Tales: an Android-only app (Kotlin, Jetpack Compose, Material 3) for D&D table ambience: scenes of layered looping sounds/music mixed live, with Philips Hue lights synced per scene. Single `:app` module, package `dev.tevv.taverntales`.

## Environment (Windows dev machine)

- No system JDK suitable for Gradle is on PATH (only Java 8). Set `JAVA_HOME` to Android Studio's JBR before running Gradle:
  - bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
  - PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
- `local.properties` (gitignored) points `sdk.dir` at `C:/Users/vetle/AppData/Local/Android/Sdk`. `adb` lives in that SDK's `platform-tools/` and is not on PATH.

## Commands

```sh
./gradlew assembleDebug                      # build debug APK
./gradlew installDebug                       # install on connected device (USB debugging)
./gradlew test                               # all JVM unit tests
./gradlew :app:testDebugUnitTest --tests "dev.tevv.taverntales.SomeTest"   # single test class
./gradlew lint
```

## Build setup notes

- Versions live in `gradle/libs.versions.toml`; add dependencies there, not inline.
- AGP 9 with **built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin. The Kotlin version comes from the `kotlin.compose` plugin applied in the root build file (it puts KGP on the classpath). Other Kotlin compiler plugins (e.g. serialization) should use `version.ref = "kotlin"`.
- `compileSdk = 37` is required by current AndroidX libraries; `targetSdk` is 36.
- The app is always dark-themed (`ui/theme/Theme.kt`); there is no light scheme by design.
- When driving the app over adb for checks, the media volume is the user's real phone volume; sounds will play out loud.

## Architecture

Single-activity Compose app, no DI framework: `TavernTalesApp` creates an `AppContainer` holding process-wide singletons; ViewModels are built in `ui/TavernTalesNavHost.kt` with `viewModel { ... }` initializers that pull from the container. Navigation uses type-safe `@Serializable` routes.

- **Data** (`model/`, `data/`): `Library` → `SceneCollection`s → `Scene`s → `SoundLayer`s, plus a global list of `SoundEvent`s (the Events tab is the same in every scene). `LibraryRepository` keeps the library in a `StateFlow` and saves `filesDir/library.json` (via `LibraryCodec`) after a debounce, so callers may update on every slider drag. Pure edit helpers live in `model/LibraryEdits.kt` and are unit-tested.
- **File format**: `LibraryCodec` is versioned. v1 was a flat `scenes.json`; it is migrated into a "My scenes" collection next to the defaults (the old file is renamed `scenes.json.migrated`). The decoder ignores unknown keys and fills defaults, so new fields only need a default value; breaking changes need a version bump and a migration.
- **Built-in content**: `data/DefaultLibrary.kt` defines the "Essentials" collection (Town, Tavern, Dungeon, Market, Forest, Cave) and the default events, with stable ids (`default-<scene>`, `event-<name>`). Built-in sounds are `asset:///sounds/<name>.ogg` URIs; built-in backgrounds are `builtin:<key>` values mapped to `R.drawable.bg_<key>` in `ui/components/SceneArt.kt`. `DefaultLibraryTest` checks every referenced file exists.
- **Generated assets**: the sounds (`app/src/main/assets/sounds/`) and backgrounds (`res/drawable-nodpi/bg_*.webp`) are synthesized by `tools/generate_sounds.py` and `tools/generate_scene_art.py` (`pip install -r tools/requirements.txt`). Each script uses one seeded RNG, so output is reproducible, but editing one generator changes the random draws of everything generated after it.
- **Real recordings** replace synthesized sounds via `tools/import_sounds.py`: raw downloads go in `sound-sources/<sound>/` (gitignored), each is described in `tools/sound_sources.json` (file, title, author, source URL, license, optional start/length/gain), and the script writes the OGG into assets and regenerates `SOUND_CREDITS.md`. Only use CC0/CC-BY or similarly permissive sources for built-ins, and verify the license on the source page. Re-running `generate_sounds.py` would overwrite imported sounds, so run `import_sounds.py` after it.
- **User media**: audio is never copied; it's picked with the system document picker and the app keeps a persistable read permission (`audio/AudioImport.kt`). Background images are the opposite: `data/BackgroundStore` downscales and copies them into `filesDir/backgrounds/` (stored as `file://` URIs) and deletes them when replaced or when the scene is deleted.
- **Audio** (`audio/`): `AmbienceMixer` owns one `ExoPlayer` per playing layer, handles fade in/out, and exposes `MixerState` (active scene id, playing layer ids, playing event ids, master volume). It lives in the container, not in the service or an Activity, so playback outlives the UI. Only one scene is active at a time; starting a layer from another scene fades out the current one. Events are fire-and-forget players released when they end, and may overlap. Players don't request audio focus (layers must not pause each other). Must be used from the main thread.
- `AmbienceService` is a `mediaPlayback` foreground service that only keeps the process alive and shows the notification. The mixer starts it when a layer starts and stops it once all layer voices are released (after fade-outs). Don't stop it earlier: stopping before `startForeground` runs crashes the app.
- **UI** (`ui/`): `home/` shows collections as rows of picture tiles; `scene/` is one `LazyVerticalGrid` (3 columns) whose full-span items are the header, controls, tabs and layer cards, and whose single-cell items are event pads. Headings use the bundled Cinzel variable font (`res/font/cinzel.ttf`, OFL, licence in `licenses/`); images load through Coil.

## Workflow

- `main` only receives features that were tested on a device. Develop on `feature/<name>` branches, push them, and merge to `main` only after the user confirms testing.
