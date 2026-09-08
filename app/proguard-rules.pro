# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JSON
-keep class org.json.** { *; }
-dontwarn org.json.**

# Keep service
-keep class com.remote.control.RemoteService { *; }
-keep class com.remote.control.CommandExecutor { *; }
-keep class com.remote.control.NetworkManager { *; }

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}