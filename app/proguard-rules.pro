# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class org.rakshakmesh.**$$serializer { *; }
-keepclassmembers class org.rakshakmesh.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
