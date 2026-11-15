# R8 rules for the release build. AndroidX, WorkManager, Compose and kotlinx.serialization ship
# their own consumer rules; youtubedl-android ships none, so its rules are here.

# --- youtubedl-android -------------------------------------------------------------------------
# The library is small and is the engine of the app: keep it whole. Its mapper classes
# (VideoInfo, VideoFormat, ...) are filled by Jackson through reflection, and the updater reads
# GitHub's release JSON with Jackson's tree model.
-keep class com.yausername.** { *; }

# Jackson (jackson-databind, used by youtubedl-android).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class com.fasterxml.jackson.databind.ext.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**

# Apache Commons Compress unpacks the bundled Python on first start. Its ZIP extra fields are
# created by reflection from a registry of classes.
-keep class org.apache.commons.compress.archivers.zip.** implements org.apache.commons.compress.archivers.zip.ZipExtraField { <init>(); }
-dontwarn org.apache.commons.compress.**

# --- kotlinx.serialization ---------------------------------------------------------------------
# The library's own R8 rules keep the generated serializers. The app's @Serializable classes are
# reached only through those serializers; keep their companion serializer() lookups explicit, so
# reified decodeFromString<T>() calls keep working whatever the plugin inlines.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.andyop21.linkdownloader.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.andyop21.linkdownloader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep source file and line numbers for readable crash traces, without the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
