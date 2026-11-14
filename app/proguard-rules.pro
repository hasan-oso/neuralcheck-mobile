# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Hilt generated classes
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep Room entities
-keep class net.neuralcheck.data.local.db.entity.** { *; }

# Keep Gson serialized classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Order 2026-10-14 (user decision, device check 03 round 2) — Gson's own R8 rule, from its android-proguard-example:
# keep the generic signature of TypeToken and of every class that extends it. WHY: `object : TypeToken<X>() {}`
# is an anonymous subclass whose only content is its generic superclass signature. R8 in full mode (AGP 8.2.2)
# merges such subclasses into one class and strips that signature when nothing says it is needed (the global
# -keepattributes Signature above is not enough for it); Gson 2.10.1 then throws in TypeToken() — "TypeToken must be
# created with a type argument ... make sure that generic signatures are preserved" — the first time the
# subclass is built. That is what stopped EvaluationAnswers from initialising in every release build from
# 1.0.8-v6beta to 1.0.13, so the technician-evaluation card never got its draft. EvaluationAnswers no longer
# builds an anonymous subclass (it uses TypeToken.getParameterized); this rule covers the other anonymous
# `object : TypeToken<...>() {}` uses in the source (Converters, AnalysisRequestSerializer, ScoringConfigReader,
# CompareTwoScansUseCase, ViewModels) if they become reachable. allowshrinking: an unused subclass is still removed.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep our Gson DTOs verbatim — these are the data classes whose field names
# must survive R8 minification so Firebase callable JSON deserialises into
# the right slots. AiAnalysisResponse / BehaviorFingerprint / TimeSeriesStats
# / BuyerAdvice etc. are reflected into by Gson at runtime; without these
# rules R8 renames the backing fields to a/b/c and `gson.fromJson(...)`
# silently returns objects with all-null fields, which crashes downstream.
-keep class net.neuralcheck.data.remote.model.** { *; }
# SubscriptionStatus / RegisterResult / PaymentResult / PromoResult /
# ScanSession / JoinSessionResult are declared at the bottom of
# FirebaseAnalysisService.kt under net.neuralcheck.data.remote (NOT under
# the model/ subpackage), so the rule above skips them. R8 was renaming
# their fields in release builds, leaving every checkSubscription call
# with scansRemaining=0 even for paid users. Catch them with a top-level
# rule that does NOT recurse — `*` (single wildcard) only matches classes
# directly in the package, so this won't accidentally widen to include
# every helper class we ever add under .remote.*.
-keep class net.neuralcheck.data.remote.* { *; }
# Keep AiAnalysisResult / PredictedIssue / IssueSeverity etc. — these are
# the DOMAIN models we serialise to Room (scan_scores.aiAnalysisJson) on
# the first AI run and rehydrate via Gson on every re-open. Without this
# rule, R8 renames the fields and the JSON we wrote in one session
# can't be deserialised in the next (or even within the same session if
# Gson's reflection happens after dead-code stripping repositions fields).
-keep class net.neuralcheck.domain.model.** { *; }
# Same reasoning for the on-disk time-series file format (TimeSeriesHeader /
# TimeSeriesFooter / TimeSeriesData / TimeSeriesSample / VariableDefinition).
# Stored as GZIP-JSON via Gson; reflection-driven serialisation needs the
# field names intact across releases or old reports become unreadable.
-keep class net.neuralcheck.data.local.files.** { *; }
# Catch-all: any field annotated with @SerializedName must keep its backing
# field name regardless of where the class lives. Belt-and-braces against
# us adding a new Gson DTO outside the two packages above and forgetting to
# extend the keep rule.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Firebase classes
-keep class com.google.firebase.** { *; }

# Phase 1.10.A — TFLite GPU delegate keep-rules
# Phase 1.6 introduced TFLite GPU delegate. The GpuDelegateFactory$Options
# API surfaces are referenced reflectively only when the optional GPU
# delegate path is used. R8 sees unreferenced symbols → missing-class
# error. -dontwarn is safe (no functional change at runtime).
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options

# ── Phase 1.15 (R8 fix) — TensorFlow Lite is invoked entirely via REFLECTION ──
# InferenceEngineImpl.kt resolves these classes + methods by their ORIGINAL
# names at runtime:
#   Class.forName("org.tensorflow.lite.Interpreter")
#   Class.forName("org.tensorflow.lite.Interpreter$Options")
#   Class.forName("org.tensorflow.lite.Delegate")
#   Class.forName("org.tensorflow.lite.nnapi.NnApiDelegate")
#   Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
#   getMethod("setNumThreads" | "addDelegate" | "run" | "getOutputTensor" |
#             "shape" | "close")
#
# The block above had ONLY -dontwarn, which suppresses warnings but does NOT
# stop obfuscation. So R8 renamed Interpreter → org.tensorflow.lite.b/c and
# dropped/renamed setNumThreads; release scans on Honor X9d failed with
#   NoSuchMethodException: org.tensorflow.lite.b.setNumThreads [int]
# and every TFLite delegate fell to STUB (forcing the NEON fallback to carry
# every scan). These -keep rules pin BOTH the class names AND all members
# (methods + fields) so the reflective lookups resolve. The `class` specifier
# also covers interfaces/enums; the explicit -keep interface + -keepclassmembers
# are belt-and-braces. Scoped to org.tensorflow.** ONLY — the rest of the app
# stays obfuscated + shrunk (we do NOT disable minify).
-keep class org.tensorflow.** { *; }
-keep interface org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**
