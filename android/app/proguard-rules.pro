# Keep kotlinx.serialization models used for Venice AI JSON (de)serialization.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.hexstrike.ai.**$$serializer { *; }
-keepclassmembers class com.hexstrike.ai.** {
    *** Companion;
}
-keepclasseswithmembers class com.hexstrike.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
