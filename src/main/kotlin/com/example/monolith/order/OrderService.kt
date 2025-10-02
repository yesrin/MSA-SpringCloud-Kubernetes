package com.example.monolith.order

import com.example.monolith.user.UserService
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val userService: UserService
) {
    fun createOrder(request: CreateOrderRequest): Order {
        userService.getUserById(request.userId)

        val order = Order(
            userId = request.userId,
            productName = request.productName,
            quantity = request.quantity,
            price = request.price
        )
        return orderRepository.save(order)
    }

    fun getAllOrders(): List<Order> {
        return orderRepository.findAll()
    }

    fun getOrderById(id: Long): Order {
        return orderRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Order not found with id: $id") }
    }

    fun getOrdersByUserId(userId: Long): List<Order> {
        userService.getUserById(userId)
        return orderRepository.findByUserId(userId)
    }

    fun updateOrderStatus(id: Long, status: OrderStatus): Order {
        val order = getOrderById(id)
        val updatedOrder = order.copy(status = status)
        return orderRepository.save(updatedOrder)
    }
}

data class CreateOrderRequest(
    val userId: Long,
    val productName: String,
    val quantity: Int,
    val price: BigDecimal
)