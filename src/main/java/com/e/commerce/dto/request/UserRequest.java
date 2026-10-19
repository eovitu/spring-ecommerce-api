package com.e.commerce.dto.request;

import com.e.commerce.validation.BCryptPasswordLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requisição para registrar ou atualizar um usuário.
 *
 * <p>Validações aplicadas:
 * <ul>
 *   <li>name: 3-120 caracteres, sem números ou símbolos especiais</li>
 *   <li>email: formato válido e único no sistema</li>
 *   <li>password: mínimo 8 caracteres, deve conter maiúscula, número e caractere especial</li>
 *   <li>phone: padrão brasileiro, 10-11 dígitos (opcional)</li>
 * </ul>
 *
 * <p>Exemplo:
 * <pre>
 * {
 *   "name": "João da Silva",
 *   "email": "joao@example.com",
 *   "password": "Senha@123",
 *   "phone": "11987654321"
 * }
 * </pre>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {

    /**
     * Nome completo do usuário.
     * Deve conter apenas letras e espaços, 3-120 caracteres.
     */
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 120, message = "Nome deve ter entre 3 e 120 caracteres")
    @Pattern(
        regexp = "^[a-zA-ZÀ-ú\\s]+$",
        message = "Nome não pode conter números ou símbolos especiais"
    )
    private String name;

    /**
     * Email único do usuário.
     * Deve ser um email válido, máximo 255 caracteres.
     */
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido (ex: usuario@example.com)")
    @Size(max = 255, message = "Email deve ter no máximo 255 caracteres")
    private String email;

    /**
     * Senha do usuário com hash BCrypt.
     * Requisitos:
     * <ul>
     *   <li>Mínimo 8 caracteres</li>
     *   <li>Máximo 72 bytes em UTF-8</li>
     *   <li>Deve conter pelo menos 1 letra maiúscula (A-Z)</li>
     *   <li>Deve conter pelo menos 1 número (0-9)</li>
     *   <li>Deve conter pelo menos 1 caractere especial (!@#$%^&*)</li>
     * </ul>
     */
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter no minimo 8 caracteres")
    @BCryptPasswordLength
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*]).+$",
        message = "Senha deve conter: 1 maiúscula, 1 número e 1 caractere especial (!@#$%^&*)"
    )
    private String password;

    /**
     * Telefone do usuário (opcional).
     * Padrão brasileiro: 10 dígitos (fixo) ou 11 dígitos (celular).
     * Exemplo: "11987654321" ou "1133334444"
     */
    @Pattern(
        regexp = "^[0-9]{10,11}$",
        message = "Telefone inválido (10 ou 11 dígitos)"
    )
    private String phone;
}
