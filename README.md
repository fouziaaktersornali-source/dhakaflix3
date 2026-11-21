# DhakaFlix Android

Native Android movie browser and in-app player for the DhakaFlix local network library.

## Build APK
GitHub Actions builds a debug APK on every push to `main`, pull request, or manual run.

1. Open **Actions**.
2. Select **Build DhakaFlix APK**.
3. Open the latest run.
4. Download `DhakaFlix-debug-apk` and extract `app-debug.apk`.

## Local build
Requires JDK 17, Android SDK Platform 35, and Gradle 8.9. Run `gradle :app:assembleDebug`.

## Server
The app browses `http://172.16.50.14/` as an HTML directory listing and uses AndroidX Media3 ExoPlayer. The phone must have LAN/VPN access to the server. The server must allow directory listing and direct media URLs. Authentication, JavaScript-only interfaces, unsupported codecs, or custom APIs may need further integration.

A cloud APK build does not prove playback against the private LAN server. Test on a real device on the same network.
