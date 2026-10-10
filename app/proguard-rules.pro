-keepclassmembers class fr.sygix.jauge.** {
    *** Companion;
}
-keepclasseswithmembers class fr.sygix.jauge.** {
    kotlinx.serialization.KSerializer serializer(...);
}
