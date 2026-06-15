package com.folhetosmart.data.local

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/** Guarda o JWT da sessão (SharedPreferences). */
class TokenStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("folheto_auth", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** Refresh token (30 dias) — para renovar a sessão sem novo login. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    /** Valor pronto para o cabeçalho Authorization, ou null se sem sessão. */
    val bearer: String?
        get() = token?.let { "Bearer $it" }

    /**
     * Papel do utilizador lido do claim `role` do JWT (ex.: "ADMIN" | "USER"),
     * ou null se não houver sessão. O papel é definido pelo servidor — a app
     * apenas o lê para decidir o que mostrar (ex.: separador Admin).
     */
    val role: String?
        get() = claims()?.optString("role")?.takeIf { it.isNotBlank() }

    /** True se o JWT da sessão tem role ADMIN. */
    val isAdmin: Boolean
        get() = role == "ADMIN"

    /**
     * True se existe um JWT em cache ainda válido (claim `exp` no futuro,
     * com 60s de margem). Decide se a app abre no Login ou no ecrã principal.
     */
    fun isSessionValid(): Boolean {
        val exp = claims()?.optLong("exp", 0L) ?: return false
        return exp == 0L || exp * 1000 > System.currentTimeMillis() + 60_000
    }

    /** Descodifica os claims (payload) do JWT em cache, ou null se ilegível. */
    private fun claims(): JSONObject? {
        val jwt = token ?: return null
        return try {
            val payload = jwt.split(".")[1]
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            )
            JSONObject(json)
        } catch (e: Exception) {
            null // token ilegível -> sem claims
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "jwt"
        const val KEY_EMAIL = "email"
        const val KEY_REFRESH = "refresh_jwt"
    }
}
