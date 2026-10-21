# Release builds ship with minify off. Keep MediaCodec / projection entry points if you turn it on.
-keep class com.sandors.mirrorcast.sender.** { *; }
