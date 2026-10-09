package com.e.commerce.entity;

import com.e.commerce.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade que representa um usuário do sistema de e-commerce.
 *
 * <p>Um usuário pode assumir dois papéis (roles):
 * <ul>
 *   <li>USER: Cliente comum que pode fazer pedidos</li>
 *   <li>ADMIN: Administrador que pode gerenciar catálogo e pedidos</li>
 * </ul>
 *
 * <p>Segurança:
 * <ul>
 *   <li>Email é único em toda a aplicação (constraint em BD)</li>
 *   <li>Senhas são armazenadas com hash BCrypt (nunca em plain text)</li>
 *   <li>Senhas NUNCA são retornadas em respostas de API</li>
 * </ul>
 *
 * <p>Relacionamentos:
 * <ul>
 *   <li>Um-para-Muitos com Order (um usuário pode ter múltiplos pedidos)</li>
 * </ul>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Entity
@Table(name = "tb_user", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_role", columnList = "role"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    /**
     * Identificador único do usuário (UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Nome completo do usuário.
     * Obrigatório, máximo 120 caracteres.
     */
    @Column(nullable = false, length = 120)
    @NotBlank
    private String name;

    /**
     * Email único do usuário.
     * Obrigatório e único no sistema (constraint em BD).
     * Máximo 255 caracteres.
     */
    @Column(nullable = false, unique = true, length = 255)
    @Email
    @NotBlank
    private String email;

    /**
     * Senha com hash BCrypt.
     * NUNCA armazenar em plain text.
     * NUNCA retornar em respostas de API.
     * Obrigatório.
     */
    @Column(nullable = false)
    @NotBlank
    @JsonIgnore
    private String password;

    /**
     * Papel/tipo de usuário no sistema.
     * Valores: USER (cliente) ou ADMIN (administrador).
     * Define quais operações o usuário pode realizar.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "session_version", nullable = false)
    @JsonIgnore
    private long sessionVersion;

    /** Revoga todas as sessões. Deve ser chamado sob lock na mesma transação. */
    public void revokeSessions() {
        sessionVersion = Math.incrementExact(sessionVersion);
    }

    /**
     * Telefone do usuário (opcional).
     * Máximo 11 caracteres (padrão brasileiro).
     */
    @Column(length = 11)
    private String phone;

    /**
     * Lista de pedidos feitos pelo usuário.
     * Relacionamento um-para-muitos.
     * Pedidos preservam o histórico comercial e não são removidos em cascata.
     */
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonManagedReference
    @JsonIgnore
    private List<Order> orders = new ArrayList<>();

    /**
     * Timestamp de quando o usuário foi criado.
     * Definido automaticamente pelo banco.
     */
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Timestamp de última atualização do usuário.
     * Atualizado automaticamente a cada modificação.
     */
    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
