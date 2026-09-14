# Keep the serializable backup and roster models plus their generated serializers,
# which means export and import keep working in a minified release build.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.elendheim.anomalies.**$$serializer { *; }
-keepclassmembers class com.elendheim.anomalies.** {
    *** Companion;
}
-keepclasseswithmembers class com.elendheim.anomalies.** {
    kotlinx.serialization.KSerializer serializer(...);
}
