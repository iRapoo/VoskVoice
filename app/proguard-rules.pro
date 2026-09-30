# Vosk talks to libvosk.so through JNA, which finds Java methods and fields by name via
# reflection. Shrinking or renaming them breaks the native bridge at runtime.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
