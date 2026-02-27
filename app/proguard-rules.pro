# Keep Gson serialization models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.funder.data.remote.** { *; }
-keep class com.example.funder.data.local.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
