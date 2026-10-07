# Tavern Tales

An Android app for running ambience at the D&D table: scenes made of layered sounds and music you toggle and mix live, with Philips Hue lights synced to the scene.

## Status

Early development. `main` holds tested, working builds; new features are developed on `feature/*` branches and merged after testing on a device.

## Features

- **Scenes**: e.g. *Town*, *Forest*, *Dungeon*, each with its own set of sound layers.
- **Live mixer**: toggle and set the volume of each layer (crowd chatter, rain, wind, tavern music) while the scene plays, including with the screen off. Layers fade in and out; one-shot (non-looping) sounds work for effects like a door slam.
- **Your own audio**: add sounds from files on the phone. They aren't copied, so keep them where they are. OGG or WAV loops more seamlessly than MP3.

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
