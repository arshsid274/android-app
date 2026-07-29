# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cue.reply.**$$serializer { *; }
-keepclassmembers class com.cue.reply.** {
    *** Companion;
}
-keepclasseswithmembers class com.cue.reply.** {
    kotlinx.serialization.KSerializer serializer(...);
}
