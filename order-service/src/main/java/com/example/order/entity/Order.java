package com.example.order.entity;

import com.example.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "orders",
        indexes = @Index(name = "idx_user_id", columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    public Order(Long userId, String productName, Integer quantity, BigDecimal price) {
        this.userId = userId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
    }
}

enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}