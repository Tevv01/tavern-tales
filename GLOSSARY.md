# Tavern Tales

Ambience for a tabletop game: scenes of looping sounds and music, mixed live, with Philips Hue lights to match.

## Library

**Scene**:
A setting at the table (a tavern, a forest), made of sound layers and, optionally, its lights.
_Avoid_: preset, ambience

**Layer**:
One looping or one-shot sound inside a scene, switched on and off and mixed by volume.
_Avoid_: track, channel

**Event**:
A one-off sound, optionally with a light flash, that can be fired over any scene.
_Avoid_: effect, sound effect

**Light setup**:
A scene's lights made in the app: colours, brightness and how much movement.
_Avoid_: lighting preset

**Hue scene**:
A scene made in the Hue app that a scene can recall instead of having a light setup.

## Playback

**Playback**:
What is happening at the table right now: at most one sound scene and at most one lit scene, which start together by default but stop independently.
_Avoid_: session, now playing

**Sound scene**:
The scene whose layers are on. There is none once Stop is pressed or its last layer is switched off.
_Avoid_: active scene, playing scene

**Lit scene**:
The scene whose lights are showing. Stopping the sound leaves it lit; it ends when the lights are stopped, another scene's lights replace it, or a scene without lights is started.
_Avoid_: active scene

**Movement**:
The gentle drifting of a lit scene's lights, set per light setup. Stopping it leaves the lights as they are.
_Avoid_: animation, motion
