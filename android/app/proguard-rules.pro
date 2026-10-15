# Retrofit y Gson necesitan firmas genéricas, anotaciones y los modelos intactos.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.lito.planify.dashboard.data.** { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.**
