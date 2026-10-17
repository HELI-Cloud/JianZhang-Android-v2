# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
# SQLCipher JNI
-keep class net.zetetic.database.** { *; }
# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
# kotlinx.serialization（Navigation 类型安全路由）
-keepclassmembers class com.jianzhang.** {
    *** Companion;
}
-keepclasseswithmembers class com.jianzhang.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jianzhang.**$$serializer { *; }
