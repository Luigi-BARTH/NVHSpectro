# ProGuard & R8 Optimization and Obfuscation Rules for NVH Spectro

# Ignore internal JTransforms / jlargearrays optional Sun JVM classes on Android
-dontwarn sun.misc.Cleaner
-dontwarn pl.edu.icm.jlargearrays.**
-dontwarn org.apache.commons.**

# Preserve Compose and Serialization
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Preserve JTransforms Math Library
-keep class com.github.wendykierp.jtransforms.** { *; }
-keep class pl.edu.icm.jlargearrays.** { *; }

# Preserve Google Play Location Services
-keep class com.google.android.gms.location.** { *; }
