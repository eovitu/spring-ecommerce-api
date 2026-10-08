package com.e.commerce.service;

import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    @Mock private OutboxEventRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void marksEventPublishedOnlyAfterConfirmationWithStableMessageId() {
        OutboxEvent event = pendingEvent();
        doAnswer(invocation -> {
            assertFalse(event.isPublished());
            MessagePostProcessor processor = invocation.getArgument(3);
            Message message = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertEquals(event.getId().toString(), message.getMessageProperties().getMessageId());
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(""), eq("orders.expired"), eq(event.getPayload()), any(MessagePostProcessor.class), any(CorrelationData.class));
        service().publishPending();
        assertTrue(event.isPublished());
        assertEquals(NOW, event.getPublishedAt());
    }

    @Test
    void returnedMessageDespiteAckLeavesEventPending() {
        OutboxEvent event = pendingEvent();
        answerConfirmation(true, true);
        assertThrows(AmqpException.class, () -> service().publishPending());
        assertFalse(event.isPublished());
    }

    @Test
    void nackLeavesEventPending() {
        OutboxEvent event = pendingEvent();
        answerConfirmation(false, false);
        assertThrows(AmqpException.class, () -> service().publishPending());
        assertFalse(event.isPublished());
    }

    @Test
    void brokerFailureLeavesEventPendingForRetry() {
        OutboxEvent event = pendingEvent();
        doThrow(new AmqpException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThrows(AmqpException.class, () -> service().publishPending());
        assertFalse(event.isPublished());
    }

    @Test
    void confirmationTimeoutLeavesEventPending() throws Exception {
        OutboxEvent event = pendingEvent();
        @SuppressWarnings("unchecked")
        CompletableFuture<CorrelationData.Confirm> future = mock(CompletableFuture.class);
        when(future.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        replaceFuture(future);
        assertThrows(AmqpException.class, () -> service().publishPending());
        assertFalse(event.isPublished());
    }

    @Test
    void interruptedConfirmationRestoresInterruptAndLeavesEventPending() {
        OutboxEvent event = pendingEvent();
        Thread.currentThread().interrupt();
        try {
            assertThrows(AmqpException.class, () -> service().publishPending());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(event.isPublished());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void exceptionalConfirmationLeavesEventPending() {
        OutboxEvent event = pendingEvent();
        CompletableFuture<CorrelationData.Confirm> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("connection closed"));
        replaceFuture(future);
        assertThrows(AmqpException.class, () -> service().publishPending());
        assertFalse(event.isPublished());
    }

    private void replaceFuture(CompletableFuture<CorrelationData.Confirm> future) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            ReflectionTestUtils.setField(correlation, "future", future);
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private void answerConfirmation(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "", "orders.expired"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = OutboxEvent.pedidoCanceladoPorExpiracao(UUID.randomUUID(), NOW.minusSeconds(1));
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        when(repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        return event;
    }

    private OutboxPublisher service() {
        return new OutboxPublisher(repository, rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
