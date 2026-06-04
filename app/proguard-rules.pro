# Marsan Chess — ProGuard Rules

# Mantener modelos de datos (Room)
-keep class com.marsan.chess.data.** { *; }
-keep class com.marsan.chess.engine.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Navigation Component
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# General
-keepattributes Signature
-keepattributes *Annotation*
