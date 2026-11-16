# Room, Compose, Navigation, DataStore and Coroutines ship their own consumer rules.
# Keep useful stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities are accessed by generated code.
-keep @androidx.room.Entity class * { *; }

-dontwarn org.jspecify.annotations.**
