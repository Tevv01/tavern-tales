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

## Architecture

Single-activity Compose app, no DI framework: `TavernTalesApp` creates an `AppContainer` holding process-wide singletons; ViewModels are built in `ui/TavernTalesNavHost.kt` with `viewModel { ... }` initializers that pull from the container. Navigation uses type-safe `@Serializable` routes.

- **Data** (`model/`, `data/`): `Scene` → list of `SoundLayer` (name, `content://` URI, volume, autoPlay, loop). `SceneRepository` keeps all scenes in a `StateFlow` and saves them to `filesDir/scenes.json` (via `SceneCodec`) after a debounce, so callers may update on every slider drag. Pure list/scene edit helpers live in `model/SceneEdits.kt` and are unit-tested. The JSON decoder ignores unknown keys and fills defaults, so new fields need a default value to stay compatible with existing files.
- **Audio** (`audio/`): `AmbienceMixer` owns one `ExoPlayer` per playing layer, handles fade in/out, and exposes `MixerState` (active scene id, playing layer ids, master volume). It lives in the container, not in the service or an Activity, so playback outlives the UI. Only one scene is active at a time; starting a layer from another scene fades out the current one. Players don't request audio focus (layers must not pause each other). Must be used from the main thread.
- `AmbienceService` is a `mediaPlayback` foreground service that only keeps the process alive and shows the notification. The mixer starts it when something starts playing and stops it once all voices are released (after fade-outs). Don't stop it earlier: stopping before `startForeground` runs crashes the app.
- Audio files are never copied: they're picked with `OpenMultipleDocuments` and the app takes a persistable read permission on the URI (`audio/AudioImport.kt`).

## Workflow

- `main` only receives features that were tested on a device. Develop on `feature/<name>` branches, push them, and merge to `main` only after the user confirms testing.
