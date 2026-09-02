# VisDroid

VisDroid is a native Android live wallpaper with a CAVA-style audio spectrum visualizer. The default layout places frequency bars along the right edge and makes them extend inward, but the edge, bar count, thickness, spacing, length, color, opacity, sensitivity, decay, rounded ends, and background dimming are configurable.

## What it does

- Uses Android `WallpaperService` for a real live wallpaper.
- Uses Android `Visualizer` on audio session `0` to receive FFT data from the system output mix.
- Maps FFT bins into logarithmically spaced frequency bands for a CAVA-like spectrum.
- Lets you choose a wallpaper image that is copied into the app's private storage.
- Includes a native settings UI and a live audio preview when permission is granted.
- Builds and runs unit tests in GitHub Actions.
- Boots an Android 35 emulator, installs the APK, grants audio permission, launches the app, opens the system live-wallpaper preview, and uploads a screenshot as a CI artifact.

## Android audio caveat

Android's `Visualizer` API intentionally exposes partial, low-quality playback data for visualization. The platform can withhold protected/private audio, and OEM audio implementations can differ. VisDroid requests `RECORD_AUDIO` because Android requires that permission for Visualizer data, but VisDroid does not record or save microphone audio.

## Build

The GitHub Actions workflow uses JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.8.2, and API 35. The debug APK is uploaded as the `visdroid-debug-apk` workflow artifact.
