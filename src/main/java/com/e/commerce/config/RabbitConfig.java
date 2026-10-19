package com.e.commerce.config;

import com.e.commerce.service.OutboxPublisher;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue ordersExpiredQueue() {
        return new Queue(OutboxPublisher.ORDERS_EXPIRED_QUEUE, true);
    }
}
