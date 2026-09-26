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

# Preserve FFmpegKit and its JNI/Native methods
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.smartexception.**

# Keep all native methods and classes containing them
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep project models and encode classes
-keep class com.example.model.** { *; }
-keep class com.example.encode.** { *; }
-keep class com.example.parser.** { *; }
-keep class com.example.util.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
