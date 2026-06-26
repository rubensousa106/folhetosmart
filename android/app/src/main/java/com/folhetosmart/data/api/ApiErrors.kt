package com.folhetosmart.data.api

import retrofit2.HttpException

/** Corpo de erro padrão do backend (`ErrorResponse`): `{ timestamp, status, error, message }`. */
data class ApiError(
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Mensagem PT-PT que o backend devolveu no corpo do erro (`ErrorResponse.message`),
 * ou `null` se não der para ler. Serve para mostrar ao utilizador a razão real em
 * vez de um código "400". Remove o prefixo técnico "Dados inválidos — " quando exista.
 *
 * Nota: `errorBody().string()` consome o corpo — chamar uma só vez por exceção.
 */
fun HttpException.serverMessage(): String? = try {
    response()?.errorBody()?.string()
        ?.let { ApiClient.gson.fromJson(it, ApiError::class.java) }
        ?.message
        ?.removePrefix("Dados inválidos — ")
        ?.trim()
        ?.ifBlank { null }
} catch (e: Exception) {
    null
}
