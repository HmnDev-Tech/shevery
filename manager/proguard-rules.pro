-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}

-assumenosideeffects class java.util.Objects{
    ** requireNonNull(...);
}

-keepnames class moe.shizuku.api.BinderContainer

# Missing class android.app.IProcessObserver$Stub
# Missing class android.app.IUidObserver$Stub
-keepclassmembers class rikka.hidden.compat.adapter.ProcessObserverAdapter {
    <methods>;
}

-keepclassmembers class rikka.hidden.compat.adapter.UidObserverAdapter {
    <methods>;
}

# Entrance of Shizuku service
-keep class rikka.shizuku.server.ShizukuService {
    public static void main(java.lang.String[]);
}

# Entrance of user service starter
-keep class moe.shizuku.starter.ServiceStarter {
    public static void main(java.lang.String[]);
}

# Entrance of shell
-keep class moe.shizuku.manager.shell.Shell {
    public static void main(java.lang.String[], java.lang.String, android.os.IBinder, android.os.Handler);
}

# AndroidX Window uses optional platform/OEM extension APIs through reflection.
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
}

-assumenosideeffects class moe.shizuku.manager.utils.Logger {
    public *** d(...);
}

#noinspection ShrinkerUnresolvedReference
-assumenosideeffects class rikka.shizuku.server.util.Logger {
    public *** d(...);
}

-allowaccessmodification
-repackageclasses rikka.shizuku
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep JavascriptInterface methods for WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# AndroidX WorkManager, Room, and App Startup
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.impl.**
-keep class * extends androidx.startup.Initializer { *; }
-keep class androidx.work.WorkManagerInitializer { *; }


# Keep Rust JNI bridge classes and methods
-keep class moe.shizuku.manager.adb.AdbPairingClient { native <methods>; }
-keep class moe.shizuku.manager.adb.PairingContext { native <methods>; }
-keep class moe.shizuku.manager.adb.AdbProtocolNative { native <methods>; }
-keep class moe.shizuku.manager.stealth.StealthNative { native <methods>; }
-keep class moe.shizuku.manager.module.ModuleNative { native <methods>; }
