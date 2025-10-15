package com.example.order.dto;

import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderWithUserResponse {
    // Order 정보
    private Long orderId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private OrderStatus status;
    private LocalDateTime createdAt;

    // User 정보
    private Long userId;
    private String userName;
    private String userEmail;

    public static OrderWithUserResponse of(Order order, UserResponse user) {
        return new OrderWithUserResponse(
            order.getId(),
            order.getProductName(),
            order.getQuantity(),
            order.getPrice(),
            order.getStatus(),
            order.getCreatedAt(),
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }
}
