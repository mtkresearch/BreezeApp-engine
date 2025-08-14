# ProGuard rules for breeze-app-engine

# Keep the base runner interface and its members
-keep class com.mtkresearch.breezeapp.engine.runner.core.BaseRunner { *; }

# Keep all classes that implement BaseRunner and their members
-keep class * implements com.mtkresearch.breezeapp.engine.runner.core.BaseRunner {
    <init>(...);
    *;
}

# Keep the AIRunner annotation itself available at runtime
-keep @interface com.mtkresearch.breezeapp.engine.annotation.AIRunner

# Keep any class that is annotated with AIRunner, and all of its members.
# This is crucial for the discovery mechanism to find and instantiate runners.
-keep @com.mtkresearch.breezeapp.engine.annotation.AIRunner class * {
    *;
}

# Keep other annotation classes used by AIRunner
-keep @interface com.mtkresearch.breezeapp.engine.annotation.VendorType
-keep @interface com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
-keep @interface com.mtkresearch.breezapp.engine.annotation.HardwareRequirement

# General rule for the rest of the app's code, can be refined later
-keep class com.mtkresearch.breezeapp.** { *; }
-dontwarn com.mtkresearch.breezeapp.** 