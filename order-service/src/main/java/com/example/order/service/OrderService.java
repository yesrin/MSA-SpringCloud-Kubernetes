package com.example.order.service;

import com.example.order.client.UserClient;
import com.example.order.dto.OrderWithUserResponse;
import com.example.order.dto.UserResponse;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;

    @CircuitBreaker(name = "userClient", fallbackMethod = "createOrderFallback")
    public Order createOrder(CreateOrderRequest request) {
        // 분산 추적 테스트: User Service 호출하여 사용자 검증
        log.info("주문 생성 요청 - userId: {}, productName: {}", request.getUserId(), request.getProductName());

        UserResponse user = userClient.getUserById(request.getUserId());
        log.info("사용자 검증 완료 - userId: {}, userName: {}", user.getId(), user.getName());

        Order order = new Order(
            request.getUserId(),
            request.getProductName(),
            request.getQuantity(),
            request.getPrice()
        );
        Order savedOrder = orderRepository.save(order);
        log.info("주문 생성 완료 - orderId: {}", savedOrder.getId());

        return savedOrder;
    }

    // Circuit Breaker Fallback 메서드
    private Order createOrderFallback(CreateOrderRequest request, Exception ex) {
        log.error("User Service 호출 실패! Circuit Breaker 작동 - userId: {}, error: {}",
                  request.getUserId(), ex.getMessage());

        // 사용자 검증 없이 주문 생성 (또는 예외를 던질 수도 있음)
        log.warn("사용자 검증 없이 주문 생성 진행 - userId: {}", request.getUserId());

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

    @CircuitBreaker(name = "userClient", fallbackMethod = "getOrdersWithUserFallback")
    public List<OrderWithUserResponse> getOrdersWithUserInfo(Long userId) {
        log.info("[Order Service] 사용자의 주문 목록 조회 시작 - userId: {}", userId);

        // 1. 해당 사용자의 주문 목록 조회
        List<Order> orders = orderRepository.findByUserId(userId);
        log.info("[Order Service] 주문 목록 조회 완료 - userId: {}, orderCount: {}", userId, orders.size());

        // 2. User Service 호출하여 사용자 정보 조회
        UserResponse user = userClient.getUserById(userId);
        log.info("[Order Service] 사용자 정보 조회 완료 - userId: {}, userName: {}", user.getId(), user.getName());

        // 3. Order + User 정보 합쳐서 반환
        List<OrderWithUserResponse> response = orders.stream()
            .map(order -> OrderWithUserResponse.of(order, user))
            .collect(Collectors.toList());

        log.info("[Order Service] Order + User 정보 반환 완료");
        return response;
    }

    // Circuit Breaker Fallback 메서드
    private List<OrderWithUserResponse> getOrdersWithUserFallback(Long userId, Exception ex) {
        log.error("User Service 호출 실패! Circuit Breaker 작동 - userId: {}, error: {}",
                  userId, ex.getMessage());

        // User 정보 없이 Order만 반환 (기본값)
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream()
            .map(order -> OrderWithUserResponse.of(order,
                new UserResponse(userId, "Unknown User", "unknown@example.com")))
            .collect(Collectors.toList());
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