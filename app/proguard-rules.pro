# FFmpeg extension is loaded by reflection from DefaultRenderersFactory
-keep class androidx.media3.decoder.ffmpeg.** { *; }
# smbj / bouncycastle / slf4j
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**
# Cast options provider is referenced from the manifest
-keep class com.jc.player.cast.CastOptionsProvider { *; }
