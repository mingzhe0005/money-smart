# Keep kotlinx.serialization serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.moneysmart.app.data.remote.**$$serializer { *; }
-keepclassmembers class com.moneysmart.app.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.moneysmart.app.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.moneysmart.app.data.repository.**$$serializer { *; }
-keepclassmembers class com.moneysmart.app.data.repository.** {
    *** Companion;
}
-keepclasseswithmembers class com.moneysmart.app.data.repository.** {
    kotlinx.serialization.KSerializer serializer(...);
}
