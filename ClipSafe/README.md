# ClipSafe

Android clipboard notebook for Android 10+.

## Features
- Encrypted local storage using AndroidX Security Crypto and Android Keystore.
- PIN lock with salted PBKDF2 hash; biometric unlock where supported.
- Save clipboard text manually, and capture clipboard changes while the app is open and unlocked.
- Search, copy, delete individual entries, and clear the full history.
- Keeps at most 300 entries.
- No INTERNET permission; Android backup is disabled.

## Important Android limitation
Android restricts background clipboard reads. ClipSafe does not use Accessibility Services, notification scraping, or hidden background collection. It captures changes only while the app is open and unlocked, and provides a manual save button.

## Build
A GitHub Actions workflow builds a debug APK on each push to this branch. For local builds, open this folder in Android Studio and run the Gradle task `:app:assembleDebug`.

This is a development/debug build, not a Play Store release. Review and test it on the target phone before storing sensitive text. Avoid copying passwords, one-time codes, or financial secrets into any clipboard history.
