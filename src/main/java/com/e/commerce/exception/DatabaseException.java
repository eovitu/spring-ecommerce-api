package com.e.commerce.exception;

/**
 * Indica violação de regra de integridade persistida no banco.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }
}


