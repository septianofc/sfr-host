# ProGuard / R8 Rules for SFR Host

# Code shrinking & obfuscation settings
-repackageclasses 'com.sfrhostapp.internal'
-allowaccessmodification

# Remove Log calls in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep Coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Moshi / JSON models
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Firebase & Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep WorkManager Worker classes
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Compose and ViewModel
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Data Models
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
