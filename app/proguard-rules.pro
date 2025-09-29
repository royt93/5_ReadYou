# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-dontobfuscate

# Disable ServiceLoader reproducibility-breaking optimizations
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory

# XML Parser fixes - comprehensive
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
-keep class org.xmlpull.v1.** {*;}
-keep class org.kxml2.** {*;}
-keep class javax.xml.** {*;}
-keep class org.xml.sax.** {*;}

# Ultimate R8 XML parsing fix
-dontwarn java.beans.**
-dontwarn javax.activation.**
-dontwarn org.jdom2.**
-dontwarn javax.xml.stream.**
-dontwarn com.sun.syndication.**
-dontwarn org.apache.commons.**

# Force R8 to ignore the problematic class relationship
-dontnote android.content.res.XmlResourceParser
-dontnote org.xmlpull.v1.XmlPullParser
-dontwarn android.content.res.XmlResourceParser

# Allow R8 to optimize while keeping essential functionality
-keep,allowshrinking,allowobfuscation class com.rometools.rome.feed.** { *; }
-keep,allowshrinking,allowobfuscation class com.rometools.rome.io.** { *; }
-keep,allowshrinking,allowobfuscation class com.rometools.modules.** { *; }

# Keep XML interfaces minimal
-keep,allowobfuscation interface org.xmlpull.v1.XmlPullParser { *; }
-keep,allowobfuscation interface org.xmlpull.v1.XmlSerializer { *; }

# Provider API
-keep class me.ash.reader.infrastructure.** { *; }
-keep class com.mckimquyen.reader.data.provider.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Models/DTOs - keep all data classes
-keep class com.mckimquyen.reader.domain.model.** { *; }
-keep class com.mckimquyen.reader.data.model.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Aggressive shrinking - remove unused code
-allowaccessmodification
-dontpreverify
-repackageclasses ''
-allowaccessmodification

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debug code
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# Note: -optimizeaggressively and -overloadaggressively are not supported in R8
# Use R8 optimization instead
