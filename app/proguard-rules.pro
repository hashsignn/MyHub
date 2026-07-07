# Release keep rules for R8 (shrinking + obfuscation).
#
# Most of what this app uses ships its own consumer rules and needs nothing here:
#   • Manifest-referenced classes (Activities, App, ForegroundService, FilterVpnService,
#     the DeviceAdminReceiver, FileProvider) are kept automatically by AGP.
#   • Room, DataStore, Material, AppCompat and kotlinx-coroutines bundle their own rules.
# The rules below cover the two things that are specific to this app.

# 1. Keep crash-log stack traces useful. We write uncaught exceptions to a local log the user can
#    share; without line numbers those traces are near-useless. Class names stay obfuscated — use the
#    mapping.txt from build/outputs/mapping/release/ to de-obfuscate a shared log.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. We persist some enums by their .name() (e.g. the launcher disguise, detox reasons) and read them
#    back with valueOf. Obfuscating the constant names would break that round-trip across an update,
#    so keep enum names/members for our own package plus the standard enum accessors everywhere.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.contentreg.app.** { *; }
