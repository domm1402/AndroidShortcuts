# Android Shortcuts

An independent Android automation app inspired by the idea of building personal shortcuts. It does not contain Apple's proprietary code or artwork.

## Included
- Native Android interface with dark and light appearance.
- Create, edit, reorder, duplicate, delete and manually run shortcuts.
- Local shortcut storage plus JSON import/export.
- Actions: open a website, open an installed app by package name, show a message, copy text, share text, wait, open Android settings, open the dialler, search the web and open the alarm UI.
- Daily scheduled triggers using Android's inexact alarm API.
- Daily alarms are restored when the app opens and after device reboot.
- Scheduled "Show message" actions appear as notifications. Scheduled "Copy to clipboard" actions copy their text in the background.
- Android may delay inexact alarms during Doze or battery-saving periods. Notifications require notification permission on Android 13+.

## Current automation limits
Actions that need to launch another screen (for example opening a website or another app) cannot be launched silently from the background on modern Android. The scheduled notification opens the app; those screen-based actions remain available through manual run. Location, Wi-Fi, Bluetooth and battery-state triggers are not implemented yet.

## Build
Open the repository in Android Studio, or use the **Build Android APK** GitHub Actions workflow. The debug APK is uploaded as an Actions artifact after a successful build.
