# ProGuard and R8 rules for VibeTune Music Streaming App

# Keep models for Gson deserialization
-keep class com.example.spotifyclone.data.model.** { *; }

# Keep Retrofit and OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

# Keep MediaSession and AndroidX Media
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }

# Keep ViewBinding
-keep class com.example.spotifyclone.databinding.** { *; }
