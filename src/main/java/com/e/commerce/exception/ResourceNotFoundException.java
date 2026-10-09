package com.e.commerce.exception;

/**
 * Indica que o recurso solicitado não foi localizado.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}


