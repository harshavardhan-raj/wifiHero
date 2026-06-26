# Keep data models used for Gson serialization/deserialization
-keep class com.wifihero.app.model.** { *; }

# Keep OkHttp & Gson reflective access rules
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
# Prevent ProGuard from stripping out class names from stack traces for release reporting
-keepattributes SourceFile, LineNumberTable
