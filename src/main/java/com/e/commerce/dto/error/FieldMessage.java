package com.e.commerce.dto.error;

import lombok.Getter;

/**
 * Representa o erro associado a um campo específico de validação.
 */
@Getter
public class FieldMessage {

    private final String fieldName;
    private final String message;

    public FieldMessage(String fieldName, String message) {
        this.fieldName = fieldName;
        this.message = message;
    }
}

