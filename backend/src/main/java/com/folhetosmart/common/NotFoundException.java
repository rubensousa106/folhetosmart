package com.folhetosmart.common;

/** Recurso não encontrado (mapeado para HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
