package com.park.ecommerce.order.dto;

import com.park.ecommerce.order.domain.Order;

import java.time.LocalDateTime;

// 클라이언트가 토스페이먼츠 결제창을 띄우는 데 필요한 값 - orderNo는 orderId, orderName과 amount는 그대로 결제 요청에 사용
public record OrderCreateResponse(
        String orderNo,
        String orderName,
        Integer amount,
        LocalDateTime expiresAt
) {
    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(order.getOrderNo(), order.getOrderName(), order.getTotalAmount(), order.getExpiresAt());
    }
}
