package com.e.commerce.service;

import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RabbitOperations rabbitOperations;

    @Test
    void marksEventPublishedOnlyAfterBrokerConfirmation() {
        OutboxEvent event = OutboxEvent.pedidoCanceladoPorExpiracao(UUID.randomUUID(), NOW.minusSeconds(1));
        when(repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitOperations);
        });

        service().publishPending();

        verify(rabbitOperations).convertAndSend("", "orders.expired", event.getPayload());
        verify(rabbitOperations).waitForConfirmsOrDie(5_000L);
        assertTrue(event.isPublished());
    }

    @Test
    void brokerFailureLeavesEventPendingForRetry() {
        OutboxEvent event = OutboxEvent.pedidoCanceladoPorExpiracao(UUID.randomUUID(), NOW.minusSeconds(1));
        when(repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker unavailable") {
        });

        assertThrows(AmqpException.class, () -> service().publishPending());

        assertFalse(event.isPublished());
    }

    private OutboxPublisher service() {
        return new OutboxPublisher(
                repository,
                rabbitTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
