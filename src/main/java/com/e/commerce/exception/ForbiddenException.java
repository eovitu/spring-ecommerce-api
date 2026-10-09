package com.e.commerce.exception;

/**
 * Indica que o usuário foi autenticado, mas não possui permissão para a ação.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}


