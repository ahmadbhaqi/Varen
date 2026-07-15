# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.agentworkspace.data.model.** { *; }
-keep class com.agentworkspace.data.db.entity.** { *; }
-keep class com.agentworkspace.model.api.** { *; }
