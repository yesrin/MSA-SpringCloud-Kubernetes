package com.example.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Circuit Breaker가 OPEN 상태일 때 실행되는 Fallback 컨트롤러
 * 서비스 장애 시 사용자에게 적절한 응답 제공
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * User Service 장애 시 Fallback 응답
     */
    @GetMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("message", "User 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "user-service");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    /**
     * Order Service 장애 시 Fallback 응답
     */
    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("message", "Order 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "order-service");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
