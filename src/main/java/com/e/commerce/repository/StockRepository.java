package com.e.commerce.repository;

import com.e.commerce.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stock from Stock stock join fetch stock.product where stock.product.id = :productId")
    Optional<Stock> findByProductIdForUpdate(@Param("productId") UUID productId);
}
