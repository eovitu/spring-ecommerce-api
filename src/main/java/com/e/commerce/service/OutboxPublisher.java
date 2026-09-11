package com.e.commerce.service;

import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend("", ORDERS_EXPIRED_QUEUE, event.getPayload());
                operations.waitForConfirmsOrDie(5_000L);
                return null;
            });
            event.marcarPublicado(clock.instant());
        }
    }
}
