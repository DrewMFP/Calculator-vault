# Keep encryption classes
-keep class com.calculator.vault.security.** { *; }
-keep class com.calculator.vault.managers.** { *; }

# Keep WebView JavaScript interface
-keepclassmembers class com.calculator.vault.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep data classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optimize
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose