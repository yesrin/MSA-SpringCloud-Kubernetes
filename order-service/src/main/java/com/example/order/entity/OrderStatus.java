package com.example.order.entity;

public enum OrderStatus {
    PENDING,      // 대기중
    CONFIRMED,    // 확인됨
    SHIPPED,      // 배송중
    DELIVERED,    // 배송완료
    CANCELLED     // 취소됨
}
