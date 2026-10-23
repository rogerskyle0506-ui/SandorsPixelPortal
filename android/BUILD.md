# Build Sandors Mirrorcast

Two separate Android Studio projects:

- `SandorsSender` — Pixel 10 (minSdk 29, targetSdk 35). Screen capture, system-audio capture, quick settings tile, foreground cast service.
- `SandorsReceiver` — Fire Stick on Fire OS 7 (minSdk 28, targetSdk 28). mDNS advertiser, PIN screen, H.264 + PCM playback.

They share the protocol in `PROTOCOL.md`. Neither app uses Play Services.

## Pixel sender

1. Install Android Studio (Ladybug or newer) with Android SDK 35 and JDK 17.
2. Open the `SandorsSender` folder. Let Gradle sync. Studio writes `local.properties` with your SDK path.
3. Run the `app` configuration on the Pixel 10.
4. Allow notifications if asked.
5. Pull down quick settings, edit the tiles, and add **Sandors**.

The tile opens the app when nothing is casting, and stops an active cast when one is running. Screen capture itself always starts from the system consent dialog. That dialog cannot be skipped. On Android 14 and newer (the Pixel 10), discovery resolves the Stick with `ServiceInfoCallback` instead of the removed `resolveService` path.

## Fire Stick receiver

1. Open `SandorsReceiver` in Android Studio and sync Gradle.
2. On the Stick: Settings → My Fire TV → Developer options → ADB debugging, and Apps from Unknown Sources.
3. From a computer on the same Wi-Fi: `adb connect STICK_IP:5555`, then `adb install -r` the debug APK from `app/build/outputs/apk/debug/`.
4. Launch **Sandors Receiver** once from the Apps row. The first launch is what allows the boot listener to run later.

Leave the receiver open while you cast. It keeps the screen awake and holds a CPU wake lock plus a Wi-Fi lock for the session.

## First cast

1. Phone and Stick on the same Wi-Fi. Multicast must be allowed (most home routers; many guest networks block it).
2. In the Pixel app, tap **Scan the room**.
3. Tap the Stick. A 4-digit code appears on the TV. Enter it on the phone.
4. Accept the screen-capture prompt and choose **Entire screen** or a single app. The phone should stay on “Starting the cast…”, then “Casting”, with the media notification (pause, next quality, volume, stop). The Stick shows **Paired** until the first frame, then the picture. It must not go black with “Sandors · live” while the phone is still asking.
5. You can leave the app. The foreground service keeps encoding. The partial wake lock and a low-latency Wi-Fi lock hold the radios while the phone screen is off.

If the phone jumps back to **Scan the room** after you pick a capture mode, a toast on the phone is the actual error. Reinstall both apps from this zip before testing again — the sender and the receiver both changed.

The next session tries the saved Stick address before a full scan.

## Notes that save a rebuild

- System-audio capture uses `AudioPlaybackCapture` (API 29). It does not use the microphone and does not ask for `RECORD_AUDIO`. Apps that mark their audio as uncapturable are skipped. The picture still casts.
- H.264 is hardware-encoded with B-frames left off. The Pixel picks the profile its encoder actually accepts (forcing Baseline in the format crashes some Pixel encoders). SPS and PPS are still prepended to every keyframe so Fire OS 7 can join mid-stream. If 1080p is refused, the sender retries at 720p30.
- The receiver targets SDK 28 on purpose. Raising it changes background-start rules the Stick does not implement the way stock Android 14 does.
- If Gradle complains about the Android Gradle Plugin, accept Studio's suggested version. The wrapper is Gradle 8.7 and AGP 8.5.2.
