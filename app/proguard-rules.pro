# Keep JNI-facing classes/methods intact - their names are looked up by
# the native side via JNIEnv, so obfuscation would break the bridge.
-keep class com.nokia.vxp.nativecore.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep VXP data model classes (loader/resource) since fields may be
# accessed via reflection-based debug tooling later.
-keep class com.nokia.vxp.loader.** { *; }
-keep class com.nokia.vxp.resource.** { *; }

-dontwarn kotlin.**
