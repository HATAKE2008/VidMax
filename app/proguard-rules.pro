# VidMax R8 rules — release minification + resource shrinking

# Reproducible stack traces with line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ── NewPipe Extractor (YouTube / online music) ───────────────────────────────
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-dontwarn org.mozilla.**
-dontwarn org.jsoup.**

# ── SMB (smbj + bouncycastle) ────────────────────────────────────────────────
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# ── WebDAV (sardine-android uses Simple XML reflection serializer) ───────────
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class org.simpleframework.xml.** { *; }
-keepclassmembers class com.thegrizzlylabs.sardineandroid.model.** { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn org.simpleframework.xml.**
-dontwarn javax.xml.**
-dontwarn org.xmlpull.**
-dontwarn org.codehaus.**
-dontwarn xpp3.**

# ── OkHttp / OkHttp logging interceptor ──────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── Glide (compose beta) ─────────────────────────────────────────────────────
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ── NanoHttpd (local streaming proxy) ────────────────────────────────────────
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ── MPV native bridge (JNI) ──────────────────────────────────────────────────
-keep class com.vidmax.player.MPVLib$* { *; }
-keepclassmembers class com.vidmax.player.MPVLib {
    native <methods>;
    private int *;
    public static <methods>;
}

# ── App enums persisted via valueOf() in SharedPreferences ───────────────────
-keepclassmembers enum com.vidmax.player.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Services / activities reached from the Manifest are kept by AGP rules ────
