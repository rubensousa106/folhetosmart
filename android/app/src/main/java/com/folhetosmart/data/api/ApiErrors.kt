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

/**
 * Mensagem PT-PT para o utilizador a partir do código de estado HTTP — nunca um
 * número. Espelha `friendlyStatus()` da web (web/lib/api.ts), para a mesma
 * lógica não ficar repetida em cada ecrã da app.
 *
 * Ordem: [overrides] (mensagens específicas deste ecrã para códigos que aqui
 * significam outra coisa, ex.: 400 = "PDF inválido" no Admin) > [serverMessage]
 * (mensagem que o backend já devolveu) > mensagem genérica por código.
 */
fun HttpException.friendlyMessage(overrides: Map<Int, String> = emptyMap()): String {
    overrides[code()]?.let { return it }
    serverMessage()?.let { return it }
    return when (code()) {
        401 -> "Email ou palavra-passe incorretos."
        403 -> "Sem permissão para fazer isto."
        409 -> "Já existe uma conta com este email."
        429 -> "Demasiadas tentativas. Tenta novamente daqui a 15 minutos."
        else -> "Não foi possível completar o pedido. Tenta novamente."
    }
}
