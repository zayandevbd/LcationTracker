# ============================================================
# ProGuard / R8 Rules — LocationTracker
# ============================================================
# These rules apply only to release builds (minifyEnabled = true).
# Debug builds skip ProGuard entirely.
# ============================================================

# ─── Project-specific optimisation settings ─────────────────────────────────

# Keep line numbers in stack traces so crash reports are readable.
# Without this, obfuscated stack traces are nearly impossible to debug.
-keepattributes SourceFile,LineNumberTable

# Preserve the original source file name in obfuscated stack traces.
-renamesourcefileattribute SourceFile

# Keep all checked exceptions, generic signatures, and annotations.
# Room and Lifecycle rely on these at runtime.
-keepattributes Signature,Exceptions,*Annotation*,EnclosingMethod,InnerClasses

# ─── Room Database ───────────────────────────────────────────────────────────

# Keep all Room entity classes and their fields.
# Room maps Java field names to SQLite column names via reflection.
# If R8 renames a field, the database schema breaks silently.
-keep class com.zayan.locationtracker.database.entity.** { *; }

# Keep Room DAO interfaces — Room generates implementations at compile time,
# but the interface is still referenced by name at runtime.
-keep interface com.zayan.locationtracker.database.dao.** { *; }

# Keep Room-generated _Impl classes. R8 may not find these automatically
# since they are generated after the initial code analysis pass.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

# ─── AndroidX Lifecycle (ViewModel + LiveData) ───────────────────────────────

# ViewModels are instantiated via reflection by ViewModelProvider.
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Keep LiveData observers — they use generic type information at runtime.
-keepclassmembers class * extends androidx.lifecycle.LiveData {
    public void observe(...);
}

# ─── Foreground Service & Receivers ─────────────────────────────────────────

# The Android OS resolves service and receiver class names from the manifest.
# If R8 renames these, the OS cannot start them — silent failure on boot
# or when trying to start the service.
-keep class com.zayan.locationtracker.service.** { *; }
-keep class com.zayan.locationtracker.receiver.** { *; }

# ─── Google Play Services — Location ────────────────────────────────────────

# FusedLocationProviderClient and related classes use internal reflection.
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ─── Application UI Classes ─────────────────────────────────────────────────

# Keep all Activity and Adapter classes — they may be referenced by name
# in XML layouts (tools:context) or via intent resolution.
-keep class com.zayan.locationtracker.ui.** { *; }

# ─── Enums ──────────────────────────────────────────────────────────────────

# R8 can sometimes incorrectly optimise enums that are used in switch statements
# or serialised. Keep them safe.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Parcelable ─────────────────────────────────────────────────────────────

# Not currently used, but keeping for safety if Parcelable is added later.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ─── Serializable ───────────────────────────────────────────────────────────

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ─── Suppress warnings for known safe omissions ─────────────────────────────

# These are classes referenced by transitive dependencies that don't exist
# in the Android runtime but are harmless to ignore.
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn sun.misc.Unsafe
