package com.folhetosmart.data.api

import com.folhetosmart.BuildConfig
import com.folhetosmart.data.local.TokenStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Serializa o lado renovação de sessão — só uma chamada de refresh de cada vez. */
    private val refreshLock = Any()

    /** Cliente HTTP pequeno e dedicado à chamada de refresh (sem o Authenticator abaixo, para não recursar). */
    private val refreshHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * @param tokenStore sessão atual. O JWT é injetado em TODOS os pedidos ao
     *   nosso backend (Fix 3) — exceto os que já trazem o cabeçalho
     *   Authorization explícito (alertas/privacidade). O [Authenticator]
     *   também usa o [tokenStore] para renovar a sessão sozinha quando o
     *   token de acesso expira, em vez de desligar o utilizador.
     */
    fun create(tokenStore: TokenStore): ApiService {
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
            val bearer = tokenStore.bearer
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
            .authenticator(sessionAuthenticator(tokenStore, backendHost))
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

    /**
     * Renova a sessão sozinha quando um pedido ao nosso backend falha com 401
     * (token de acesso expirado): chama POST /api/v1/auth/refresh com o refresh
     * token guardado e repete o pedido original com o token novo. Sem isto, a
     * app desligava o utilizador sempre que o JWT expirava a meio da sessão — a
     * web já faz este refresh automático (web/lib/api.ts); isto traz a app a par.
     */
    private fun sessionAuthenticator(tokenStore: TokenStore, backendHost: String?) =
        Authenticator { _, response ->
            val request = response.request
            // Só tenta renovar pedidos ao NOSSO backend (nunca a hosts externos,
            // ex. R2) e nunca aos próprios endpoints de autenticação (login,
            // registo, refresh) — um 401 aí é credenciais erradas, não sessão
            // expirada, e tentar "renovar" causaria recursão.
            if (request.url.host != backendHost ||
                request.url.encodedPath.startsWith("/api/v1/auth/")
            ) {
                return@Authenticator null
            }
            // Já tentámos renovar nesta cadeia e voltou a falhar — desiste (evita ciclo infinito).
            if (response.priorResponse != null) {
                return@Authenticator null
            }

            synchronized(refreshLock) {
                val failedBearer = request.header("Authorization")
                // Outra thread já renovou entretanto (corrida entre pedidos em
                // paralelo) — usa o token novo sem voltar a chamar o backend.
                val current = tokenStore.bearer
                if (current != null && current != failedBearer) {
                    return@synchronized request.newBuilder()
                        .header("Authorization", current)
                        .build()
                }

                val refreshToken = tokenStore.refreshToken ?: return@synchronized null
                val renewed = refreshSession(refreshToken) ?: return@synchronized null

                tokenStore.token = renewed.token
                renewed.refreshToken?.let { tokenStore.refreshToken = it }

                request.newBuilder()
                    .header("Authorization", "Bearer ${renewed.token}")
                    .build()
            }
        }

    /**
     * Chamada SÍNCRONA e direta a POST /api/v1/auth/refresh, com um
     * [OkHttpClient] próprio ([refreshHttp], SEM o [sessionAuthenticator] acima
     * anexado — evita recursão). Devolve null em qualquer falha (refresh token
     * também expirado, sem rede, resposta inesperada) — o 401 original propaga
     * normalmente para o ecrã, tal como acontecia antes desta função existir.
     */
    private fun refreshSession(refreshToken: String): AuthResponse? {
        val body = gson.toJson(RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}api/v1/auth/refresh")
            .post(body)
            .build()
        return try {
            refreshHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = resp.body?.string() ?: return null
                gson.fromJson(json, AuthResponse::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }
}
