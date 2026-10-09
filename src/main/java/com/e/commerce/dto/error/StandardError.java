package com.e.commerce.dto.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Representa a resposta padrão de erro da API.
 *
 * <p>É utilizada para estruturar erros genéricos como 404, 409, 401, 403 e 500.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StandardError {

    private Instant timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
}

