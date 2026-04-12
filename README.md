# Income > Expense

`Income > Expense` (`I>E`) is an Android finance tracker built with Kotlin and Jetpack Compose. It focuses on fast entry, simple summaries, recurring transactions, local backup/export, and home screen widgets.

## Current Features

- Add income and expense transactions from the Home screen
- Create recurring transactions with daily, weekly, monthly, or yearly schedules
- Review saved transactions and manage recurring rules
- Compare totals in Stats by month, year, and category
- Manage custom categories
- Choose the display currency used for amounts and summaries
- Switch between system, light, and dark theme modes
- Export and import backup files
- Schedule one automatic backup per day to a user-selected folder
- Use two widgets:
  - `Add Transaction` widget to jump directly into entry flow
  - `Year Income > Expense` widget to show the current year net total

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android SDK 36 / min SDK 24
- Gradle Kotlin DSL

## Project Structure

- [app/src/main/java/com/tazrog/ive/MainActivity.kt](/home/tazrog/AndroidStudioProjects/IvE/app/src/main/java/com/tazrog/ive/MainActivity.kt)
  Main Compose UI, storage helpers, recurring transaction logic, and backup scheduling
- [app/src/main/java/com/tazrog/ive/AddTransactionWidgetProvider.kt](/home/tazrog/AndroidStudioProjects/IvE/app/src/main/java/com/tazrog/ive/AddTransactionWidgetProvider.kt)
  Home screen shortcut widget
- [app/src/main/java/com/tazrog/ive/YearIncomeExpenseWidgetProvider.kt](/home/tazrog/AndroidStudioProjects/IvE/app/src/main/java/com/tazrog/ive/YearIncomeExpenseWidgetProvider.kt)
  Current-year summary widget
- [app/src/main/AndroidManifest.xml](/home/tazrog/AndroidStudioProjects/IvE/app/src/main/AndroidManifest.xml)
  App components, widget registration, and boot receiver

## Build

Debug APK:

```bash
./gradlew assembleDebug
```

Release APK:

```bash
./gradlew assembleRelease
```

Expected outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

## Notes

- App data is stored locally with `SharedPreferences`
- Automatic backup depends on the user selecting a writable document tree
- The boot/package-replaced receiver re-syncs scheduled automatic backups after restart or app update
