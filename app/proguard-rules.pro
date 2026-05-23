# EInkReader ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.einkreader.** { *; }
-dontwarn javax.xml.**
