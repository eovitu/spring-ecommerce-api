package com.e.commerce.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Limita a senha ao comprimento em bytes suportado pelo BCrypt. */
@Documented
@Constraint(validatedBy = BCryptPasswordLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface BCryptPasswordLength {
    String message() default "Senha deve ter no maximo 72 bytes em UTF-8";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
