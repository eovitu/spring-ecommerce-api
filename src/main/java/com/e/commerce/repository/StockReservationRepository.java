package com.e.commerce.repository;

import com.e.commerce.entity.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    @Query("select distinct reservation.stock.id from StockReservation reservation "
            + "where reservation.order.id = :orderId order by reservation.stock.id")
    List<UUID> findProductIdsByOrderId(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from StockReservation reservation "
            + "join fetch reservation.stock where reservation.order.id = :orderId "
            + "order by reservation.stock.id")
    List<StockReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query("select distinct reservation.order.id from StockReservation reservation "
            + "where reservation.status = com.e.commerce.enums.StockReservationStatus.ATIVA "
            + "and reservation.expiresAt <= :now order by reservation.order.id")
    List<UUID> findExpiredActiveOrderIds(@Param("now") Instant now);
}
