package com.e.commerce.dto.response;

import com.e.commerce.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta com dados de um pedido.
 *
 * <p>DTO de saída que expõe informações sobre um pedido,
 * incluindo status, usuário, itens e total.
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {

    /** Identificador único do pedido */
    private UUID id;

    /** Data e hora em que o pedido foi criado */
    private LocalDateTime moment;

    /** Status atual do pedido */
    private OrderStatus status;

    /** Nome do cliente que fez o pedido */
    private String nomeCliente;

    /** Total do pedido (soma de todos os itens) */
    private BigDecimal total;

    /** Lista de itens inclusos no pedido */
    private List<OrderItemResponse> items;

}
