package com.e.commerce.service;

import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class OutboxRoutingRabbitIntegrationTest {
    @Container
    static final GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:4-management"))
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 2:2")
            .withExposedPorts(5672)
            .waitingFor(new WaitAllStrategy().withStrategy(Wait.forListeningPort())
                    .withStrategy(Wait.forLogMessage(".*Server startup complete.*\\n", 1)))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void missingRouteRemainsPendingAndRetryKeepsPersistedMessageIdAndPayload() {
        CachingConnectionFactory factory = connectionFactory();
        try {
            RabbitTemplate template = spy(new RabbitTemplate(factory));
            template.setMandatory(true);
            RabbitAdmin admin = new RabbitAdmin(factory);
            admin.deleteQueue(OutboxPublisher.ORDERS_EXPIRED_QUEUE);
            OutboxEvent event = event();
            OutboxPublisher publisher = publisher(template, event);

            assertThrows(AmqpException.class, publisher::publishPending);
            assertFalse(event.isPublished());

            admin.declareQueue(new Queue(OutboxPublisher.ORDERS_EXPIRED_QUEUE, true));
            publisher.publishPending();
            Message delivered = template.receive(OutboxPublisher.ORDERS_EXPIRED_QUEUE, 5_000);
            assertNotNull(delivered);
            assertTrue(event.isPublished());
            assertEquals(event.getId().toString(), delivered.getMessageProperties().getMessageId());
            assertEquals(event.getPayload(), new String(delivered.getBody(), StandardCharsets.UTF_8));
            ArgumentCaptor<MessagePostProcessor> processors = ArgumentCaptor.forClass(MessagePostProcessor.class);
            ArgumentCaptor<CorrelationData> correlations = ArgumentCaptor.forClass(CorrelationData.class);
            verify(template, times(2)).convertAndSend(eq(""), eq("orders.expired"), eq(event.getPayload()), processors.capture(), correlations.capture());
            assertTrue(correlations.getAllValues().get(0).getFuture().join().ack());
            assertNotNull(correlations.getAllValues().get(0).getReturned());
            assertNull(correlations.getAllValues().get(1).getReturned());
            for (MessagePostProcessor processor : processors.getAllValues()) {
                Message message = processor.postProcessMessage(new Message(new byte[0], new org.springframework.amqp.core.MessageProperties()));
                assertEquals(event.getId().toString(), message.getMessageProperties().getMessageId());
            }
            assertNotEquals(correlations.getAllValues().get(0).getId(), correlations.getAllValues().get(1).getId());
        } finally {
            factory.destroy();
        }
    }

    private CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getMappedPort(5672));
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    private OutboxEvent event() {
        OutboxEvent event = OutboxEvent.pedidoCanceladoPorExpiracao(UUID.randomUUID(), Instant.now());
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }

    private OutboxPublisher publisher(RabbitTemplate template, OutboxEvent event) {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        return new OutboxPublisher(repository, template, Clock.systemUTC());
    }
}
