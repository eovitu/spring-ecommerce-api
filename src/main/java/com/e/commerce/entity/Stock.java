package com.e.commerce.entity;

import com.e.commerce.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @Column(name = "product_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    public static Stock criar(Product product, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Quantidade total nao pode ser negativa");
        }

        Stock stock = new Stock();
        stock.product = Objects.requireNonNull(product, "Produto e obrigatorio");
        stock.totalQuantity = totalQuantity;
        return stock;
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public void reservar(int quantity) {
        exigirQuantidadePositiva(quantity);
        if (quantity > getAvailableQuantity()) {
            throw new InsufficientStockException(
                    "Estoque insuficiente para o produto " + (id != null ? id : "sem id")
            );
        }
        reservedQuantity += quantity;
    }

    public void liberar(int quantity) {
        exigirQuantidadeReservada(quantity);
        reservedQuantity -= quantity;
    }

    public void confirmarBaixa(int quantity) {
        exigirQuantidadeReservada(quantity);
        reservedQuantity -= quantity;
        totalQuantity -= quantity;
    }

    private void exigirQuantidadeReservada(int quantity) {
        exigirQuantidadePositiva(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("Quantidade excede o estoque reservado");
        }
    }

    private void exigirQuantidadePositiva(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }
    }
}
