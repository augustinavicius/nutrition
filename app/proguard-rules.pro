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

# AGP 9 enables R8's strict full mode, in which `-keep class A` no longer implies keeping
# A's default constructor. Several dependencies still ship consumer rules written for the
# old semantics and then construct those classes reflectively, which fails silently at
# runtime in release builds only. Keep the constructors they rely on.
#
# ML Kit discovers its components through Firebase's registry; without these, the registry
# is empty and BarcodeScanning.getClient() throws a NullPointerException.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# WorkManager instantiates workers reflectively (Context, WorkerParameters).
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# androidx.startup instantiates initializers reflectively; WorkManager's own bootstrap
# goes through one.
-keep class * extends androidx.startup.Initializer {
    <init>();
}
