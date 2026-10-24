# LiteRT-LM (Google AI Edge) — native bridge via JNI
-keep class com.google.ai.edge.litertlm.** { *; }

# Ktor / kotlinx.coroutines (reflection + atomicfu)
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Gson & serialization models: keep generic signatures and data classes serialized via reflection
-keep class com.raikaai.app.data.** { *; }
-keep class com.raikaai.app.localai.** { *; }
-keep class com.raikaai.app.core.server.** { *; }
-keep class com.raikaai.app.core.remote.** { *; }
-keep class com.raikaai.app.core.container.** { *; }
-keep class com.raikaai.app.ui.chat.PersonaPreset { *; }
-keep class com.raikaai.app.ui.chat.BenchmarkResult { *; }
-keep class com.raikaai.app.ui.roleplay.** { *; }
-keep class com.raikaai.app.skills.** { *; }
-keep class com.raikaai.app.web.** { *; }
-keep class com.raikaai.app.rag.** { *; }
-keep class com.raikaai.app.artifacts.** { *; }
-keep class com.raikaai.app.mcp.** { *; }
-keep class com.materialkolor.** { *; }
# WorkManager: ReminderWorker diinstansiasi via reflection oleh WorkerFactory bawaan
-keep class com.raikaai.app.reminder.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# WorkManager: WorkDatabase_Impl diinstansiasi via reflection oleh Room saat
# WorkManagerInitializer berjalan di ContentProvider (SEBELUM Application.onCreate).
# R8 full mode men-strip konstruktornya -> "Unable to get provider
# androidx.startup.InitializationProvider" -> force close instan tanpa UI dan
# tanpa sempat menulis crash report. (Bug ditemukan 2026-10-01)
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); *; }
-keep class androidx.work.impl.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
