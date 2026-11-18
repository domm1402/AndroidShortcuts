# Android Shortcuts

An Android-native visual shortcut builder inspired by the automation concepts of Apple's Shortcuts.

## Build
Open the repository in Android Studio or run:

`gradle assembleDebug`

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow builds the APK on pushes to `main` and can also be started manually from Actions.

## Current actions
- Open website
- Open app by Android package name
- Show an on-screen notification
- Copy text to clipboard
- Share text with another app
- Wait (up to 10 seconds)

Shortcuts are stored locally on the device. Android restricts background execution and access to protected system settings, so some automations require explicit Android permissions or cannot be performed silently.
