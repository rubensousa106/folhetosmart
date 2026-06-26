package com.folhetosmart.ui

import android.util.Patterns

/**
 * Validação local de formulários (a app valida ANTES de ir ao servidor, para o
 * utilizador ver logo o erro). Cada função devolve a mensagem (PT-PT) a mostrar
 * por baixo do campo, ou `null` se o valor é válido.
 *
 * As regras espelham as do backend (`RegisterRequest`: email `@Email`+`@NotBlank`;
 * password `@Size(min=8)`+`@NotBlank`) para cliente e servidor nunca discordarem.
 */
object Validators {

    /** Mínimo de caracteres da palavra-passe — igual ao `@Size(min=8)` do backend. */
    const val PASSWORD_MIN = 8

    fun email(value: String): String? {
        val v = value.trim()
        return when {
            v.isBlank() -> "O email é obrigatório"
            !Patterns.EMAIL_ADDRESS.matcher(v).matches() -> "Email inválido"
            else -> null
        }
    }

    fun password(value: String): String? = when {
        value.isBlank() -> "A palavra-passe é obrigatória"
        value.length < PASSWORD_MIN -> "A palavra-passe tem de ter pelo menos $PASSWORD_MIN caracteres"
        else -> null
    }

    /** Campo de texto obrigatório (ex.: nome). [message] descreve o que falta. */
    fun required(value: String, message: String): String? =
        if (value.isBlank()) message else null

    /** Confirmação de palavra-passe: tem de ser igual à [original]. */
    fun confirmPassword(value: String, original: String): String? = when {
        value.isBlank() -> "Confirma a palavra-passe"
        value != original -> "As palavras-passe não coincidem"
        else -> null
    }
}
