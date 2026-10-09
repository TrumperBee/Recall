# Keep generated Room and Hilt classes (consumer rules ship with the libraries).
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn org.slf4j.**
