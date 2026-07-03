# Keep kotlinx.serialization models used for Venice AI JSON (de)serialization.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.vulnrbot.app.**$$serializer { *; }
-keepclassmembers class com.vulnrbot.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.vulnrbot.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
