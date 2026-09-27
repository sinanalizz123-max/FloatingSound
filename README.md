# FloatingSound

A tiny Android utility that replaces broken physical volume buttons with an
always-available floating control. No dependencies — Android framework only.

## Features

- Floating dot docked to the left/right screen edge (drag to move, tap to open)
- Slim vertical frosted-glass panel next to the button:
  - Vertical sliders: media / call / ring (icons on top, value below)
  - Sound modes: 🔊 Ring (full) / 📳 Vibrate / 🔇 Silent (native)
- Quick Settings tile to switch sound mode from the notification shade
- Foreground service + boot receiver so it stays alive

## Permissions

- Display over other apps (overlay)
- Do Not Disturb access (required by Android for native Silent)
- Notifications (Android 13+, for the foreground-service notice)
- Vibrate (tap feedback), Receive boot completed

## Build

Needs Android SDK 34 + JDK 17:

```bash
# point to your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Toolchain: AGP 8.5.2 · compileSdk / targetSdk 34 · minSdk 26 · Java 17.

## License

MIT
