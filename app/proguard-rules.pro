# GeoTrace Proguard Rules
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep data models for persistence and export
-keep class com.example.gistracker.data.model.** { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
