# Keep Kotlinx Serialization models & serializers
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lvupme.app.**$$serializer { *; }
-keepclassmembers class com.lvupme.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lvupme.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Explicitly keep all serializable DTOs, Room entities, and route payloads
-keep class com.lvupme.app.data.remote.** { *; }
-keep class com.lvupme.app.data.local.** { *; }
-keep class com.lvupme.app.ui.onboarding.OnboardingProposal** { *; }

# Keep Glance widget, WorkManager workers, Services, and Receivers
-keep class com.lvupme.app.widget.** { *; }
-keep class com.lvupme.app.worker.** { *; }
-keep class com.lvupme.app.lockscreen.** { *; }
-keep class com.lvupme.app.notification.** { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
