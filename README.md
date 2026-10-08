# Tavern Tales

An Android app for running ambience at the tabletop RPG table. Each scene layers sounds and music that you switch on and mix live, with sound-effect pads for dramatic moments and Philips Hue lights that change with the scene.

**Latest release: [0.3.0](https://github.com/Tevv01/tavern-tales/releases/latest)**. Early development: `main` only holds builds that were tested on a phone.

## Features

**Scenes and collections**
- Group scenes into collections (per campaign, per region, ...). The built-in **Essentials** collection has a Town, Tavern, Dungeon, Market, Forest and Cave, each with its own artwork, sounds and lighting.
- Give any scene a picture from your gallery, and move scenes between collections.
- Tap a collection's name to fold it away (one for D&D, one for a board game night...); folded collections stay folded and show when one of their scenes is playing.

**Live ambience mixer**
- Each scene is made of sound layers (crowds, rain, fire, music...) that you switch on and off and mix while it plays. Sounds fade in and out smoothly.
- Plays on with the screen off, with a "now playing" notification and a Stop button.
- Add your own audio files. One-shot (non-looping) sounds work for effects like a church bell.

**Events**
- A pad of sound effects available in every scene: Fireball, Explosion, Holy light, Thunder, Sword clash, Arcane spell, Monster roar and Arrow volley.
- With Hue connected, events flash the lights: Fireball bursts orange, Thunder strobes white, Holy light glows gold. Afterwards the lights return to the scene.
- Add your own, and change each pad's name, icon, colour, volume, sound and light flash.

**Philips Hue lighting**
- Finds your Hue Bridge on the Wi-Fi and pairs with the link button. Reconnects by itself if the bridge gets a new address.
- Give each scene its lights: colours, brightness, candle and fire flicker on bulbs that support it, and gentle movement while the scene plays. Or link a scene from the Hue app. The built-in scenes come with lighting.
- Playing a scene switches the lights too; edits show on the lights as you make them.

**Backup and restore**
- Save your whole library (scenes, light setups, events, pictures and your own sounds) to one file. Restore it on a new phone or after a reinstall, or add a friend's scenes to yours.

## Install

1. Download the `.apk` from the [latest release](https://github.com/Tevv01/tavern-tales/releases/latest) on an Android phone (Android 8 or newer).
2. Open it. You may need to allow installing apps from your browser or file manager.

From 0.2.1 on, releases are signed and install over each other, keeping your scenes. If you have 0.1.0 or 0.2.0 installed (debug-signed test builds), uninstall it once before installing a signed release: make a backup first (**⋮ → Back up library**) and restore it afterwards.

## Getting started

- **Find your way**: the app opens on its main menu. **Scenes** leads to everything below; **Credits & licences** and **Privacy** are there too.
- **Play a scene**: tap the play button on a scene's picture, or open it and tap **Play scene**. Switch individual sounds on and off and adjust their volumes on the **Ambience** tab; the **Events** tab has the sound-effect pads.
- **Set up Hue lights**: tap the lightbulb on the home screen, connect your bridge and press its link button, then choose the **Room for scene lighting**. Each scene's **Lights** row lets you edit its colours, brightness, flicker and movement.
- **Back up**: **⋮ → Back up library** on the home screen. Keep the file somewhere safe, such as Google Drive.

## Requirements and limitations

- Android 8.0 or newer. Layouts are designed for phones in portrait.
- Hue needs a square (v2) Hue Bridge or a Bridge Pro on the same Wi-Fi as the phone. The old round bridge isn't supported, and guest networks that isolate devices block it.
- About 1.2% battery per hour while playing with the screen off (measured on a Galaxy S24 Ultra). If your phone's battery saver stops playback, set Tavern Tales to **Unrestricted** in its battery settings.

## Privacy

Tavern Tales has no account and no ads, and doesn't track you. Your scenes, sounds, pictures and settings stay on your phone (and in backup files you choose to make).

The app only talks to the network for:
- **Your Hue Bridge**, directly on your Wi-Fi. If the bridge can't be found there, the app asks Philips Hue's discovery service (`discovery.meethue.com`) for its local address, as the Hue app does.
- **Crash reports, only if you agree.** The app asks once; you can change your answer in the **⋮** menu. If a crash happens, a report goes to [Sentry](https://sentry.io), stored in the EU. It contains:
  - the error and where in the app it happened, and the app version;
  - the phone model and Android version, plus technical details such as memory, storage, battery level, screen size, network type, and your language and time-zone settings;
  - a random ID created when the app was installed, which only lets crashes from the same install be counted together. It isn't linked to you.

  No name, account, IP address, location, scenes, sounds, pictures or screenshots are included. After a report is sent, the app shows you the complete report, exactly as it was sent, so you can check (and copy) what was shared. Development (debug) builds never send reports.

## Planned

- A public release on the Play Store.

## Development

Kotlin, Jetpack Compose and Material 3, Media3/ExoPlayer for audio, OkHttp for the Hue bridge's local API. Requires the Android SDK (installed with Android Studio) and JDK 17 or newer (Android Studio's bundled JBR works).

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build and install on a connected phone
./gradlew test                 # unit tests
./gradlew lint
```

Or open the folder in Android Studio and press Run.

### Build variants and releases

- **debug** installs as *Tavern Tales Debug* (`dev.tevv.taverntales.debug`, red bug badge), next to the real app and with its own data.
- **release** (`./gradlew assembleRelease`) is shrunk and signed. Signing is configured outside the repository: `local.properties` points at a `keystore.properties` file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it, the release APK is unsigned.
- **releaseTest** (`./gradlew assembleReleaseTest`) is the same release build installed as *Tavern Tales Test* (blue check badge), to check a release on a phone without touching the real app.

### On-device tests

`SessionStressTest` runs two compressed game nights on a real phone and checks that audio players, threads and memory are all released. Install and run it directly. **Don't use `connectedDebugAndroidTest` on a phone with data you care about**: it uninstalls the app afterwards, deleting its library and Hue pairing.

```sh
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w -e class dev.tevv.taverntales.audio.SessionStressTest \
    dev.tevv.taverntales.debug.test/androidx.test.runner.AndroidJUnitRunner
```

It plays real audio, so mute the phone first.

### Project layout

| Path | What's there |
|---|---|
| `app/src/main/java/dev/tevv/taverntales/model` | Library, collections, scenes, sound layers, events, light setups |
| `.../data` | Storage (`library.json` with format migrations), built-in content, backup and restore |
| `.../audio` | The mixer (one player per sound, fades), background playback service, scene launcher |
| `.../hue` | Hue bridge discovery, pairing, light commands and movement |
| `.../ui` | Compose screens: home, scene, Hue setup |
| `app/src/main/assets/sounds`, `res/drawable-nodpi` | Built-in sounds and scene pictures |
| `tools/` | Scripts that fetch and process the built-in sounds and draw the scene art |

### Workflow

New work happens on `feature/*` branches and is merged into `main` once it has been tested on a phone. Releases are tagged `vX.Y.Z` and published on GitHub with the APK attached.

## Built-in sounds and artwork

The built-in sounds are CC0 (public domain) field recordings and effects from [Freesound](https://freesound.org); [SOUND_CREDITS.md](SOUND_CREDITS.md) lists each sound's source and author. They are fetched and processed by scripts (Python 3, packages in `tools/requirements.txt`):

```sh
pip install -r tools/requirements.txt
python tools/fetch_freesound.py --list               # search Freesound; needs an API key in .freesound-key
python tools/fetch_freesound.py --pick rain=584943   # download a chosen sound into sound-sources/
python tools/import_sounds.py                        # make seamless, level-matched OGGs + SOUND_CREDITS.md
```

`import_sounds.py` also writes the in-app sound credits (`app/src/main/assets/credits/sounds.json`); after changing `tools/sound_sources.json` by hand, `python tools/import_sounds.py --credits-only` refreshes both credit files without touching the audio.

The scene pictures, the main menu's title picture and the app background are drawn procedurally with `python tools/generate_scene_art.py`. (`tools/generate_sounds.py` holds the original synthesized sounds; running it would overwrite the recordings.)

## Credits

- Sounds: see [SOUND_CREDITS.md](SOUND_CREDITS.md).
- Heading font: [Cinzel](https://github.com/NDISCOVER/Cinzel), SIL Open Font License (see `licenses/`).
- Philips Hue is a trademark of Signify. Tavern Tales is not affiliated with or endorsed by Signify.
