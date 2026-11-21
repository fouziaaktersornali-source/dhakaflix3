# DhakaFlix Android app

A dark, native Android movie browser/player starter project for the user's private DhakaFlix LAN file server.

## Current configuration
- App name: DhakaFlix
- Initial media directory: `http://172.16.50.14/` (based on the supplied file-browser screenshot)
- Optional website shown in screenshot: `http://172.16.50.4/`
- Native playback: AndroidX Media3 ExoPlayer (no external player app)
- Directory discovery: parses ordinary HTML links and recognizes common video extensions.

## Build
1. Open this folder in Android Studio (Giraffe or newer; Android SDK Platform 35 installed).
2. Allow Gradle sync and dependency downloads.
3. Connect an Android phone to the same Wi-Fi/LAN as the server.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Important compatibility note
This first build expects the file server at `http://172.16.50.14/` to expose an HTML directory listing with clickable links. If the server requires authentication, uses a JavaScript-only file browser, or exposes files through a special API, its exact endpoints/response format will need to be integrated. The supplied screenshots do not reveal those details. AVI and some unusual codecs may not be supported by Android's native decoders; MP4 (H.264/AAC), MKV with supported codecs, HLS and DASH are preferred.

## Network and testing
The app uses HTTP cleartext because the provided LAN server URL is HTTP. The phone must be on the same LAN, or connected through an authorized VPN. This environment did not include Android SDK/Gradle, so an APK could not be compiled or device-tested here; open and build the project in Android Studio to produce the APK.
