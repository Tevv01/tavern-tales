# Tavern Tales

An Android app for running ambience at the D&D table: scenes made of layered sounds and music you toggle and mix live, with Philips Hue lights synced to the scene.

## Status

Early development. `main` holds tested, working builds; new features are developed on `feature/*` branches and merged after testing on a device.

## Features

- **Collections of scenes**: group scenes however you like (per campaign, per region...). The built-in **Essentials** collection has a Town, Tavern, Dungeon, Market, Forest and Cave, each with its own artwork and sounds.
- **Live mixer**: toggle and set the volume of each layer (crowd chatter, rain, wind, tavern music) while the scene plays, including with the screen off. Layers fade in and out; one-shot (non-looping) sounds work for effects like a church bell.
- **Events**: a pad of sound effects (fireball, explosion, holy light, thunder, sword clash...) available in every scene. Add your own and change their name, icon, colour, volume and sound.
- **Your own audio and pictures**: add sounds from files on the phone (they aren't copied, so keep them where they are; OGG or WAV loops more seamlessly than MP3), and give any scene a picture from your gallery.

- **Backup and restore**: save your whole library (scenes, light setups, events, pictures and your own sounds) to one file, and restore it on a new phone or after a reinstall, or add someone else's scenes to yours.
- **Philips Hue**: connect your Hue Bridge (found automatically on your Wi-Fi) and give each scene its lights: colours, brightness and flicker effects (candle, fire) made in the app, or a scene from the Hue app. The built-in scenes come with lighting already. Playing a scene switches the lights too.

## Planned

- Events that flash the lights (e.g. orange for a fireball).

## Building

Requires the Android SDK (installed with Android Studio) and JDK 17+ (Android Studio's bundled JBR works).

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build and install on a connected device
./gradlew test                 # unit tests
```

Or open the folder in Android Studio and press Run.

## Built-in sounds and artwork

The built-in sounds are CC0 (public domain) field recordings and effects from [Freesound](https://freesound.org); see [SOUND_CREDITS.md](SOUND_CREDITS.md) for each sound's source and author. They are fetched and processed by scripts (Python 3, packages in `tools/requirements.txt`):

```sh
pip install -r tools/requirements.txt
python tools/fetch_freesound.py --list               # search Freesound; needs an API key in .freesound-key
python tools/fetch_freesound.py --pick rain=584943   # download a chosen sound into sound-sources/
python tools/import_sounds.py                        # make seamless, level-matched OGGs + SOUND_CREDITS.md
```

The scene pictures are drawn procedurally by `python tools/generate_scene_art.py`.

The heading font is [Cinzel](https://github.com/NDISCOVER/Cinzel) (SIL Open Font License, see `licenses/`).
