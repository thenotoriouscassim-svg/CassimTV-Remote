# Cassim TV Remote

A native Android remote for Android TVs that expose ADB over TCP. The phone and TV must be on the same local network; the TV can stay connected by Ethernet.

## Current MVP
- D-pad navigation, OK, Back, Home
- Volume up/down and mute
- Power key event
- Saved TV IP address and port 5555
- **Change TV IP** control and reconnect
- ADB RSA key is generated once and stored privately in app storage
- Connection and authorization feedback

## Important first connection
The TV must have ADB over TCP enabled on port 5555. On first connection, Android TV may show an RSA authorization prompt. Accept it on the TV and connect again. The app does not bypass ADB security.

## Build
Open this project in Android Studio (Jellyfish or newer), allow Gradle sync, then run the app on an Android phone. Or run:

```bash
gradle :app:assembleDebug
```

## Notes
- The app uses the ADB protocol directly through the open-source `com.tananaev:adblib:1.3` library; it does not shell out to a terminal app.
- Power uses Android keyevent 26. Some TVs may not wake from deep standby over ADB.
- Tested hardware/network compatibility still needs to be confirmed on the target TV.
