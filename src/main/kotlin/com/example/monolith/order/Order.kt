package com.example.monolith.order

import com.example.monolith.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "orders")
data class Order(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val productName: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @Enumerated(EnumType.STRING)
    val status: OrderStatus = OrderStatus.PENDING
) : BaseEntity()

enum class OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}