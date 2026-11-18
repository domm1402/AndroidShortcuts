# Android Shortcuts

A native Android shortcut builder inspired by the automation concepts of Apple's Shortcuts. This is an independent implementation, not Apple's app or source code.

## Included
- Dark and light appearance
- Shortcut dashboard, search, starter gallery and settings
- Visual action editor with add, edit, delete and reorder
- Run actions in sequence
- Local JSON persistence
- Import and export shortcuts as JSON
- Actions: open website, launch app by package name, show message, copy text, share text, wait, open Android settings, open dialler, web search and set alarm
- GitHub Actions APK build on pushes to main

## Build
Open the Actions tab and download the `AndroidShortcuts-debug` artifact from a successful run. For a local build, open the project in Android Studio or run `gradle assembleDebug`.

## Platform limitations
This build executes shortcuts while the app is open. Android restricts background work, location monitoring, accessibility, notification posting and protected device settings. Scheduled/background triggers, home-screen widgets, notification permission onboarding and deeper system integrations still need implementation and testing.
