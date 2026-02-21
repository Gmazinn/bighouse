# Add project specific ProGuard rules here.
# Keep Gson serialized model fields
-keepclassmembers class com.bighouse.dungeonsim.** {
    <fields>;
}
-keep class com.bighouse.dungeonsim.data.model.** { *; }
-keep class com.bighouse.dungeonsim.engine.** { *; }
