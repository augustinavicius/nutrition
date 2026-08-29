# kotlinx.serialization keeps generated serializers reachable via reflection lookups.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.augustinavicius.nutrition.**$$serializer { *; }
-keepclassmembers class io.github.augustinavicius.nutrition.** { *** Companion; }
-keepclasseswithmembers class io.github.augustinavicius.nutrition.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.internal.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
