# Fox MR Unity package (Android, VR Box)
Adds camera hand tracking, PS4 light-bar color tracking, tracking presets, and PS4/Xbox/Nintendo controller calibration to a Unity game.
Use it only with games you own or have written permission to modify. Not tested inside Unity yet.

## Install
1. Copy the `FoxMR` folder into your project's `Assets` folder (it includes `Plugins/Android/foxmr-tracker.aar`, built by the GitHub workflow).
2. Dependencies: if you use the External Dependency Manager, `Editor/FoxMRDependencies.xml` handles it (Assets > External Dependency Manager > Android Resolver > Resolve).
   Otherwise: Project Settings > Player > Publishing > tick "Custom Main Gradle Template", then in `dependencies {}` add:
   implementation 'com.google.mediapipe:tasks-vision:0.10.14'
   implementation 'androidx.camera:camera-core:1.3.4'
   implementation 'androidx.camera:camera-camera2:1.3.4'
   implementation 'androidx.camera:camera-lifecycle:1.3.4'
   implementation 'androidx.lifecycle:lifecycle-runtime:2.7.0'
3. Minimum API level 26 (Project Settings > Player > Other Settings). Use IL2CPP/ARM64.
4. Menu FoxMR > Setup Input Axes (adds the controller axes the calibration uses; legacy Input Manager).

## Use
- Add `FoxMR.Menu` to any object in your first scene (open it with a three-finger tap or the open key; calibrate BEFORE putting the phone in the headset).
- Put `FoxMR.HandAnchor` on the object that should follow each hand (slot 0 = left, 1 = right), set the VR camera as `head`.
  Use its `onGrab` / `onRelease` events or `Grabbing` to drive the game's grab logic. Grab modes: Pinch, Fist, Either, ControllerButton.
- Read results directly: `FoxMR.Tracker.Hands[0..1]` (valid, pos, size, pinch, closed, landmarks).
- Controllers: `FoxMR.ControllerInput.Axis(joy, axis)` and `.Pressed(joy, PadAction.Trigger)` return calibrated values.
- Head rotation and side-by-side view come from your VR plugin (for example Google Cardboard XR).
