# Tavern Tales

An Android app for running ambience at the D&D table: scenes made of layered sounds and music you toggle and mix live, with Philips Hue lights synced to the scene.

## Status

Early development. `main` holds tested, working builds; new features are developed on `feature/*` branches and merged after testing on a device.

## Features

- **Collections of scenes**: group scenes however you like (per campaign, per region...). The built-in **Essentials** collection has a Town, Tavern, Dungeon, Market, Forest and Cave, each with its own artwork and sounds.
- **Live mixer**: toggle and set the volume of each layer (crowd chatter, rain, wind, tavern music) while the scene plays, including with the screen off. Layers fade in and out; one-shot (non-looping) sounds work for effects like a church bell.
- **Events**: a pad of sound effects (fireball, explosion, holy light, thunder, sword clash...) available in every scene. Add your own and change their name, icon, colour, volume and sound.
- **Your own audio and pictures**: add sounds from files on the phone (they aren't copied, so keep them where they are; OGG or WAV loops more seamlessly than MP3), and give any scene a picture from your gallery.

## Planned

- **Hue sync**: connect to a Hue bridge on the local network and set lights per scene (colour, brightness), editable in the app.

## Building

Requires the Android SDK (installed with Android Studio) and JDK 17+ (Android Studio's bundled JBR works).

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build and install on a connected device
./gradlew test                 # unit tests
```

Or open the folder in Android Studio and press Run.

## Built-in sounds and artwork

All built-in sounds and scene pictures are synthesized by scripts, not recorded or drawn, so there are no licensing concerns. To regenerate them (Python 3 with the packages in `tools/requirements.txt`):

```sh
pip install -r tools/requirements.txt
python tools/generate_sounds.py        # app/src/main/assets/sounds/*.ogg
python tools/generate_scene_art.py     # app/src/main/res/drawable-nodpi/bg_*.webp
```

The heading font is [Cinzel](https://github.com/NDISCOVER/Cinzel) (SIL Open Font License, see `licenses/`).
