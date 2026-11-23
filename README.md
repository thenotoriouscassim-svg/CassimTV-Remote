# Cassim TV Remote

One Android app with two modes:

- **THIS IS TV** — creates a short-lived six-digit PIN and runs the receiver.
- **THIS IS REMOTE** — enters the PIN and opens the remote-control screen.

## Architecture

The TV receiver maintains a persistent secure WebSocket to the Cassim relay on Render. The phone sends authenticated, allowlisted actions over HTTPS. The relay forwards each action and waits for an acknowledgement from the TV.

Relay: https://cassimtv3.onrender.com

## First prototype notes

- Pairing PINs expire after five minutes and are single-use.
- Receiver and remote receive separate random bearer tokens.
- The relay currently keeps pairings in memory. A Render restart or instance replacement clears active pairings; pair again if that happens.
- Both devices need internet access to reach the relay. They do not need to be on the same Wi-Fi network.
- Volume up/down and mute use Android's local AudioManager.
- Home, Back, directional focus movement, OK and the power dialog use the Android AccessibilityService. Enable **Cassim TV Navigation** in Android Accessibility settings on the TV.
- Android restricts ordinary apps from injecting arbitrary hardware key events. The accessibility service navigates accessible focusable controls; compatibility varies by TV firmware and app.
- The power button opens Android's power menu; it does not forcibly shut down the TV.
- The receiver runs as a foreground connected-device service while paired.

## Build

Open this repository in Android Studio or use the GitHub Actions workflow. The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`
