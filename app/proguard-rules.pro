# R8 rules for release builds.
# Most libraries (Compose, WorkManager, Navigation 3, kotlinx.serialization) ship
# their own consumer rules; these cover what is specific to this app.

# Navigation 3 back-stack keys are saved with kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.example.btcwidget.**$$serializer { *; }
-keepclassmembers class com.example.btcwidget.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.btcwidget.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.example.btcwidget.Main { *; }

# Keep readable stack traces in crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
