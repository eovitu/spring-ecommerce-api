package com.e.commerce.exception;

/**
 * Indica tentativa de acesso sem autenticação válida.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}


