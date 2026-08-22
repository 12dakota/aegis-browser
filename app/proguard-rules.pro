# R8 / ProGuard — shrink + obfuscate release APKs.
# Keep engine JNI / WebView / Gecko / WPE entry points.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.aegis.browser.AegisApplication { *; }
-keep class com.aegis.browser.BuildConfig { *; }

-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**

-keep class org.wpewebkit.** { *; }
-dontwarn org.wpewebkit.**

-keep class android.webkit.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
