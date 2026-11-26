# Installing JC Player on your phone

JC Player isn't on the Play Store, so you install it from an APK file. It takes about two minutes.
**Requirements:** an Android phone or tablet running Android 8.0 or newer.

## 1. Download the APK

On your phone, open the **[latest release](https://github.com/jayachandardodda/jc-player/releases/latest)**
and tap **`JC-Player-vX.Y.Z.apk`** under *Assets*.

Or download it on a computer and send it to your phone over WhatsApp (as a *Document*), Telegram,
email, Google Drive, or a USB cable.

## 2. Allow the install

1. Open the downloaded file. It's in your browser's downloads, or in the **Files** / **My Files** app under *Downloads*.
2. Android asks you to allow installs from that app (Chrome, Files, WhatsApp…).
   Tap **Settings**, turn on **Allow from this source**, and go back.
3. Tap **Install**.
4. If **Google Play Protect** warns that the app is unknown, tap **More details → Install anyway**.
   The warning appears for every app that isn't from the Play Store. JC Player has no ads, trackers or analytics,
   and its full source code is in this repository.

## 3. First launch

1. Open **JC Player** and tap **Allow** so it can see your videos.
2. Optional: **Settings → All files access** lets it:
   - auto-load subtitle files (`Movie.srt` next to `Movie.mp4`)
   - move, copy and rename videos and folders
3. Optional: allow **notifications** so background play shows playback controls.

## Updating

Download the new APK from [Releases](https://github.com/jayachandardodda/jc-player/releases) and install it
**over** the existing app. Your watch history, playlists, favourites and settings are kept.

> If Android says *"App not installed: package conflicts with an existing package"*, the new APK was signed
> with a different key (for example, you built it yourself on another computer). Uninstall JC Player first,
> then install the new APK. Uninstalling clears your watch history and settings.

## Installing from source (developers)

1. Install [Android Studio](https://developer.android.com/studio), or JDK 17 + the Android SDK.
2. Clone the repo and build:

   ```bash
   git clone https://github.com/jayachandardodda/jc-player.git
   cd jc-player
   ./gradlew assembleRelease          # Windows: .\gradlew.bat assembleRelease
   ```

   The APK is written to `app/build/outputs/apk/release/app-release.apk`.
3. To install straight to a phone, turn on **Developer options → USB debugging** (Settings → About phone →
   tap *Build number* 7 times), connect the phone, and run `./gradlew installRelease`.
   On Android 11+ you can use **Wireless debugging** instead of a cable.

## Troubleshooting

| Problem | Fix |
|---|---|
| "There was a problem parsing the package" | The download was incomplete. Download the APK again. |
| No videos appear | Settings → Apps → JC Player → Permissions → allow **Photos and videos**. Then pull down on the home screen to refresh. |
| A video won't play or has no sound | In the player, tap ⚙︎ (Quick settings) → **Decoder** to switch between HW and SW. |
| Subtitles next to a video aren't found | Turn on **Settings → All files access**. |
| Move/Copy says it needs access | Same as above: turn on **All files access**. |
