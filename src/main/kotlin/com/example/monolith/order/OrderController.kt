package com.example.monolith.order

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<Order> {
        val order = orderService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(order)
    }

    @GetMapping
    fun getAllOrders(): ResponseEntity<List<Order>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/{id}")
    fun getOrderById(@PathVariable id: Long): ResponseEntity<Order> {
        val order = orderService.getOrderById(id)
        return ResponseEntity.ok(order)
    }

    @GetMapping("/user/{userId}")
    fun getOrdersByUserId(@PathVariable userId: Long): ResponseEntity<List<Order>> {
        val orders = orderService.getOrdersByUserId(userId)
        return ResponseEntity.ok(orders)
    }

    @PatchMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: Long,
        @RequestBody request: UpdateOrderStatusRequest
    ): ResponseEntity<Order> {
        val order = orderService.updateOrderStatus(id, request.status)
        return ResponseEntity.ok(order)
    }
}

data class UpdateOrderStatusRequest(
    val status: OrderStatus
)