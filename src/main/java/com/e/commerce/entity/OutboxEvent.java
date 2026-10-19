package com.e.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent pedidoCanceladoPorExpiracao(UUID orderId, Instant occurredAt) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = "ORDER";
        event.aggregateId = Objects.requireNonNull(orderId, "Pedido e obrigatorio");
        event.eventType = "pedido.cancelado.expiracao";
        event.payload = "{\"orderId\":\"" + orderId + "\"}";
        event.occurredAt = Objects.requireNonNull(occurredAt, "Instante do evento e obrigatorio");
        return event;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void marcarPublicado(Instant publishedAt) {
        if (isPublished()) {
            throw new IllegalStateException("Evento de outbox ja foi publicado");
        }
        this.publishedAt = Objects.requireNonNull(publishedAt, "Instante de publicacao e obrigatorio");
    }
}
