package com.example.order.controller;

import com.example.order.dto.OrderWithUserResponse;
import com.example.order.entity.Order;
import com.example.order.service.OrderService;
import com.example.order.service.OrderService.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("[Order Controller] 주문 생성 API 호출됨");
        Order order = orderService.createOrder(request);
        log.info("[Order Controller] 주문 생성 완료 - orderId: {}", order.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping
    public ResponseEntity<?> getOrders(@RequestParam(required = false) Long userId) {
        if (userId != null) {
            // userId가 있으면 Order + User 정보 함께 반환
            log.info("[Order Controller] userId로 주문 조회 (User 정보 포함) - userId: {}", userId);
            List<OrderWithUserResponse> ordersWithUser = orderService.getOrdersWithUserInfo(userId);
            return ResponseEntity.ok(ordersWithUser);
        } else {
            // userId가 없으면 전체 주문 조회 (Order만)
            log.info("[Order Controller] 전체 주문 조회");
            List<Order> orders = orderService.getAllOrders();
            return ResponseEntity.ok(orders);
        }
    }
}