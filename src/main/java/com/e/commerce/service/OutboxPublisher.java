package com.e.commerce.service;

import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    public static final String ORDERS_EXPIRED_QUEUE = "orders.expired";

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${jobs.outbox.delay:5000}", initialDelayString = "${jobs.outbox.initial-delay:30000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            String messageId = event.getId().toString();
            CorrelationData correlation = new CorrelationData();
            rabbitTemplate.convertAndSend("", ORDERS_EXPIRED_QUEUE, event.getPayload(), message -> {
                message.getMessageProperties().setMessageId(messageId);
                return message;
            }, correlation);
            try {
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new AmqpException("Publicacao de outbox nao confirmada ou sem rota");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AmqpException("Publicacao de outbox interrompida", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new AmqpException("Falha ao confirmar publicacao de outbox", exception);
            }
            event.marcarPublicado(clock.instant());
        }
    }
}
