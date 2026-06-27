package com.folhetosmart.data.api

import com.folhetosmart.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Constrói o [ApiService] do Retrofit. */
object ApiClient {

    /**
     * Cabeçalho que sinaliza um pedido de longa duração (extração com IA do
     * folheto, ~1-2 min). O interceptor sobe o read/write timeout só nesse
     * pedido e remove o cabeçalho antes de o enviar.
     */
    const val HEADER_LONG_TIMEOUT = "X-Long-Timeout"

    /** Gson partilhado (também usado pela cache Room para round-trip do JSON). */
    val gson = GsonBuilder()
        // Mapeia camelCase (Kotlin) <-> snake_case (JSON da API).
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    /**
     * @param bearerProvider devolve "Bearer <jwt>" da sessão atual, ou null.
     *   O token é injetado em TODOS os pedidos (Fix 3) — exceto os que já
     *   trazem o cabeçalho Authorization explícito (alertas/privacidade).
     */
    fun create(bearerProvider: () -> String? = { null }): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // Host do NOSSO backend — o JWT só pode ser injetado em pedidos para aqui.
        val backendHost = BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.host

        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val bearer = bearerProvider()
            // Injeta o JWT SÓ nos pedidos ao nosso backend. NUNCA em hosts externos
            // (ex.: links assinados do R2): além de ser fuga de credenciais, o R2
            // rejeita um pedido que traga presigned-URL + cabeçalho Authorization
            // (400 "Missing x-amz-content-sha256") — era isto que deixava o feed de
            // produtos vazio (downloadFeed ia direto ao R2 com o JWT colado).
            val withAuth = if (bearer != null &&
                request.header("Authorization") == null &&
                request.url.host == backendHost
            ) {
                request.newBuilder().header("Authorization", bearer).build()
            } else {
                request
            }
            chain.proceed(withAuth)
        }

        // Pedidos com X-Long-Timeout (process-flyer) usam um read timeout alto.
        val timeoutInterceptor = Interceptor { chain ->
            val request = chain.request()
            if (request.header(HEADER_LONG_TIMEOUT) != null) {
                chain.withReadTimeout(180, TimeUnit.SECONDS)
                    .withWriteTimeout(60, TimeUnit.SECONDS)
                    .proceed(request.newBuilder().removeHeader(HEADER_LONG_TIMEOUT).build())
            } else {
                chain.proceed(request)
            }
        }

        val http = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(timeoutInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
