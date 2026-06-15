# =====================================================================
# Regras R8/ProGuard para o release (isMinifyEnabled = true).
# =====================================================================

# --- Modelos da API (Gson, por reflexão) — mantém nomes dos campos ---
-keep class com.folhetosmart.data.api.** { *; }

# --- Retrofit ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Mantém as interfaces de serviço Retrofit e os seus métodos suspend.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- Gson ---
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- OkHttp / Okio (avisos benignos de plataforma) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Firebase Messaging ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
