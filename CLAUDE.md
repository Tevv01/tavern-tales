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
- **File format**: `LibraryCodec` is versioned. v1 was a flat `scenes.json`; it is migrated into a "My scenes" collection next to the defaults (the old file is renamed `scenes.json.migrated`). v2→v3 adds the default light setups to built-in scenes that have no lights set; v3→v4 gives built-in scenes' setups their default `motion`, keeping the user's colours and brightness. Migrated files are rewritten on load. The decoder ignores unknown keys and fills defaults, so new fields only need a default value; breaking changes need a version bump and a migration.
- **Built-in content**: `data/DefaultLibrary.kt` defines the "Essentials" collection (Town, Tavern, Dungeon, Market, Forest, Cave) and the default events, with stable ids (`default-<scene>`, `event-<name>`). Built-in sounds are `asset:///sounds/<name>.ogg` URIs; built-in backgrounds are `builtin:<key>` values mapped to `R.drawable.bg_<key>` in `ui/components/SceneArt.kt`. `DefaultLibraryTest` checks every referenced file exists.
- **Generated art**: the backgrounds (`res/drawable-nodpi/bg_*.webp`) are drawn by `tools/generate_scene_art.py` (`pip install -r tools/requirements.txt`). It uses one seeded RNG, so output is reproducible, but editing one scene changes the random draws of every scene after it.
- **Built-in sounds are real CC0 recordings from Freesound**, credited in `SOUND_CREDITS.md`. Pipeline: `tools/fetch_freesound.py` searches (CC0 only) and downloads full-length HQ previews into `sound-sources/<sound>/` (gitignored) and records each pick in `tools/sound_sources.json`; `tools/import_sounds.py` turns them into seamless, loudness-matched OGG assets and regenerates the credits. The manifest also holds per-sound processing options (`start`, `exact_loop`, `sparse`, `stack`, `limit_db`, `gain_db`; documented at the top of `import_sounds.py`). Swap a sound with `fetch_freesound.py --pick <sound>=<freesound id>` then re-run the importer. The Freesound API key lives in `.freesound-key` (gitignored, never commit it). Only use CC0/CC-BY or similarly permissive sources for built-ins.
- `tools/generate_sounds.py` (the original synthesized sounds) would overwrite the imported assets; don't run it into the assets folder unless reverting to synthesized audio, and re-run `import_sounds.py` afterwards.
- **User media**: audio is never copied; it's picked with the system document picker and the app keeps a persistable read permission (`audio/AudioImport.kt`). Background images are the opposite: `data/BackgroundStore` downscales and copies them into `filesDir/backgrounds/` (stored as `file://` URIs) and deletes them when replaced or when the scene is deleted.
- **Audio** (`audio/`): `AmbienceMixer` owns one `ExoPlayer` per playing layer, handles fade in/out, and exposes `MixerState` (active scene id, playing layer ids, playing event ids, master volume). It lives in the container, not in the service or an Activity, so playback outlives the UI. Only one scene is active at a time; starting a layer from another scene fades out the current one. Events are fire-and-forget players released when they end, and may overlap. Players don't request audio focus (layers must not pause each other). Must be used from the main thread.
- `AmbienceService` is a `mediaPlayback` foreground service that only keeps the process alive and shows the notification. The mixer starts it when a layer starts and stops it once all layer voices are released (after fade-outs). Don't stop it earlier: stopping before `startForeground` runs crashes the app.
- **Hue** (`hue/`): `HueController` (in the container) finds bridges via mDNS (`_hue._tcp`, `NsdManager`), pairs (v1 `POST /api` with the link button, polled for 90 s), and lists/recalls scenes over the CLIP v2 API (`/clip/v2/resource/scene|room|zone`, header `hue-application-key`). The pairing (ip, bridge id, app key, cert pin) is stored in `filesDir/hue.json`, never in the library. The bridge's HTTPS certificate isn't publicly trusted, so `HueApi` pins its public-key SHA-256 on first contact during pairing and requires it afterwards (trust on first use); the hostname verifier only accepts the paired IP. A scene's lights are either `lighting: LightSetup?` (made in the app: colour slots with optional Hue effects like `candle`/`fire`, plus brightness; applied to the room/zone stored as `HueBridge.group`) or `lights: HueSceneRef?` (a scene from the Hue app, recalled); the repository keeps at most one set. `LightCommands` turns a setup into one CLIP v2 light PUT per bulb (slots spread over lights in name order; xy for colour bulbs, mirek for white-ambiance, effect or `no_effect` where supported), sent ~110 ms apart; a new light change cancels one in progress. A setup's `motion` (0..1) makes `HueController` keep nudging one light at a time (brightness swing and a lean toward the next slot's colour, faded over 3.5–8 s, steps at least 700 ms apart; lights running a native effect are skipped) for as long as its scene is the active one: `SceneLauncher` animates only the active scene's lights and calls `stopMotion()` when the mixer's active scene becomes null. Missed motion steps are only logged. `audio/SceneLauncher` applies a scene's lights whenever it starts or becomes active through a layer toggle; screens must start scenes through the launcher, not the mixer. Built-in light setups live in `DefaultLibrary.lighting`. Parsing (`HueParsing`), commands and colour maths (`LightMath`) are unit-tested.
- **UI** (`ui/`): `home/` shows collections as rows of picture tiles; `scene/` is one `LazyVerticalGrid` (3 columns) whose full-span items are the header, controls, tabs and layer cards, and whose single-cell items are event pads. Headings use the bundled Cinzel variable font (`res/font/cinzel.ttf`, OFL, licence in `licenses/`); images load through Coil.

## Workflow

- `main` only receives features that were tested on a device. Develop on `feature/<name>` branches, push them, and merge to `main` only after the user confirms testing.
