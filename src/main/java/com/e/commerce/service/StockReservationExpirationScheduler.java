package com.e.commerce.service;

import com.e.commerce.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockReservationExpirationScheduler {

    private final StockReservationRepository reservationRepository;
    private final StockReservationExpirationService expirationService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${jobs.stock-expiration.delay:60000}", initialDelayString = "${jobs.stock-expiration.initial-delay:30000}")
    public void expireReservations() {
        for (UUID orderId : reservationRepository.findExpiredActiveOrderIds(clock.instant())) {
            try {
                expirationService.expireOrderReservations(orderId);
            } catch (RuntimeException exception) {
                log.error("stock_reservation_expiration_failed orderId={}", orderId, exception);
            }
        }
    }
}
