package com.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /**
     * User Service 장애 시 Fallback 응답
     */
    @GetMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        return createFallbackResponse("user-service",
            "User 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * Order Service 장애 시 Fallback 응답
     */
    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return createFallbackResponse("order-service",
            "Order 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * 공통 Fallback 응답 생성 메서드
     *
     * @param serviceName 서비스 이름
     * @param message 사용자에게 표시할 메시지
     * @return Fallback 응답
     */
    private ResponseEntity<Map<String, Object>> createFallbackResponse(String serviceName, String message) {
        log.warn("Circuit breaker activated for service: {}", serviceName);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        response.put("service", serviceName);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
