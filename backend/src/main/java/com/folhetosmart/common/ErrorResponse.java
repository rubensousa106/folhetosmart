package com.folhetosmart.common;

import java.time.Instant;

/** Corpo padrão de erro devolvido pela API. As mensagens são em PT-PT. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message);
    }
}
