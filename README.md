# JC Player

An ad-free Android video player with pro features: gestures, subtitles, network streaming and more. Built with Kotlin,
Jetpack Compose and Media3 (ExoPlayer), plus FFmpeg for extra audio codecs.

## Install on your phone

**Ready-made APK** (after a build):

- `app/build/outputs/apk/release/app-release.apk` (smaller, faster; recommended)
- `app/build/outputs/apk/debug/app-debug.apk`

Copy the APK to your phone and open it. Android will ask you to allow installs from that
source the first time.

**Or install over USB:**

1. On the phone: Settings → About phone → tap *Build number* 7 times → back to Settings →
   Developer options → turn on **USB debugging**.
2. Plug the phone in and accept the "Allow USB debugging?" prompt.
3. From this folder:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\jdk17"
.\gradlew.bat installRelease      # or installDebug
```

## Build

Toolchain (already installed on this PC):

- JDK 17 → `%LOCALAPPDATA%\Programs\jdk17`
- Android SDK → `%LOCALAPPDATA%\Android\Sdk`

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\jdk17"
.\gradlew.bat assembleRelease     # APK in app/build/outputs/apk/release
.\gradlew.bat testDebugUnitTest   # unit tests
```

You can also open the folder in Android Studio and press Run.

> The release APK is signed with the debug key so it installs directly. Before
> publishing to the Play Store, create your own keystore and set it in `app/build.gradle.kts`.

## Features

**Library**
- Finds every video on the phone, grouped by folder, with thumbnails, duration, size, resolution and watch progress
- Sort by name (natural order: Ep 2 before Ep 10), date, size or duration; list or grid view
- Search, "NEW" badges, a Resume button for the last video
- Long-press a video: play, add to playlist, share, rename, delete, properties
- Long-press a folder: play all, hide, or move to the PIN-protected **Private folder**

**Player**
- Gestures: left side swipe = brightness, right side = volume (boost up to 200%), sideways = seek,
  double-tap sides = ±10 s (taps add up), double-tap centre = play/pause, pinch = zoom & pan
- Remembers where you stopped in every video, with a "Start over" button
- Auto-plays the rest of the folder or playlist
- Aspect ratio: Fit / Stretch / Crop / 16:9 / 4:3 / 21:9; rotate button; auto-rotate to match the video
- Screen lock and **Kids lock** (also pins the app; long-press the lock icon to exit)
- Speed 0.25×–4× with natural pitch
- HW / SW decoder switch; FFmpeg decodes AC3, EAC3, DTS, TrueHD and more
- Audio track switching, equalizer with presets, bass boost
- Subtitles: SRT, ASS/SSA, VTT, TTML; built-in or from a file; auto-loads `Movie.srt` next to
  `Movie.mp4` (needs *All files access* in Settings); size, colour, background, outline, bold,
  position; **sync delay** (±0.1 s / ±0.5 s)
- Background play (keeps playing with a notification and lock-screen controls)
- Picture-in-picture with play/pause/rewind/forward buttons; auto-enters when you press Home
- Sleep timer (minutes or end of video), A–B repeat, video info (codec, resolution, decoder)
- Hardware volume keys drive the player's volume, including the boost range

**Network**
- Play any URL: http(s), HLS (.m3u8), DASH (.mpd), RTSP; recent-URL history
- Browse and play from **SMB shares** (Windows shared folders, NAS); subtitle files beside the video are loaded too
- **Chromecast**: cast local videos (served from the phone over Wi-Fi) or network URLs

**Other**
- "Open with" from WhatsApp, file managers, browsers
- Themes: System / Light / Dark / AMOLED black, 6 accent colours
- No ads, no analytics, no network calls except what you play

## Project layout

```
app/src/main/java/com/jc/player/
  JcApp.kt                app singletons (settings, database, media scanner, player)
  data/                   models, settings (SharedPreferences), MediaStore scanner, Room DB
  player/                 ExoPlayer holder, background service, SMB data source, subtitles, audio effects
  cast/                   Chromecast options + local HTTP server for casting phone files
  ui/MainActivity.kt      tabs + navigation + permissions
  ui/library/             folders, videos, search
  ui/player/              PlayerActivity, controller (logic/state), screen (gestures/controls), side panels
  ui/playlists/ network/ settings/ vault/
```

## Known limits

- ASS/SSA subtitles show text, colours and positions, but not advanced effects such as karaoke or animation (Media3 limitation).
- Built-in (embedded) subtitles can only be delayed, not shown earlier. Subtitle files can be shifted either way.
- Casting videos from SMB shares isn't supported yet. Casting local files and URLs works.
- SMB passwords are stored in the app's private storage, not encrypted.

## License

JC Player is released under the [MIT License](LICENSE).

It uses open-source libraries under their own licences, including
[Media3/ExoPlayer](https://github.com/androidx/media) (Apache 2.0),
the [Jellyfin FFmpeg decoder](https://github.com/jellyfin/jellyfin-androidx-media) (built on FFmpeg, LGPL/GPL),
[smbj](https://github.com/hierynomus/smbj) (Apache 2.0) and [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) (BSD).

