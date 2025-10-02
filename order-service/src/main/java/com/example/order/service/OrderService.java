package com.example.order.service;

import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(
            request.getUserId(),
            request.getProductName(),
            request.getQuantity(),
            request.getPrice()
        );
        return orderRepository.save(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateOrderRequest {
        private Long userId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;

        public CreateOrderRequest(Long userId, String productName, Integer quantity, BigDecimal price) {
            this.userId = userId;
            this.productName = productName;
            this.quantity = quantity;
            this.price = price;
        }
    }
}